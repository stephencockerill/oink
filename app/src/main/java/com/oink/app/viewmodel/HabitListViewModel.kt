package com.oink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oink.app.AppContainer
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitType
import com.oink.app.utils.HeroSignals
import com.oink.app.utils.Mascot
import com.oink.app.utils.MascotState
import com.oink.app.utils.Milestone
import com.oink.app.widget.WidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable per-habit card state for the home list.
 *
 * All `val`s, so it is stable for Compose skipping. Everything the card renders
 * is precomputed here off the reactive sources, so the composable does no work.
 */
data class HabitCardState(
    val id: Long,
    val emoji: String,
    val name: String,
    val habitType: HabitType,
    val streak: Int,
    val availableFreezes: Int,
    val spendable: Long,
    /**
     * Today's check-in outcome: `true` done/clean, `false` missed/slipped,
     * `null` not logged yet. Drives the inline quick check-in control on the
     * card, which reads this differently per [habitType].
     */
    val todayCompleted: Boolean?
)

/**
 * Whether the home list has any public habit to show.
 *
 * [Loading] is the pre-resolution state before [HabitRepository.allHabits] has
 * emitted. The screen shows neither the list nor the empty state while loading,
 * so a populated database never flashes the empty state on open. [Empty] and
 * [HasHabits] are decided straight off the habit set rather than off
 * [HabitListViewModel.habitCards], so the decision is exact and not subject to
 * the per-card combine latency.
 */
sealed interface HomeListState {
    data object Loading : HomeListState
    data object Empty : HomeListState
    data object HasHabits : HomeListState
}

/**
 * ViewModel for the home habit list.
 *
 * Exposes the public (non-private) habits as [HabitCardState]s and the shared
 * pot as [overallBank]. There is no habit id here: the list spans every public
 * habit. Private habits are excluded - they surface behind the private area
 * (#38/#39), not on the shared home list.
 *
 * A plain [ViewModel] (not [androidx.lifecycle.AndroidViewModel]): it needs no
 * Application context.
 */
class HabitListViewModel(
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
    private val freezeRepository: FreezeRepository,
    private val cashOutRepository: CashOutRepository,
    private val widgetUpdater: WidgetUpdater = WidgetUpdater.NoOp
) : ViewModel() {

    /**
     * The public habits as card states.
     *
     * Rebuilds whenever the set of habits changes ([HabitRepository.allHabits]),
     * then combines each habit's per-field flows into one card state. Combining
     * over an empty iterable never emits, so an empty public-habit set is
     * short-circuited to an empty list rather than hanging.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val habitCards: StateFlow<List<HabitCardState>> = habitRepository.allHabits
        .flatMapLatest { habits ->
            val public = habits.filter { !it.isPrivate }
            if (public.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(public.map { habit -> cardStateFlow(habit) }) { cards -> cards.toList() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Whether there is any public habit to render, held separately from
     * [habitCards] so the home screen can tell "still loading" apart from
     * "loaded and genuinely empty" and only show the empty state once the habit
     * set has actually resolved.
     */
    val homeListState: StateFlow<HomeListState> = habitRepository.allHabits
        .map { habits ->
            if (habits.any { !it.isPrivate }) HomeListState.HasHabits else HomeListState.Empty
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeListState.Loading
        )

    /**
     * The shared piggy bank total: the sum of every public habit's spendable
     * balance. See [CashOutRepository.pot].
     *
     * The home list only ever renders public habit cards, so this bank is always
     * the public pot, keeping "overall bank = sum of the visible cards" true even
     * while the private area is unlocked. The unlock-aware pot lives on the
     * Rewards surface, where a claim can draw from private banks.
     */
    val overallBank: StateFlow<Long> = cashOutRepository.pot(includePrivate = false)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * The home hero state for the shared bank card.
     *
     * Balance is the shared public pot ([overallBank]); the rest is aggregated
     * across public habits: today's earned rewards sum into the gain chip, the
     * hottest streak flames, and the mascot's mood comes from the most recent
     * check-in's balance delta and date (a fresh halving on any public habit wins
     * a comeback). Milestone progress derives from the balance. All precomputed
     * here so [com.oink.app.ui.components.HeroBankCard] does no work.
     */
    val heroState: StateFlow<HeroBankState> = combine(
        overallBank,
        publicHeroSignals()
    ) { balance, signal ->
        HeroBankState(
            balanceCents = balance,
            dailyGainCents = signal.dailyGainCents,
            streak = signal.streak,
            mascotState = Mascot.stateFor(
                balanceDelta = signal.balanceDelta,
                currentStreak = signal.streak,
                lastCheckIn = signal.lastCheckIn,
                today = checkInRepository.today()
            ),
            milestone = Milestone.resolve(balance),
            label = HERO_LABEL,
            subtitle = HERO_SUBTITLE
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HeroBankState(
            balanceCents = 0L,
            dailyGainCents = 0L,
            streak = 0,
            mascotState = MascotState.SLEEPING,
            milestone = Milestone.resolve(0L),
            label = HERO_LABEL,
            subtitle = HERO_SUBTITLE
        )
    )

    /**
     * One public habit's hero signals, folded together across the set by
     * [aggregate]. Combining over an empty iterable never emits, so an empty
     * public set is short-circuited to a resting signal rather than hanging.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun publicHeroSignals(): Flow<AggregateSignal> = habitRepository.allHabits
        .flatMapLatest { habits ->
            val public = habits.filter { !it.isPrivate }
            if (public.isEmpty()) {
                flowOf(AggregateSignal(streak = 0, dailyGainCents = 0L, lastCheckIn = null, balanceDelta = 0L))
            } else {
                combine(public.map { habit -> habitSignalFlow(habit) }) { signals ->
                    aggregate(signals.toList())
                }
            }
        }

    /**
     * A single public habit's hero signals: its streak, the reward it banked
     * today (if any), and the date and balance delta of its most recent check-in.
     */
    private fun habitSignalFlow(habit: Habit): Flow<HabitSignal> = combine(
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
     * Fold per-habit signals into the one aggregate the hero renders.
     *
     * Streak is the hottest across habits; gains sum. The mascot delta comes only
     * from habits that acted on the most recent day, and a fresh loss on any of
     * them takes precedence over another habit's gain - matching [Mascot]'s own
     * "a streak can't paper over a loss" precedence.
     */
    private fun aggregate(signals: List<HabitSignal>): AggregateSignal {
        val maxStreak = signals.maxOfOrNull { it.streak } ?: 0
        val totalGain = signals.sumOf { it.gainCents }
        val latestDate = signals.mapNotNull { it.lastCheckIn }.maxOrNull()
        val onLatest = signals.filter { it.lastCheckIn == latestDate }
        val delta = when {
            onLatest.any { it.balanceDelta < 0 } -> onLatest.minOf { it.balanceDelta }
            else -> onLatest.maxOfOrNull { it.balanceDelta } ?: 0L
        }
        return AggregateSignal(
            streak = maxStreak,
            dailyGainCents = totalGain,
            lastCheckIn = latestDate,
            balanceDelta = delta
        )
    }

    /**
     * Combine one habit's reactive fields (spendable, freezes, streak) into a
     * single card state that re-emits whenever any of them changes.
     */
    private fun cardStateFlow(habit: Habit): Flow<HabitCardState> = combine(
        cashOutRepository.spendable(habit.id),
        freezeRepository.availableFreezes(habit.id),
        streakFlow(habit.id),
        todayCompletedFlow(habit.id)
    ) { spendable, freezes, streak, todayCompleted ->
        HabitCardState(
            id = habit.id,
            emoji = habit.emoji,
            name = habit.name,
            habitType = habit.habitType,
            streak = streak,
            availableFreezes = freezes,
            spendable = spendable,
            todayCompleted = todayCompleted
        )
    }

    /**
     * Today's check-in outcome for a habit (`true`/`false`/`null`), derived from
     * the same [CheckInRepository.allCheckIns] flow the streak uses rather than
     * [CheckInRepository.getTodayCheckIn].
     *
     * Deliberate: `getTodayCheckIn` runs a perpetual until-midnight timer to
     * auto-roll "today", but [streakFlow] evaluates `today()` off `allCheckIns`
     * and does not roll at midnight. Sourcing both from `allCheckIns` keeps the
     * card internally consistent (status and streak roll together on the next
     * data change) and avoids a forever-scheduled coroutine per card.
     */
    private fun todayCompletedFlow(habitId: Long): Flow<Boolean?> =
        checkInRepository.allCheckIns(habitId).map { checkIns ->
            val today = checkInRepository.today()
            checkIns.find { it.date == today }?.didSucceed
        }

    /**
     * Log (or change) today's check-in for a habit straight from its card.
     *
     * Mirrors [com.oink.app.viewmodel.MainViewModel.recordCheckIn]: the write and
     * widget refresh run under [NonCancellable] so a fast home-press can't orphan
     * them mid-write. Streak, spendable balance, and today's status all update
     * reactively through the card's flows - no manual refresh. A miss halves the
     * spendable balance immediately; re-logging done reverses it exactly, because
     * the balance is computed from the previous day's balance and replayed by
     * [CheckInRepository.recordCheckIn], not from the halved value.
     */
    fun recordCheckIn(habitId: Long, didSucceed: Boolean) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed, habitId)
                widgetUpdater.update()
            }
        }
    }

    /**
     * A habit's current streak, derived from its check-ins and frozen days so it
     * can never go stale: any check-in or freeze change re-emits and recomputes.
     */
    private fun streakFlow(habitId: Long): Flow<Int> = combine(
        checkInRepository.allCheckIns(habitId),
        freezeRepository.frozenDates(habitId)
    ) { checkIns, frozen ->
        checkInRepository.calculateStreak(checkIns, frozen)
    }

    /**
     * One public habit's contribution to the hero, before aggregation.
     */
    private data class HabitSignal(
        val streak: Int,
        val gainCents: Long,
        val lastCheckIn: LocalDate?,
        val balanceDelta: Long
    )

    /**
     * The public-habit set folded into a single hero signal.
     */
    private data class AggregateSignal(
        val streak: Int,
        val dailyGainCents: Long,
        val lastCheckIn: LocalDate?,
        val balanceDelta: Long
    )

    companion object {
        private const val HERO_LABEL = "Piggy Bank"
        private const val HERO_SUBTITLE = "Shared across all your habits"

        /**
         * Factory that builds the ViewModel from the [AppContainer]. This
         * ViewModel needs neither an Application context nor a habit id, so the
         * container is simply closed over.
         */
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabitListViewModel(
                        habitRepository = container.habitRepository,
                        checkInRepository = container.checkInRepository,
                        freezeRepository = container.freezeRepository,
                        cashOutRepository = container.cashOutRepository,
                        widgetUpdater = container.widgetUpdater
                    )
                }
            }
    }
}
