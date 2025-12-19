package com.oink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oink.app.data.AppDatabase
import com.oink.app.widget.OinkWidget
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
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Check if user already logged exercise today
        val database = AppDatabase.getDatabase(applicationContext)
        val todayCheckIn = database.checkInDao().getCheckInForDate(LocalDate.now().toEpochDay())

        // Only show notification if:
        // - No check-in for today, OR
        // - Check-in exists but user marked it as "didn't exercise" (rest day)
        if (todayCheckIn == null || todayCheckIn.didExercise == false) {
            NotificationHelper.showDailyReminder(applicationContext)
        }

        // Always refresh the widget so urgency state is current
        OinkWidget.updateAllWidgets(applicationContext)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reminder_work"
    }
}

