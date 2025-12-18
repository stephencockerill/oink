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
import com.oink.app.data.PreferencesRepository
import com.oink.app.utils.BalanceCalculator
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val preferencesRepository: PreferencesRepository,
    private val cashOutRepository: CashOutRepository
) : AndroidViewModel(application) {

    /**
     * Current balance as a StateFlow.
     *
     * Uses BalanceCalculator for the actual calculation - see that class
     * for the formula and rationale.
     */
    val currentBalance: StateFlow<Double> = combine(
        repository.currentBalance,
        cashOutRepository.totalCashedOut,
        preferencesRepository.totalFreezeSpending
    ) { checkInBalance, cashedOut, freezeSpending ->
        BalanceCalculator.calculateActualBalance(checkInBalance, cashedOut, freezeSpending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    /**
     * Today's check-in status.
     * null means not checked in yet.
     */
    val todayCheckIn: StateFlow<CheckIn?> = repository.getTodayCheckIn()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * All check-ins for the history screen.
     */
    val allCheckIns: StateFlow<List<CheckIn>> = repository.allCheckIns
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
     * Current streak count.
     */
    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    /**
     * Preview of what balance would be if user exercises.
     */
    private val _exercisePreview = MutableStateFlow(0.0)
    val exercisePreview: StateFlow<Double> = _exercisePreview.asStateFlow()

    /**
     * Preview of what balance would be if user misses.
     */
    private val _missPreview = MutableStateFlow(0.0)
    val missPreview: StateFlow<Double> = _missPreview.asStateFlow()

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
     * Available streak freezes.
     */
    private val _availableFreezes = MutableStateFlow(0)
    val availableFreezes: StateFlow<Int> = _availableFreezes.asStateFlow()

    /**
     * A missed day that could be frozen (if any).
     * null means no missed day needs attention.
     */
    private val _missedDayForFreeze = MutableStateFlow<LocalDate?>(null)
    val missedDayForFreeze: StateFlow<LocalDate?> = _missedDayForFreeze.asStateFlow()

    /**
     * Current frozen dates.
     */
    private val _frozenDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val frozenDates: StateFlow<Set<LocalDate>> = _frozenDates.asStateFlow()

    /**
     * Current exercise reward amount.
     */
    private val _exerciseReward = MutableStateFlow(PreferencesRepository.DEFAULT_EXERCISE_REWARD)
    val exerciseReward: StateFlow<Double> = _exerciseReward.asStateFlow()

    /**
     * Freeze cost (2x exercise reward).
     */
    private val _freezeCost = MutableStateFlow(PreferencesRepository.DEFAULT_EXERCISE_REWARD * 2)
    val freezeCost: StateFlow<Double> = _freezeCost.asStateFlow()

    init {
        refreshData()
    }

    /**
     * Refresh all data.
     * Call this when the app resumes or when data might be stale.
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                // Load exercise reward setting
                val reward = preferencesRepository.getExerciseReward()
                _exerciseReward.value = reward
                _freezeCost.value = reward * 2

                // Load freeze data
                _availableFreezes.value = preferencesRepository.getAvailableFreezes()
                _frozenDates.value = preferencesRepository.getFrozenDates()

                // Calculate streak with frozen dates considered
                _streak.value = repository.calculateStreak(_frozenDates.value)

                // Calculate previews that account for deductions
                // Raw preview = what the check-in balance would be
                // Actual preview = raw preview - total deductions
                val totalDeductions = cashOutRepository.getTotalCashedOut() +
                    preferencesRepository.getTotalFreezeSpending()

                val rawExercisePreview = repository.previewExerciseBalance()
                val rawMissPreview = repository.previewMissBalance()

                _exercisePreview.value = (rawExercisePreview - totalDeductions).coerceAtLeast(0.0)
                _missPreview.value = (rawMissPreview - totalDeductions).coerceAtLeast(0.0)

                // Check for missed days that could be frozen
                _missedDayForFreeze.value = repository.findMissedDayForFreeze(_frozenDates.value)
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            }
        }
    }

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
        if (_availableFreezes.value >= PreferencesRepository.MAX_FREEZES) {
            _error.value = "Already have max freezes (${PreferencesRepository.MAX_FREEZES})"
            return
        }

        viewModelScope.launch {
            try {
                if (preferencesRepository.purchaseFreeze()) {
                    _availableFreezes.value = preferencesRepository.getAvailableFreezes()
                }
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
        val cost = _freezeCost.value
        if (balance < cost) {
            _error.value = "Not enough balance! Need \$${cost.toInt()} to use a freeze"
            return
        }
        if (_availableFreezes.value <= 0) {
            _error.value = "No freezes available! Buy one in Settings first."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Critical operations - must complete even if user exits
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    // Use the freeze (decrements available count, adds to frozen dates)
                    if (preferencesRepository.useFreeze(date)) {
                        // Track freeze spending separately (NOT deducting from check-in!)
                        preferencesRepository.addFreezeSpending(cost)
                        // Update widget immediately
                        updateWidget()
                    }
                }
                // UI state refresh can be cancelled
                refreshData()
                _missedDayForFreeze.value = null
            } catch (e: Exception) {
                _error.value = "Failed to use freeze: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Dismiss the freeze prompt without using a freeze.
     */
    fun dismissFreezePrompt() {
        _missedDayForFreeze.value = null
    }

    /**
     * Record a check-in for today.
     *
     * @param didExercise Whether the user exercised today
     */
    fun recordTodayCheckIn(didExercise: Boolean) {
        recordCheckIn(LocalDate.now(), didExercise)
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
                    repository.recordCheckIn(date, didExercise)
                    // Update widget IMMEDIATELY after DB write, before refreshData
                    // This ensures widget gets updated even if user exits fast
                    updateWidget()
                }
                // UI state refresh can be cancelled - it's not critical
                refreshData()
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
                    repository.bulkRecordCheckIns(dates, didExercise)
                    updateWidget()
                }
                refreshData()
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
        private val preferencesRepository: PreferencesRepository,
        private val cashOutRepository: CashOutRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository, preferencesRepository, cashOutRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

