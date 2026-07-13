package com.oink.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.await
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Handles scheduling and canceling of daily reminder notifications.
 *
 * ## Why one-time self-rescheduling work instead of PeriodicWorkRequest
 *
 * A [androidx.work.PeriodicWorkRequest] measures its 24h interval from when the
 * previous run actually finished. Under Doze the run is deferred, so the next
 * interval starts late and the error accumulates: an "8pm" reminder wanders to
 * 9pm, 10pm, and later over successive days.
 *
 * Instead we enqueue a single [androidx.work.OneTimeWorkRequest] whose delay is
 * computed to the next wall-clock occurrence of the target time. After the work
 * runs, [ReminderWorker] re-invokes [scheduleDailyReminder], which recomputes
 * the delay against the real clock. Any Doze deferral therefore affects only the
 * single day it happened on and self-corrects the next day, so drift never
 * accumulates.
 *
 * ## Accepted drift
 *
 * WorkManager still respects Doze, so a given day's notification may be delayed
 * by up to a Doze maintenance window (typically minutes, occasionally longer on
 * a device that is idle overnight). This is acceptable for a daily habit nudge
 * and lets us avoid the `SCHEDULE_EXACT_ALARM` permission and boot-receiver
 * machinery that `AlarmManager` would require. If to-the-minute precision ever
 * becomes a hard requirement, switch to `AlarmManager.setExactAndAllowWhileIdle`.
 *
 * ## Constraints
 *
 * No [androidx.work.Constraints] are attached deliberately: a reminder must be
 * able to fire regardless of network, battery, or storage state. Restrictive
 * constraints would suppress the notification exactly when we still want it.
 * Backoff is configured so that a transient failure is retried rather than lost.
 */
object ReminderScheduler {

    /**
     * Schedule a daily reminder at the specified time.
     *
     * Enqueues one-time work for the next occurrence of [hour]:[minute];
     * [ReminderWorker] reschedules the following day's run once it fires.
     *
     * @param context Application context
     * @param hour Hour of day (0-23)
     * @param minute Minute (0-59)
     */
    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)

        // Calculate the delay to the next occurrence of this time against the
        // real clock, so each day's run is anchored to the wall-clock target
        // rather than to the previous (possibly Doze-delayed) run.
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(hour, minute)
        var targetDateTime = now.toLocalDate().atTime(targetTime)

        // If the target time has already passed today, schedule for tomorrow.
        if (now.toLocalTime() >= targetTime) {
            targetDateTime = targetDateTime.plusDays(1)
        }

        val initialDelay = Duration.between(now, targetDateTime)

        val reminderRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(initialDelay)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Enqueue the work, replacing any existing reminder.
        workManager.enqueueUniqueWork(
            ReminderWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
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
        val workInfos = workManager.getWorkInfosForUniqueWork(ReminderWorker.WORK_NAME).await()
        return workInfos.any { !it.state.isFinished }
    }
}
