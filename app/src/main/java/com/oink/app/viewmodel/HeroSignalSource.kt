package com.oink.app.viewmodel

import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.utils.HeroSignals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * One habit's contribution to the hero, before aggregation.
 */
data class HabitSignal(
    val streak: Int,
    val gainCents: Long,
    val lastCheckIn: LocalDate?,
    val balanceDelta: Long
)

/**
 * A habit set folded into a single hero signal.
 */
data class HeroAggregate(
    val streak: Int,
    val dailyGainCents: Long,
    val lastCheckIn: LocalDate?,
    val balanceDelta: Long
)

/**
 * Shared derivation of the hero-card signals both the home list and the Rewards
 * screen show above their bank card.
 *
 * The two surfaces differ only in which habits are in scope - the home list is
 * always public-only, Rewards is gated by the private-area unlock - so each
 * ViewModel resolves its own in-scope habit set and hands it to
 * [aggregatedSignals]. Everything downstream (streak, today's gain, most-recent
 * check-in delta, and the fold into one [HeroAggregate]) is identical and lives
 * here so it exists once.
 *
 * Constructor-injected repositories, matching the app's manual-DI idiom; see
 * [com.oink.app.AppContainer].
 */
class HeroSignalSource(
    private val checkInRepository: CheckInRepository,
    private val freezeRepository: FreezeRepository
) {

    /**
     * Hero signals aggregated across an already-resolved in-scope habit set.
     * Combining over an empty iterable never emits, so an empty set is
     * short-circuited to a resting signal rather than hanging.
     */
    fun aggregatedSignals(inScope: List<Habit>): Flow<HeroAggregate> =
        if (inScope.isEmpty()) {
            flowOf(HeroAggregate(streak = 0, dailyGainCents = 0L, lastCheckIn = null, balanceDelta = 0L))
        } else {
            combine(inScope.map { habit -> habitSignalFlow(habit) }) { signals ->
                aggregate(signals.toList())
            }
        }

    /**
     * A single habit's hero signals: its streak, the reward it banked today (if
     * any), and the date and balance delta of its most recent check-in.
     */
    fun habitSignalFlow(habit: Habit): Flow<HabitSignal> = combine(
        streakFlow(habit.id),
        checkInRepository.allCheckIns(habit.id)
    ) { streak, checkIns ->
        val today = checkInRepository.today()
        val todayCheckIn = checkIns.find { it.date == today }
        val gain = if (todayCheckIn?.didSucceed == true) habit.rewardValue else 0L
        val recent = HeroSignals.recent(checkIns)
        HabitSignal(
            streak = streak,
            gainCents = gain,
            lastCheckIn = recent.lastCheckIn,
            balanceDelta = recent.balanceDelta
        )
    }

    /**
     * A habit's current streak, derived from its check-ins and frozen days so it
     * can never go stale: any check-in or freeze change re-emits and recomputes.
     */
    fun streakFlow(habitId: Long): Flow<Int> = combine(
        checkInRepository.allCheckIns(habitId),
        freezeRepository.frozenDates(habitId)
    ) { checkIns, frozen ->
        checkInRepository.calculateStreak(checkIns, frozen)
    }

    /**
     * Fold per-habit signals into the one aggregate the hero renders: hottest
     * streak, summed gains, and the mascot delta from habits that acted on the
     * most recent day - a fresh loss on any of them wins over another's gain,
     * matching [com.oink.app.utils.Mascot]'s "a streak can't paper over a loss"
     * precedence.
     */
    fun aggregate(signals: List<HabitSignal>): HeroAggregate {
        val maxStreak = signals.maxOfOrNull { it.streak } ?: 0
        val totalGain = signals.sumOf { it.gainCents }
        val latestDate = signals.mapNotNull { it.lastCheckIn }.maxOrNull()
        val onLatest = signals.filter { it.lastCheckIn == latestDate }
        val delta = when {
            onLatest.any { it.balanceDelta < 0 } -> onLatest.minOf { it.balanceDelta }
            else -> onLatest.maxOfOrNull { it.balanceDelta } ?: 0L
        }
        return HeroAggregate(
            streak = maxStreak,
            dailyGainCents = totalGain,
            lastCheckIn = latestDate,
            balanceDelta = delta
        )
    }
}
