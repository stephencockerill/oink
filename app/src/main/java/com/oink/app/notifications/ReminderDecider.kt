package com.oink.app.notifications

import com.oink.app.data.CheckInDao
import com.oink.app.data.HabitDao
import java.time.Clock
import java.time.LocalDate

/**
 * Decides whether the single global daily reminder should fire.
 *
 * The app shows ONE daily nudge, not one per habit, so the decision is an
 * aggregate over every habit: fire if the user still has outstanding work today,
 * i.e. at least one habit has no completed check-in for today. Suppress only when
 * every eligible habit is already completed today (and suppress when there are no
 * eligible habits at all - there is nothing to nudge about).
 *
 * Private habits are excluded from the decision. Private habits are excluded from
 * every aggregate (see `docs/multi-habit-plan.md`, "Private habits - design",
 * rule 4), and this global nudge is a public surface, so a pending private habit
 * must never be the reason a reminder fires.
 *
 * This lives outside [ReminderWorker] so the decision is a plain suspend function,
 * unit-testable with the DAO fakes and no WorkManager harness - mirroring
 * [com.oink.app.widget.WidgetDataLoader].
 */
class ReminderDecider(
    private val habitDao: HabitDao,
    private val checkInDao: CheckInDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    /**
     * True when at least one non-private habit has no completed check-in for
     * today (no check-in at all, or one logged as not completed).
     *
     * Returns false when every non-private habit is completed today, and false
     * when there are no non-private habits.
     */
    suspend fun shouldRemind(): Boolean {
        val todayEpochDay = LocalDate.now(clock).toEpochDay()
        return habitDao.getAllHabits()
            .filter { !it.isPrivate }
            .any { habit ->
                val checkIn = checkInDao.getCheckInForDate(habit.id, todayEpochDay)
                checkIn == null || !checkIn.didSucceed
            }
    }
}
