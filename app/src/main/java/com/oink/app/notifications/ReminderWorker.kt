package com.oink.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oink.app.OinkApplication
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.flow.first

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
        // Fire the single global notification per the aggregate decision: a build
        // nudge when a build habit is still pending, else a pride-framed quit
        // celebration, else nothing. The decision lives in ReminderDecider so it
        // is unit-testable without a WorkManager harness.
        val container = (applicationContext as OinkApplication).container
        val decider = ReminderDecider(
            container.habitRepository,
            container.checkInRepository,
            container.freezeRepository
        )
        when (val decision = decider.decide()) {
            ReminderDecision.BuildNudge ->
                NotificationHelper.showDailyReminder(applicationContext)
            is ReminderDecision.QuitCelebration ->
                NotificationHelper.showQuitCelebration(applicationContext, decision.cleanStreak)
            ReminderDecision.None -> Unit
        }

        // Always refresh the widget so urgency state is current
        OinkWidget.updateAllWidgets(applicationContext)

        // Reschedule the next day's run anchored to the wall-clock target time.
        // Reading preferences here keeps them the single source of truth: if the
        // user disabled reminders, we stop; if they changed the time, we honor it.
        val prefs = container.preferencesRepository.userPreferences.first()
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

