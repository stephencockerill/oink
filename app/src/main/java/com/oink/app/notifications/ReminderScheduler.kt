package com.oink.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Handles scheduling and canceling of daily reminder notifications.
 *
 * Uses WorkManager's PeriodicWorkRequest to fire notifications daily.
 * The initial delay is calculated to fire at the specified time.
 */
object ReminderScheduler {

    /**
     * Schedule a daily reminder at the specified time.
     *
     * @param context Application context
     * @param hour Hour of day (0-23)
     * @param minute Minute (0-59)
     */
    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)

        // Calculate initial delay to the next occurrence of this time
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(hour, minute)
        var targetDateTime = now.toLocalDate().atTime(targetTime)

        // If the target time has already passed today, schedule for tomorrow
        if (now.toLocalTime() >= targetTime) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val initialDelay = Duration.between(now, targetDateTime)

        // Create a periodic work request that runs every 24 hours
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .build()

        // Enqueue the work, replacing any existing reminder
        workManager.enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            reminderRequest
        )
    }

    /**
     * Cancel any scheduled reminders.
     */
    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(ReminderWorker.WORK_NAME)
    }

    /**
     * Check if reminders are currently scheduled.
     */
    suspend fun isReminderScheduled(context: Context): Boolean {
        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(ReminderWorker.WORK_NAME).get()
        return workInfos.any { !it.state.isFinished }
    }
}

