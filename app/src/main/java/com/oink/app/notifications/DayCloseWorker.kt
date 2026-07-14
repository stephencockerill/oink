package com.oink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oink.app.OinkApplication
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.CancellationException

/**
 * Worker that resolves quit habits' passive clean days at day-close.
 *
 * Enqueued as one-time work anchored to the next local midnight (see
 * [DayCloseScheduler]). Each run materializes every quit habit's unresolved clean
 * days via [DayCloseResolver], refreshes widgets so the newly accrued balances
 * show, then reschedules the next midnight run so the chain perpetuates.
 *
 * The resolve is wrapped so a transient failure never breaks the daily chain:
 * the next run is always rescheduled, and because [DayCloseResolver] is
 * idempotent and resolves the full elapsed gap, a day missed here is simply
 * caught up on the following run.
 */
class DayCloseWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as OinkApplication).container
        val resolver = DayCloseResolver(
            container.habitRepository,
            container.checkInRepository
        )

        try {
            resolver.resolveElapsedDays()
            OinkWidget.updateAllWidgets(applicationContext)
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate, never be swallowed.
            throw e
        } catch (e: Exception) {
            // Swallow so the self-rescheduling chain below always continues; the
            // idempotent resolver catches up any unresolved day on the next run.
        }

        DayCloseScheduler.scheduleNextMidnight(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "day_close_work"
    }
}
