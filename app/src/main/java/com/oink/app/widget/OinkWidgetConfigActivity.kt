package com.oink.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.oink.app.OinkApplication
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkTheme
import kotlinx.coroutines.launch

/**
 * Per-instance widget configuration: pick which habit a widget highlights.
 *
 * Launched by the launcher on widget placement (via `android:configure` in
 * `oink_widget_info.xml`) and again whenever the user edits the widget. It
 * follows the standard AppWidget configuration contract:
 * - default the result to [RESULT_CANCELED], so backing out cancels placement
 * - read the widget id from [AppWidgetManager.EXTRA_APPWIDGET_ID]
 * - on selection, store the chosen `habitId` in that instance's Glance state,
 *   trigger a widget update, then finish with [RESULT_OK]
 *
 * The picker lists non-private habits only, so a private habit can never be
 * assigned to a launcher widget in the first place.
 */
class OinkWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Backing out without a choice must leave the placement cancelled.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val habitRepository = (application as OinkApplication).container.habitRepository

        setContent {
            OinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HabitPickerScreen(
                        habitRepository = habitRepository,
                        onHabitSelected = ::selectHabit
                    )
                }
            }
        }
    }

    /**
     * Persist the chosen habit into this widget instance's Glance state, force a
     * re-render, and finish the configuration successfully.
     */
    private fun selectHabit(habitId: Long) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@OinkWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@OinkWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[OinkWidget.HABIT_ID_KEY] = habitId
                }
            }

            OinkWidget().update(this@OinkWidgetConfigActivity, glanceId)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
}

/**
 * The habit picker list. A config activity is a small, read-only, one-shot
 * screen, so it collects the habit flow directly rather than owning a ViewModel;
 * only non-private habits are offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitPickerScreen(
    habitRepository: HabitRepository,
    onHabitSelected: (Long) -> Unit
) {
    val habits by habitRepository.allHabits.collectAsStateWithLifecycle(initialValue = emptyList())
    val pickableHabits = habits.filter { !it.isPrivate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Choose a habit",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = pickableHabits,
                key = { it.id },
                contentType = { "habit-option" }
            ) { habit ->
                HabitOption(
                    habit = habit,
                    onClick = { onHabitSelected(habit.id) }
                )
            }
        }
    }
}

/**
 * A single selectable habit row: emoji badge + name.
 */
@Composable
private fun HabitOption(
    habit: Habit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OinkPink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = habit.emoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = habit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
