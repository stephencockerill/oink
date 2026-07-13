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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

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
    val streak: Int,
    val availableFreezes: Int,
    val spendable: Long
)

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
    private val cashOutRepository: CashOutRepository
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
     * The shared piggy bank total: the sum of every public habit's spendable
     * balance. See [CashOutRepository.pot].
     */
    val overallBank: StateFlow<Long> = cashOutRepository.pot
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * Combine one habit's reactive fields (spendable, freezes, streak) into a
     * single card state that re-emits whenever any of them changes.
     */
    private fun cardStateFlow(habit: Habit): Flow<HabitCardState> = combine(
        cashOutRepository.spendable(habit.id),
        freezeRepository.availableFreezes(habit.id),
        streakFlow(habit.id)
    ) { spendable, freezes, streak ->
        HabitCardState(
            id = habit.id,
            emoji = habit.emoji,
            name = habit.name,
            streak = streak,
            availableFreezes = freezes,
            spendable = spendable
        )
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

    companion object {
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
                        cashOutRepository = container.cashOutRepository
                    )
                }
            }
    }
}
