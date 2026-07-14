package com.oink.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.oink.app.BuildConfig
import com.oink.app.MainActivity
import com.oink.app.OinkApplication
import com.oink.app.R
import com.oink.app.utils.Formatters
import com.oink.app.utils.HabitCopy
import com.oink.app.utils.UrgencyLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home screen widget for Oink.
 *
 * Each widget instance highlights ONE non-private habit, chosen in
 * [OinkWidgetConfigActivity] and stored as a `habitId` in this instance's Glance
 * state. It shows that habit's own balance and streak - not the global pot.
 *
 * Shows:
 * - The habit's emoji + name
 * - Its spendable balance
 * - Its streak with escalating intensity
 * - Today's status with time-based urgency
 *
 * Safety: [provideGlance] re-validates the stored habit on every render via
 * [WidgetDataLoader]. If the habit is missing, deleted, or since-toggled
 * private, it renders a neutral fallback instead, so a private or deleted habit
 * can never leak onto the launcher.
 *
 * Uses Glance (Jetpack's Compose for widgets) because:
 * 1. Consistent with our Compose-based UI
 * 2. Cleaner than XML RemoteViews
 * 3. Type-safe and less error-prone
 */
class OinkWidget : GlanceAppWidget() {

    /**
     * Use PreferencesGlanceStateDefinition to track state changes.
     * When we update the state, Glance is forced to re-render.
     */
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        logd { "provideGlance called for widget $id" }

        // Read this instance's chosen habit from its own Glance state, then
        // re-validate + load off the main thread. A null result (no habit chosen,
        // or the habit is missing/deleted/private) means render the neutral
        // fallback rather than any habit data.
        val widgetData = withContext(Dispatchers.IO) {
            val habitId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[HABIT_ID_KEY]
            habitId?.let { loaderFor(context).resolveWidgetData(it) }
        }

        logd { "Widget data: $widgetData" }

        provideContent {
            // Read state to establish dependency (forces re-render when state changes)
            val prefs = currentState<Preferences>()
            val lastUpdate = prefs[LAST_UPDATE_KEY] ?: 0L
            logd { "Widget rendering with lastUpdate=$lastUpdate" }

            GlanceTheme {
                if (widgetData != null) {
                    WidgetContent(data = widgetData)
                } else {
                    FallbackContent()
                }
            }
        }
    }

    /**
     * Build a [WidgetDataLoader] backed by the shared repository graph.
     *
     * The widget runs in the app process, so it reuses the singletons from
     * [AppContainer] rather than constructing its own database and repositories.
     */
    private fun loaderFor(context: Context): WidgetDataLoader {
        val container = (context.applicationContext as OinkApplication).container
        return WidgetDataLoader(
            container.habitRepository,
            container.checkInRepository,
            container.cashOutRepository
        )
    }

    companion object {
        private const val TAG = "OinkWidget"

        // Key for tracking updates - changing this value forces Glance to re-render
        private val LAST_UPDATE_KEY = longPreferencesKey("last_update")

        /**
         * Per-instance key holding the habit this widget highlights. Written by
         * [OinkWidgetConfigActivity]; read back in [provideGlance].
         */
        internal val HABIT_ID_KEY = longPreferencesKey("habit_id")

        /**
         * Log a debug message only in debug builds.
         *
         * The message lambda is inlined, so the string is never built in release
         * builds where these verbose render/update traces would only add noise.
         */
        private inline fun logd(message: () -> String) {
            if (BuildConfig.DEBUG) Log.d(TAG, message())
        }

        /**
         * Update all widget instances.
         * Call this whenever check-in data changes.
         *
         * The KEY trick here: We update the widget STATE first, which forces
         * Glance to actually re-render instead of serving cached content.
         */
        suspend fun updateAllWidgets(context: Context) {
            logd { "=== WIDGET UPDATE TRIGGERED ===" }

            try {
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(OinkWidget::class.java)
                logd { "Found ${glanceIds.size} Glance widget IDs" }

                val widget = OinkWidget()
                val updateTimestamp = System.currentTimeMillis()

                glanceIds.forEach { glanceId ->
                    try {
                        // THE KEY: Update the widget's state with a new timestamp
                        // This forces Glance to recognize the widget as "dirty" and re-render
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                            prefs.toMutablePreferences().apply {
                                this[LAST_UPDATE_KEY] = updateTimestamp
                            }
                        }
                        logd { "Updated state for widget $glanceId with timestamp $updateTimestamp" }

                        // Now trigger the actual update
                        widget.update(context, glanceId)
                        logd { "Triggered update for widget $glanceId" }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update widget $glanceId", e)
                    }
                }

                logd { "=== WIDGET UPDATE COMPLETE ===" }
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed", e)
            }
        }
    }
}

/**
 * Data class for widget display, scoped to the one habit this instance shows.
 */
data class WidgetData(
    val habitName: String,
    val habitEmoji: String,
    val balance: Long,
    val streak: Int,
    val checkedInToday: Boolean,
    val completedToday: Boolean?,
    val currentHour: Int
)

/**
 * Streak tier for escalating display intensity.
 */
enum class StreakTier {
    NONE,       // 0 days
    SPARK,      // 1-2 days - just getting started
    FIRE,       // 3-6 days - building momentum
    BLAZE,      // 7-13 days - on fire!
    INFERNO,    // 14-29 days - unstoppable
    LEGENDARY   // 30+ days - absolute legend
}

/**
 * Get the streak tier based on consecutive days.
 */
private fun getStreakTier(streak: Int): StreakTier = when {
    streak <= 0 -> StreakTier.NONE
    streak <= 2 -> StreakTier.SPARK
    streak <= 6 -> StreakTier.FIRE
    streak <= 13 -> StreakTier.BLAZE
    streak <= 29 -> StreakTier.INFERNO
    else -> StreakTier.LEGENDARY
}

/**
 * Get the urgency level based on time and logged status.
 */
private fun getUrgencyLevel(hour: Int, isLogged: Boolean): UrgencyLevel = when {
    isLogged -> UrgencyLevel.CALM
    hour < 12 -> UrgencyLevel.CALM
    hour < 17 -> UrgencyLevel.NUDGE
    hour < 21 -> UrgencyLevel.WARN
    else -> UrgencyLevel.CRITICAL
}

/**
 * Get streak display emoji(s) based on tier.
 * More emojis = more motivation = more gains 💪
 */
private fun getStreakEmoji(tier: StreakTier): String = when (tier) {
    StreakTier.NONE -> ""
    StreakTier.SPARK -> "✨"
    StreakTier.FIRE -> "🔥"
    StreakTier.BLAZE -> "🔥🔥"
    StreakTier.INFERNO -> "💥🔥💥"
    StreakTier.LEGENDARY -> "👑✨"
}

/**
 * Get streak color based on tier.
 */
private fun getStreakColorRes(tier: StreakTier): Int = when (tier) {
    StreakTier.NONE -> R.color.widget_text_secondary
    StreakTier.SPARK -> R.color.widget_streak_fire
    StreakTier.FIRE -> R.color.widget_streak_fire
    StreakTier.BLAZE -> R.color.widget_streak_blaze
    StreakTier.INFERNO -> R.color.widget_streak_inferno
    StreakTier.LEGENDARY -> R.color.widget_streak_legendary
}

/**
 * Get background color based on urgency.
 */
private fun getBackgroundColorRes(urgency: UrgencyLevel): Int = when (urgency) {
    UrgencyLevel.CALM -> R.color.widget_urgency_calm
    UrgencyLevel.NUDGE -> R.color.widget_urgency_nudge
    UrgencyLevel.WARN -> R.color.widget_urgency_warn
    UrgencyLevel.CRITICAL -> R.color.widget_urgency_critical
}

/**
 * Get urgency indicator for unlogged state.
 * Friendly nudges, not scary warnings.
 */
private fun getUrgencyIndicator(urgency: UrgencyLevel): String = when (urgency) {
    UrgencyLevel.CALM -> ""
    UrgencyLevel.NUDGE -> ""          // CTA is enough
    UrgencyLevel.WARN -> "⏰"
    UrgencyLevel.CRITICAL -> ""       // Emoji in CTA
}

/**
 * Widget content composable.
 *
 * Layout: 3x1 widget
 * - Left: Balance (primary info)
 * - Right: Streak prominently displayed + status
 *
 * Dynamic behaviors:
 * - Streak gets more impressive with longer streaks
 * - Background shifts warmer if not logged as day progresses
 */
// androidx.glance.unit.ColorProvider(resId) is the documented public way to build
// a Glance ColorProvider from a color resource; its RestrictedApi flag is a known
// lint bug.
@Suppress("RestrictedApi")
@Composable
private fun WidgetContent(data: WidgetData) {
    val streakTier = getStreakTier(data.streak)
    val urgencyLevel = getUrgencyLevel(data.currentHour, data.checkedInToday)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(getBackgroundColorRes(urgencyLevel)))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Balance - BIG AND BEAUTIFUL
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "${data.habitEmoji} ${data.habitName}",
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = Formatters.formatCurrency(data.balance),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_accent),  // Coral pink!
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Right side: Streak (prominent!) and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Streak - big and proud
                if (data.streak > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getStreakEmoji(streakTier),
                            style = TextStyle(
                                fontSize = when (streakTier) {
                                    StreakTier.LEGENDARY -> 20.sp
                                    StreakTier.INFERNO -> 18.sp
                                    StreakTier.BLAZE -> 16.sp
                                    else -> 14.sp
                                }
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = "${data.streak}",
                            style = TextStyle(
                                color = ColorProvider(getStreakColorRes(streakTier)),
                                fontSize = when (streakTier) {
                                    StreakTier.LEGENDARY -> 28.sp
                                    StreakTier.INFERNO -> 26.sp
                                    StreakTier.BLAZE -> 24.sp
                                    StreakTier.FIRE -> 22.sp
                                    else -> 20.sp
                                },
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(2.dp))
                        Text(
                            text = "days",
                            style = TextStyle(
                                color = ColorProvider(getStreakColorRes(streakTier)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Today's status with urgency - fun and encouraging!
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val urgencyIndicator = getUrgencyIndicator(urgencyLevel)
                    if (urgencyIndicator.isNotEmpty()) {
                        Text(
                            text = urgencyIndicator,
                            style = TextStyle(fontSize = 12.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            data.completedToday == true -> HabitCopy.DONE
                            data.completedToday == false -> HabitCopy.REST
                            else -> HabitCopy.cta(urgencyLevel)
                        },
                        style = TextStyle(
                            color = ColorProvider(
                                when {
                                    data.completedToday == true -> R.color.widget_success
                                    data.completedToday == false -> R.color.widget_text_secondary
                                    urgencyLevel == UrgencyLevel.CRITICAL -> R.color.widget_error
                                    urgencyLevel == UrgencyLevel.WARN -> R.color.widget_error
                                    else -> R.color.widget_text_secondary
                                }
                            ),
                            fontSize = when (urgencyLevel) {
                                UrgencyLevel.CRITICAL -> 13.sp
                                else -> 12.sp
                            },
                            fontWeight = when (urgencyLevel) {
                                UrgencyLevel.CRITICAL -> FontWeight.Bold
                                UrgencyLevel.WARN -> FontWeight.Medium
                                else -> FontWeight.Normal
                            }
                        )
                    )
                }
            }
        }
    }
}

/**
 * Neutral fallback shown when the widget has no valid habit to display.
 *
 * Rendered whenever no habit is chosen yet, or the chosen habit is missing,
 * deleted, or private. It carries NO habit data - just a neutral prompt - so a
 * private or deleted habit can never surface here. Tapping opens the app, the
 * same as a populated widget.
 */
// androidx.glance.unit.ColorProvider(resId) is the documented public way to build
// a Glance ColorProvider from a color resource; its RestrictedApi flag is a known
// lint bug.
@Suppress("RestrictedApi")
@Composable
private fun FallbackContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_urgency_calm))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🐷 Oink",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_secondary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "Tap to choose a habit",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text_secondary),
                    fontSize = 12.sp
                )
            )
        }
    }
}

/**
 * Widget receiver - required for the widget to work.
 *
 * This is the entry point Android uses to interact with our widget.
 */
class OinkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OinkWidget()
}
