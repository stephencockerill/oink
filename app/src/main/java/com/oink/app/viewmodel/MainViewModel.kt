package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.oink.app.AppContainer
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckIn
import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
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
    private val freezeRepository: FreezeRepository,
    private val privateGate: PrivateGate,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    /**
     * The habit this detail screen operates on, taken from the `habit/{habitId}`
     * route argument via [SavedStateHandle]. Every check-in, streak, balance, and
     * freeze read/write targets this habit, so two habits never collide. Falls
     * back to the seeded default habit if the argument is absent.
     */
    private val habitId: Long =
        savedStateHandle.get<Long>(HABIT_ID_KEY) ?: HabitRepository.DEFAULT_HABIT_ID

    /**
     * This habit's display name, for the detail top bar. Empty until the habit
     * row loads (or if it does not exist).
     */
    val habitName: StateFlow<String> = habitRepository.habit(habitId)
        .map { it?.name ?: "" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /**
     * This habit's emoji, for the detail top bar.
     */
    val habitEmoji: StateFlow<String> = habitRepository.habit(habitId)
        .map { it?.emoji ?: "" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    /**
     * True when this habit is private and the private gate is locked.
     *
     * The [PrivateGate] on its own only guards the dedicated private area; a
     * per-habit screen (detail/history/calendar/settings) reached under it would
     * otherwise keep rendering a private habit's data after a background re-lock.
     * The screen observes this and pops back out of the private subtree to the
     * PIN gate when it flips true. Public habits are never gated: a null/absent or
     * public habit yields false regardless of the unlock flag.
     */
    val privateLocked: StateFlow<Boolean> = combine(
        habitRepository.habit(habitId),
        privateGate.isUnlocked
    ) { habit, unlocked ->
        habit?.isPrivate == true && !unlocked
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /**
     * This habit's spendable balance as a StateFlow.
     *
     * Detail is per-habit, so this is THIS habit's spendable balance
     * (raw check-in balance - its cash-out allocations - its freeze spending),
     * not the shared pot. The home list shows the pot as the overall bank. Using
     * the per-habit balance here also makes the freeze-affordability check in
     * [useFreeze] correct for this habit. See [CashOutRepository.spendable].
     */
    val currentBalance: StateFlow<Long> = cashOutRepository.spendable(habitId)
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
    val dailyReward: StateFlow<Long> = habitRepository.habit(habitId)
        .map { it?.rewardValue ?: PreferencesRepository.DEFAULT_DAILY_REWARD }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_DAILY_REWARD
        )

    /**
     * Freeze cost (2x this habit's reward), in cents.
     */
    val freezeCost: StateFlow<Long> = habitRepository.habit(habitId)
        .map { (it?.rewardValue ?: PreferencesRepository.DEFAULT_DAILY_REWARD) * 2 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_DAILY_REWARD * 2
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
     * Preview of what the spendable balance would be if the user completes today.
     *
     * Actual preview = raw preview - total deductions (cash-outs + freeze
     * spending), floored at zero.
     */
    val completedPreview: StateFlow<Long> = combine(
        repository.currentBalance(habitId),
        dailyReward,
        cashOutRepository.allocatedForHabit(habitId),
        freezeRepository.totalFreezeSpending(habitId)
    ) { rawBalance, reward, allocated, freezeSpending ->
        val deductions = allocated + freezeSpending
        val rawPreview = repository.previewCompletedBalance(rawBalance, reward, deductions)
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
     * Freezes are FREE to acquire but cost 2x daily reward to USE.
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
     * This costs 2x daily reward from balance and preserves the streak.
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
     * @param didSucceed Whether the habit succeeded today
     */
    fun recordTodayCheckIn(didSucceed: Boolean) {
        recordCheckIn(repository.today(), didSucceed)
    }

    /**
     * Record a check-in for a specific date.
     * This allows retroactive logging.
     *
     * @param date The date to record the check-in for
     * @param didSucceed Whether the habit succeeded on that date
     */
    fun recordCheckIn(date: LocalDate, didSucceed: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Wrap the critical operations in NonCancellable so they complete
                // even if user presses home button immediately after tapping
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.recordCheckIn(date, didSucceed, habitId)
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
     * as either completed or missed in one operation.
     *
     * @param dates Set of dates to update
     * @param didSucceed Whether these days succeeded
     */
    fun bulkRecordCheckIns(dates: Set<LocalDate>, didSucceed: Boolean) {
        if (dates.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.bulkRecordCheckIns(dates, didSucceed, habitId)
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

    companion object {
        /**
         * [SavedStateHandle] key for the habit id, matching the `{habitId}`
         * argument on the `habit/...` navigation routes. The other per-habit
         * ViewModels reuse this key so the whole detail flow scopes to one habit.
         */
        const val HABIT_ID_KEY = "habitId"

        /**
         * Factory that builds the ViewModel from [CreationExtras].
         *
         * The [AppContainer] (the repository graph) is closed over explicitly, so
         * the graph is passed in rather than looked up through a hidden singleton.
         * The [Application] context (required by [AndroidViewModel]) comes from
         * [APPLICATION_KEY], and the per-habit [SavedStateHandle] - populated with
         * the route's `{habitId}` argument - comes from [createSavedStateHandle].
         * Scoping the `viewModel(factory = ...)` call to a `habit/{habitId}`
         * back-stack entry therefore yields a ViewModel bound to that habit.
         */
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[APPLICATION_KEY] as Application
                    MainViewModel(
                        application = application,
                        repository = container.checkInRepository,
                        habitRepository = container.habitRepository,
                        cashOutRepository = container.cashOutRepository,
                        freezeRepository = container.freezeRepository,
                        privateGate = container.privateGate,
                        savedStateHandle = createSavedStateHandle()
                    )
                }
            }
    }
}

