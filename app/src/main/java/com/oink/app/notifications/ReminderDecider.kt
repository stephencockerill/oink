package com.oink.app.notifications

import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitType

/**
 * What the single global daily notification should say when it fires.
 *
 * There is one notification slot, shared by build and quit habits, so the
 * decider collapses every habit into one outcome:
 * - [BuildNudge]: at least one build habit still has outstanding work today.
 *   The actionable nudge wins, so this takes priority over a celebration.
 * - [QuitCelebration]: no build habit needs nudging, but a quit habit has a
 *   positive clean streak worth celebrating. [cleanStreak] is the best clean
 *   streak across the eligible quit habits.
 * - [None]: nothing to say.
 */
sealed interface ReminderDecision {
    data object None : ReminderDecision
    data object BuildNudge : ReminderDecision
    data class QuitCelebration(val cleanStreak: Int) : ReminderDecision
}

/**
 * Decides what the single global daily notification should say.
 *
 * The app shows ONE daily notification, not one per habit, so the decision is an
 * aggregate over every non-private habit. The two habit types drive it
 * differently:
 *
 * - A **build** habit is "pending" when it has no completed check-in today, and a
 *   pending build habit fires the actionable [ReminderDecision.BuildNudge].
 * - A **quit** habit never fires the build nudge: its success is passive, so an
 *   unlogged day is the expected clean-so-far state, not outstanding work. A quit
 *   habit instead fires a pride-framed [ReminderDecision.QuitCelebration] of its
 *   clean streak - and only when nothing needs nudging. Crucially, the
 *   celebration is streak-framed and never references the avoided behavior
 *   (ironic-process caution, see docs/negative-habits.md): a "don't do X" cue can
 *   trigger the very craving it warns against, so the notification carries only
 *   the streak number, never the habit's name or the behavior.
 *
 * Private habits are excluded from the decision entirely: they are excluded from
 * every aggregate (docs/multi-habit-plan.md, "Private habits - design", rule 4),
 * and this notification is a public surface, so a pending or streaking private
 * habit must never be the reason it fires.
 *
 * "Today" comes from [CheckInRepository.today] so there is one authoritative
 * source of the current date across the app.
 *
 * This lives outside [ReminderWorker] so the decision is a plain suspend function,
 * unit-testable with the repository fakes and no WorkManager harness - mirroring
 * [com.oink.app.widget.WidgetDataLoader].
 */
class ReminderDecider(
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
    private val freezeRepository: FreezeRepository
) {

    /**
     * Collapse every non-private habit into a single notification decision. See
     * the class doc for how build and quit habits each contribute.
     */
    suspend fun decide(): ReminderDecision {
        val today = checkInRepository.today()
        val habits = habitRepository.getAllHabits().filter { !it.isPrivate }

        val buildPending = habits
            .filter { it.habitType == HabitType.BUILD }
            .any { habit ->
                val checkIn = checkInRepository.getCheckInForDate(today, habit.id)
                checkIn == null || !checkIn.didSucceed
            }
        if (buildPending) return ReminderDecision.BuildNudge

        var bestCleanStreak = 0
        for (habit in habits.filter { it.habitType == HabitType.QUIT }) {
            val frozen = freezeRepository.getFrozenDates(habit.id)
            bestCleanStreak = maxOf(bestCleanStreak, checkInRepository.calculateStreak(habit.id, frozen))
        }

        return if (bestCleanStreak > 0) {
            ReminderDecision.QuitCelebration(bestCleanStreak)
        } else {
            ReminderDecision.None
        }
    }
}
