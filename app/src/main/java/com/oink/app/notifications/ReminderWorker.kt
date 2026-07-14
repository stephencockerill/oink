package com.oink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oink.app.data.AppDatabase
import com.oink.app.data.DataStorePreferencesRepository
import com.oink.app.data.HabitRepository
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Worker that shows the daily reminder notification.
 *
 * WorkManager is the right choice here because:
 * 1. It survives app restarts and device reboots
 * 2. It respects battery optimization (Doze mode)
 * 3. It's guaranteed to run (eventually)
 *
 * The trade-off is that the notification might be slightly
 * delayed during Doze mode, but for a daily reminder that's fine.
 *
 * This worker is enqueued as one-time work. After firing, it reschedules the
 * next day's run via [ReminderScheduler], recomputing the delay against the
 * wall clock so the reminder time does not drift. See [ReminderScheduler] for
 * the rationale.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Check if the user already completed the day
        val database = AppDatabase.getDatabase(applicationContext)
        val todayCheckIn = database.checkInDao()
            .getCheckInForDate(HabitRepository.DEFAULT_HABIT_ID, LocalDate.now().toEpochDay())

        // Only show notification if:
        // - No check-in for today, OR
        // - Check-in exists but the user marked the day as not completed (off day)
        if (todayCheckIn == null || todayCheckIn.didSucceed == false) {
            NotificationHelper.showDailyReminder(applicationContext)
        }

        // Always refresh the widget so urgency state is current
        OinkWidget.updateAllWidgets(applicationContext)

        // Reschedule the next day's run anchored to the wall-clock target time.
        // Reading preferences here keeps them the single source of truth: if the
        // user disabled reminders, we stop; if they changed the time, we honor it.
        val prefs = DataStorePreferencesRepository(applicationContext).userPreferences.first()
        if (prefs.remindersEnabled) {
            ReminderScheduler.scheduleDailyReminder(
                applicationContext,
                prefs.reminderHour,
                prefs.reminderMinute
            )
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reminder_work"
    }
}

