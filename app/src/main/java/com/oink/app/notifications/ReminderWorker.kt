package com.oink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oink.app.widget.OinkWidget

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
        // Show the notification
        NotificationHelper.showDailyReminder(applicationContext)

        // Also refresh the widget so urgency state is current
        OinkWidget.updateAllWidgets(applicationContext)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_reminder_work"
    }
}

