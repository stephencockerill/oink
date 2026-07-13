package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oink.app.data.CashOut
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckIn
import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.utils.Formatters
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Main ViewModel for the Oink app.
 *
 * This is where the magic happens. All the UI state flows through here,
 * and all user actions get processed here.
 *
 * Why AndroidViewModel instead of plain ViewModel?
 * Because we need Application context to update the home screen widget
 * when check-ins change. We use Application (not Activity) context
 * to avoid memory leaks.
 *
 * Why StateFlow instead of LiveData? Because:
 * 1. StateFlow is lifecycle-aware when collected with collectAsStateWithLifecycle()
 * 2. It plays nicer with Compose
 * 3. It's got null safety built in
 * 4. It's the modern way, and LiveData is showing its age
 */
class MainViewModel(
    application: Application,
    private val repository: CheckInRepository,
    private val habitRepository: HabitRepository,
    private val cashOutRepository: CashOutRepository,
    private val freezeRepository: FreezeRepository
) : AndroidViewModel(application) {

    /**
     * The habit this screen operates on. The app is single-habit for now, so
     * every check-in, streak, and freeze read/write targets the seeded default
     * habit.
     */
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID

    /**
     * Current spendable balance as a StateFlow.
     *
     * This is the shared pot: the sum of every public habit's spendable balance.
     * See [CashOutRepository.pot] for the derivation.
     */
    val currentBalance: StateFlow<Long> = cashOutRepository.pot
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * Today's check-in status.
     * null means not checked in yet.
     */
    val todayCheckIn: StateFlow<CheckIn?> = repository.getTodayCheckIn(habitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * All check-ins for the history screen.
     */
    val allCheckIns: StateFlow<List<CheckIn>> = repository.allCheckIns(habitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * All cash-outs for reward indicators on calendar/history.
     */
    val allCashOuts: StateFlow<List<CashOut>> = cashOutRepository.allCashOuts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Loading state for UI feedback.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Error state for displaying error messages.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * This habit's per-day reward, in cents. Sourced from [Habit.rewardValue],
     * the single source of truth, so a settings edit flows straight through.
     */
    val exerciseReward: StateFlow<Long> = habitRepository.habit(habitId)
        .map { it?.rewardValue ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_EXERCISE_REWARD
        )

    /**
     * Freeze cost (2x this habit's reward), in cents.
     */
    val freezeCost: StateFlow<Long> = habitRepository.habit(habitId)
        .map { (it?.rewardValue ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD) * 2 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_EXERCISE_REWARD * 2
        )

    /**
     * Available streak freezes for this habit.
     */
    val availableFreezes: StateFlow<Int> = freezeRepository.availableFreezes(habitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Current frozen dates for this habit.
     */
    val frozenDates: StateFlow<Set<LocalDate>> = freezeRepository.frozenDates(habitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    /**
     * Missed days the user has dismissed this session, so the freeze prompt
     * stops surfacing them. This is genuine transient UI state (not persisted
     * and not derivable from stored data), so it stays a MutableStateFlow.
     */
    private val _dismissedFreezeDates = MutableStateFlow<Set<LocalDate>>(emptySet())

    /**
     * Current streak count.
     *
     * Derived from check-ins and frozen dates so it can never go stale: any
     * check-in edit or freeze change re-emits and recomputes automatically.
     */
    val streak: StateFlow<Int> = combine(
        repository.allCheckIns(habitId),
        frozenDates
    ) { checkIns, frozen ->
        repository.calculateStreak(checkIns, frozen)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    /**
     * Preview of what the spendable balance would be if the user exercises.
     *
     * Actual preview = raw preview - total deductions (cash-outs + freeze
     * spending), floored at zero.
     */
    val exercisePreview: StateFlow<Long> = combine(
        repository.currentBalance(habitId),
        exerciseReward,
        cashOutRepository.allocatedForHabit(habitId),
        freezeRepository.totalFreezeSpending(habitId)
    ) { rawBalance, reward, allocated, freezeSpending ->
        val deductions = allocated + freezeSpending
        val rawPreview = repository.previewExerciseBalance(rawBalance, reward, deductions)
        (rawPreview - deductions).coerceAtLeast(0L)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    /**
     * Preview of what the spendable balance would be if the user misses.
     */
    val missPreview: StateFlow<Long> = combine(
        repository.currentBalance(habitId),
        cashOutRepository.allocatedForHabit(habitId),
        freezeRepository.totalFreezeSpending(habitId)
    ) { rawBalance, allocated, freezeSpending ->
        val deductions = allocated + freezeSpending
        val rawPreview = repository.previewMissBalance(rawBalance, deductions)
        (rawPreview - deductions).coerceAtLeast(0L)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    /**
     * A missed day that could be frozen (if any).
     * null means no missed day needs attention.
     *
     * Dismissed dates are folded into the skip set alongside frozen dates so a
     * dismissed prompt doesn't immediately reappear.
     */
    val missedDayForFreeze: StateFlow<LocalDate?> = combine(
        repository.allCheckIns(habitId),
        frozenDates,
        _dismissedFreezeDates
    ) { checkIns, frozen, dismissed ->
        repository.findMissedDayForFreeze(checkIns, frozen + dismissed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Purchase a streak freeze.
     * Freezes are FREE to acquire but cost 2x exercise reward to USE.
     * Max 2 freezes at a time.
     *
     * Results are observed through:
     * - [availableFreezes] StateFlow for the updated count
     * - [error] StateFlow for any error messages
     */
    fun purchaseFreeze() {
        if (availableFreezes.value >= PreferencesRepository.MAX_FREEZES) {
            _error.value = "Already have max freezes (${PreferencesRepository.MAX_FREEZES})"
            return
        }

        viewModelScope.launch {
            try {
                // availableFreezes updates reactively via the habit flow.
                freezeRepository.purchaseFreeze(habitId)
            } catch (e: Exception) {
                _error.value = "Failed to get freeze: ${e.message}"
            }
        }
    }

    /**
     * Use a freeze for a missed day.
     * This costs 2x exercise reward from balance and preserves the streak.
     *
     * The cost is tracked separately as "freeze spending" rather than
     * being deducted from check-in balances. This prevents the bug where
     * toggling a check-in would lose track of money spent on freezes.
     *
     * @param date The date to freeze
     */
    fun useFreeze(date: LocalDate) {
        val balance = currentBalance.value
        val cost = freezeCost.value
        if (balance < cost) {
            _error.value = "Not enough balance! Need ${Formatters.formatCurrency(cost)} to use a freeze"
            return
        }
        if (availableFreezes.value <= 0) {
            _error.value = "No freezes available! Buy one in Settings first."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Critical operations - must complete even if user exits.
                // The freeze mutations flow back through the preferences and
                // check-in flows, so streak/balance/missedDayForFreeze update
                // reactively - no manual refresh needed.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    // Use the freeze (decrements available count, records the frozen day)
                    if (freezeRepository.useFreeze(habitId, date)) {
                        // Track freeze spending separately (NOT deducting from check-in!)
                        freezeRepository.addFreezeSpending(habitId, cost)
                        // Update widget immediately
                        updateWidget()
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to use freeze: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Dismiss the freeze prompt without using a freeze.
     *
     * Records the currently-surfaced missed day as dismissed so the reactive
     * [missedDayForFreeze] derivation skips past it instead of re-showing it.
     */
    fun dismissFreezePrompt() {
        val date = missedDayForFreeze.value ?: return
        _dismissedFreezeDates.update { it + date }
    }

    /**
     * Record a check-in for today.
     *
     * @param didExercise Whether the user exercised today
     */
    fun recordTodayCheckIn(didExercise: Boolean) {
        recordCheckIn(repository.today(), didExercise)
    }

    /**
     * Record a check-in for a specific date.
     * This allows retroactive logging.
     *
     * @param date The date to record the check-in for
     * @param didExercise Whether the user exercised on that date
     */
    fun recordCheckIn(date: LocalDate, didExercise: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Wrap the critical operations in NonCancellable so they complete
                // even if user presses home button immediately after tapping
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.recordCheckIn(date, didExercise, habitId)
                    // Update widget IMMEDIATELY after DB write.
                    // This ensures widget gets updated even if user exits fast.
                    // UI state (streak, previews, balance) updates reactively
                    // off the check-in flow, so no manual refresh is needed.
                    updateWidget()
                }
            } catch (e: Exception) {
                _error.value = "Failed to save check-in: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Bulk record check-ins for multiple dates.
     *
     * Used for calendar multi-select feature. Marks all selected dates
     * as either exercised or missed in one operation.
     *
     * @param dates Set of dates to update
     * @param didExercise Whether these days were exercise days
     */
    fun bulkRecordCheckIns(dates: Set<LocalDate>, didExercise: Boolean) {
        if (dates.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.bulkRecordCheckIns(dates, didExercise, habitId)
                    updateWidget()
                }
            } catch (e: Exception) {
                _error.value = "Failed to save check-ins: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update the home screen widget.
     * Called within NonCancellable context from recordCheckIn.
     */
    private suspend fun updateWidget() {
        try {
            android.util.Log.d("MainViewModel", "Triggering widget update")
            OinkWidget.updateAllWidgets(getApplication())
            android.util.Log.d("MainViewModel", "Widget update completed")
        } catch (e: Exception) {
            // Widget update failure shouldn't crash the app
            // Just log and continue
            android.util.Log.e("MainViewModel", "Widget update failed", e)
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Factory for creating MainViewModel with dependencies.
     *
     * Why a factory? Because ViewModels shouldn't have dependencies
     * passed through their constructor directly - they need to survive
     * configuration changes, and the system needs to know how to
     * recreate them. This factory tells it how.
     */
    class Factory(
        private val application: Application,
        private val repository: CheckInRepository,
        private val habitRepository: HabitRepository,
        private val cashOutRepository: CashOutRepository,
        private val freezeRepository: FreezeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository, habitRepository, cashOutRepository, freezeRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

