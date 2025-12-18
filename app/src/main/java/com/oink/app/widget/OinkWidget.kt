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
import com.oink.app.MainActivity
import com.oink.app.R
import com.oink.app.data.AppDatabase
import com.oink.app.data.CheckInRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.utils.BalanceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * Home screen widget for Oink.
 *
 * Shows:
 * - Current balance (your piggy bank!)
 * - Current streak with escalating intensity
 * - Today's status with time-based urgency
 *
 * Features:
 * - Streak display gets more impressive as streak grows
 * - Background urgency increases throughout the day if not logged
 * - Tapping anywhere opens the app
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
        Log.d(TAG, "provideGlance called for widget $id")

        // Fetch data from database
        val widgetData = withContext(Dispatchers.IO) {
            getWidgetData(context)
        }

        Log.d(TAG, "Widget data: balance=${widgetData.balance}, streak=${widgetData.streak}, exercisedToday=${widgetData.exercisedToday}")

        provideContent {
            // Read state to establish dependency (forces re-render when state changes)
            val prefs = currentState<Preferences>()
            val lastUpdate = prefs[LAST_UPDATE_KEY] ?: 0L
            Log.d(TAG, "Widget rendering with lastUpdate=$lastUpdate")

            GlanceTheme {
                WidgetContent(data = widgetData)
            }
        }
    }

    private suspend fun getWidgetData(context: Context): WidgetData {
        val database = AppDatabase.getDatabase(context)
        val preferencesRepository = PreferencesRepository(context)
        val repository = CheckInRepository(database.checkInDao(), preferencesRepository)

        val latestCheckIn = database.checkInDao().getLatestCheckIn()
        val todayCheckIn = database.checkInDao().getCheckInForDate(LocalDate.now().toEpochDay())
        val streak = repository.calculateStreak()

        // Calculate ACTUAL balance using centralized BalanceCalculator
        val checkInBalance = latestCheckIn?.balanceAfter ?: 0.0
        val totalCashedOut = database.cashOutDao().getTotalCashedOut()
        val totalFreezeSpending = preferencesRepository.getTotalFreezeSpending()
        val actualBalance = BalanceCalculator.calculateActualBalance(
            checkInBalance = checkInBalance,
            totalCashedOut = totalCashedOut,
            totalFreezeSpending = totalFreezeSpending
        )

        Log.d(TAG, "getWidgetData: checkInBalance=$checkInBalance, cashedOut=$totalCashedOut, freezeSpending=$totalFreezeSpending, actual=$actualBalance")

        return WidgetData(
            balance = actualBalance,
            streak = streak,
            checkedInToday = todayCheckIn != null,
            exercisedToday = todayCheckIn?.didExercise,
            currentHour = LocalTime.now().hour
        )
    }

    companion object {
        private const val TAG = "OinkWidget"

        // Key for tracking updates - changing this value forces Glance to re-render
        private val LAST_UPDATE_KEY = longPreferencesKey("last_update")

        /**
         * Update all widget instances.
         * Call this whenever check-in data changes.
         *
         * The KEY trick here: We update the widget STATE first, which forces
         * Glance to actually re-render instead of serving cached content.
         */
        suspend fun updateAllWidgets(context: Context) {
            Log.d(TAG, "=== WIDGET UPDATE TRIGGERED ===")

            // Small delay to ensure DB transaction is fully committed
            delay(100)

            try {
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(OinkWidget::class.java)
                Log.d(TAG, "Found ${glanceIds.size} Glance widget IDs")

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
                        Log.d(TAG, "Updated state for widget $glanceId with timestamp $updateTimestamp")

                        // Now trigger the actual update
                        widget.update(context, glanceId)
                        Log.d(TAG, "Triggered update for widget $glanceId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update widget $glanceId", e)
                    }
                }

                Log.d(TAG, "=== WIDGET UPDATE COMPLETE ===")
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed", e)
            }
        }
    }
}

/**
 * Data class for widget display.
 */
data class WidgetData(
    val balance: Double,
    val streak: Int,
    val checkedInToday: Boolean,
    val exercisedToday: Boolean?,
    val currentHour: Int
)

/**
 * Urgency level based on time of day and whether user has logged.
 */
enum class UrgencyLevel {
    CALM,       // Logged, or early morning (before noon)
    NUDGE,      // Not logged, afternoon (12pm-5pm)
    WARN,       // Not logged, evening (5pm-9pm)
    CRITICAL    // Not logged, night (after 9pm)
}

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
 */
private fun getStreakEmoji(tier: StreakTier): String = when (tier) {
    StreakTier.NONE -> ""
    StreakTier.SPARK -> "🔥"
    StreakTier.FIRE -> "🔥🔥"
    StreakTier.BLAZE -> "🔥🔥🔥"
    StreakTier.INFERNO -> "💥🔥💥"
    StreakTier.LEGENDARY -> "👑🔥👑"
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
 */
private fun getUrgencyIndicator(urgency: UrgencyLevel): String = when (urgency) {
    UrgencyLevel.CALM -> ""
    UrgencyLevel.NUDGE -> "⏰"
    UrgencyLevel.WARN -> "⚠️"
    UrgencyLevel.CRITICAL -> "🚨"
}

/**
 * Get call-to-action text based on urgency.
 */
private fun getCtaText(urgency: UrgencyLevel): String = when (urgency) {
    UrgencyLevel.CALM -> "Tap to log"
    UrgencyLevel.NUDGE -> "Log your workout"
    UrgencyLevel.WARN -> "Don't forget!"
    UrgencyLevel.CRITICAL -> "LOG NOW!"
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
            // Left side: Balance
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "🐷 Oink",
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_secondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = formatCurrency(data.balance),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_primary),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Right side: Streak (prominent!) and status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Streak - BIG AND PROUD AS FUCK
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

                // Today's status with urgency
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val urgencyIndicator = getUrgencyIndicator(urgencyLevel)
                    if (urgencyIndicator.isNotEmpty()) {
                        Text(
                            text = urgencyIndicator,
                            style = TextStyle(fontSize = 11.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            data.exercisedToday == true -> "✓ Logged"
                            data.exercisedToday == false -> "✗ Rest day"
                            else -> getCtaText(urgencyLevel)
                        },
                        style = TextStyle(
                            color = ColorProvider(
                                when {
                                    data.exercisedToday == true -> R.color.widget_success
                                    data.exercisedToday == false -> R.color.widget_error
                                    urgencyLevel == UrgencyLevel.CRITICAL -> R.color.widget_error
                                    urgencyLevel == UrgencyLevel.WARN -> R.color.widget_accent
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
 * Format currency for display.
 */
private fun formatCurrency(amount: Double): String {
    return "$${String.format("%.2f", amount)}"
}

/**
 * Widget receiver - required for the widget to work.
 *
 * This is the entry point Android uses to interact with our widget.
 */
class OinkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OinkWidget()
}
