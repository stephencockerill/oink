package com.oink.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the day-close auto-resolve worker for the next local midnight.
 *
 * ## Why one-time self-rescheduling work anchored to midnight
 *
 * This mirrors [ReminderScheduler]. A [androidx.work.PeriodicWorkRequest]
 * measures its interval from when the previous run finished, so Doze deferral
 * accumulates and the "midnight" run drifts later each day. Instead we enqueue a
 * single [androidx.work.OneTimeWorkRequest] whose delay is computed to the next
 * wall-clock midnight; after it runs, [DayCloseWorker] re-invokes
 * [scheduleNextMidnight], recomputing the delay against the real clock so any
 * Doze deferral self-corrects the next day and never accumulates.
 *
 * ## Correctness under deferral
 *
 * The exact firing instant does not need to be precise: [DayCloseResolver]
 * resolves every unresolved elapsed day up to yesterday, so a run delayed by a
 * Doze window (or a device that was off for days) still materializes every clean
 * day it owes on its next run. That tolerance is what lets us avoid the
 * `SCHEDULE_EXACT_ALARM` permission and boot-receiver machinery `AlarmManager`
 * would require.
 *
 * No [androidx.work.Constraints] are attached: day-close resolution must run
 * regardless of network, battery, or storage state. Backoff retries a transient
 * failure.
 */
object DayCloseScheduler {

    /**
     * Enqueue the day-close worker to run at the next local midnight, replacing
     * any already-scheduled run.
     *
     * Called at app start ([com.oink.app.OinkApplication.onCreate]) so the chain
     * is always live, and again by [DayCloseWorker] after each run so it
     * perpetuates. [ExistingWorkPolicy.REPLACE] both keeps the launch path from
     * stacking duplicates and lets the worker re-arm its own unique work as it
     * finishes; a REPLACE that races an in-flight run is safe because
     * [DayCloseResolver] is idempotent.
     */
    fun scheduleNextMidnight(context: Context) {
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        val initialDelay = Duration.between(now, nextMidnight)

        val request = OneTimeWorkRequestBuilder<DayCloseWorker>()
            .setInitialDelay(initialDelay)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            DayCloseWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
