package com.oink.app.widget

import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.HabitRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalTime

/**
 * Loads and re-validates the data one widget instance renders for its chosen
 * habit.
 *
 * A widget instance stores only a `habitId`; everything shown is derived here at
 * render time. Re-validating on every render is what stops a widget leaking a
 * habit that has since been deleted or toggled private: [resolveWidgetData]
 * returns null - the caller's cue to draw the neutral fallback - whenever the
 * habit is missing or private, so a private or deleted habit can never reach the
 * launcher.
 *
 * This lives outside the Glance widget so the validation + per-habit loading is
 * a plain suspend function, unit-testable with the repository fakes and no
 * Glance rendering harness.
 */
class WidgetDataLoader(
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
    private val cashOutRepository: CashOutRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    /**
     * Resolve the render data for [habitId], or null when the widget must fall
     * back to its neutral state.
     *
     * Returns null when the habit is missing/deleted or currently private. The
     * privacy check reads the habit's live [com.oink.app.data.Habit.isPrivate],
     * so flipping a habit to private blanks its widget on the next render.
     *
     * On success the balance is the habit's own spendable share
     * ([CashOutRepository.spendable]) and the streak is the habit's own streak
     * ([CheckInRepository.calculateStreak]) - never the global pot.
     */
    suspend fun resolveWidgetData(habitId: Long): WidgetData? {
        val habit = habitRepository.getHabit(habitId) ?: return null
        if (habit.isPrivate) return null

        val today = checkInRepository.today()
        val todayCheckIn = checkInRepository.getCheckInForDate(today, habitId)
        val balance = cashOutRepository.spendable(habitId).first()
        val streak = checkInRepository.calculateStreak(habitId)

        return WidgetData(
            habitName = habit.name,
            habitEmoji = habit.emoji,
            balance = balance,
            streak = streak,
            checkedInToday = todayCheckIn != null,
            exercisedToday = todayCheckIn?.didExercise,
            currentHour = LocalTime.now(clock).hour
        )
    }
}
