package com.oink.app.notifications

import com.oink.app.data.CheckInRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Materializes the passive clean days a quit habit has earned but not yet
 * recorded.
 *
 * A quit habit's good day is a non-event: the user does nothing, so no check-in
 * exists until the day is over. Because the balance ledger is stored per row
 * ([com.oink.app.data.CheckIn.balanceAfter]), those clean days must be written
 * to accrue their reward. This runs at day-close (local midnight) and, for every
 * [HabitType.QUIT] habit, inserts a clean check-in
 * ([CheckInRepository.recordCheckIn] `didSucceed = true`) for each unresolved
 * elapsed day from the day after the habit's last check-in through yesterday that
 * has no check-in yet.
 *
 * It handles catch-up, not just the single just-ended day: if the device was off
 * for several days the whole gap resolves at once. A day with a logged slip
 * already has a check-in, so it is left untouched. Today and future days are
 * never resolved - a clean day cannot be claimed before it is over.
 *
 * It is idempotent: a second run finds every day already resolved and writes
 * nothing. Build habits are ignored entirely - their unlogged days stay misses.
 *
 * This lives outside [DayCloseWorker] so the resolve decision is a plain suspend
 * function, unit-testable with the repository fakes and a fixed [Clock] and no
 * WorkManager harness - mirroring [ReminderDecider] and
 * [com.oink.app.widget.WidgetDataLoader].
 */
class DayCloseResolver(
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    /**
     * Resolve every quit habit's unresolved elapsed clean days. See the class
     * doc for the range, catch-up, slip-skipping, and idempotency guarantees.
     */
    suspend fun resolveElapsedDays() {
        val yesterday = LocalDate.now(clock).minusDays(1)

        habitRepository.getAllHabits()
            .filter { it.habitType == HabitType.QUIT }
            .forEach { habit ->
                val start = firstUnresolvedDay(habit.id, habit.createdAt)

                // Only accumulate days with no check-in yet, so a logged slip
                // (which already has a check-in) is left as the resolution it
                // already is and never overwritten to clean.
                val cleanDays = buildSet {
                    var date = start
                    while (!date.isAfter(yesterday)) {
                        if (checkInRepository.getCheckInForDate(date, habit.id) == null) {
                            add(date)
                        }
                        date = date.plusDays(1)
                    }
                }

                if (cleanDays.isNotEmpty()) {
                    // bulkRecordCheckIns inserts the new clean days and replays
                    // the ledger once. It never touches today or future days
                    // because none are in the set.
                    checkInRepository.bulkRecordCheckIns(cleanDays, didSucceed = true, habit.id)
                }
            }
    }

    /**
     * The first day that could still need resolving for a habit: the day after
     * its most recent check-in, or - when it has none yet - its creation day, so
     * a brand-new quit habit accrues from the day it was created rather than from
     * the epoch.
     */
    private suspend fun firstUnresolvedDay(habitId: Long, createdAtMillis: Long): LocalDate {
        val lastCheckIn = checkInRepository.getLatestCheckInOnce(habitId)
        return lastCheckIn?.date?.plusDays(1)
            ?: Instant.ofEpochMilli(createdAtMillis).atZone(clock.zone).toLocalDate()
    }
}
