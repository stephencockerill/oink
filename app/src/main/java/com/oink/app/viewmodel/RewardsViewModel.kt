package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oink.app.AppContainer
import com.oink.app.data.CashOut
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.PinHasher
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import com.oink.app.utils.HeroSignals
import com.oink.app.utils.Mascot
import com.oink.app.utils.MascotState
import com.oink.app.utils.Milestone
import com.oink.app.utils.MilestoneTier
import com.oink.app.utils.RewardTimeline
import com.oink.app.utils.RewardTimelineEntry
import com.oink.app.widget.OinkWidget
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether the discreet private-funds unlock affordance shows on the Rewards
 * screen, and in what mode.
 *
 * Crucially, this is derived purely from the PIN configuration
 * ([PreferencesRepository.hasPin]) and the shared [PrivateGate] unlock flag,
 * never from whether any private habits or funds exist. That mirrors the private
 * area's plausible-deniability guarantee: the control's presence leaks only that
 * a PIN was set, exactly what the visible Private area already implies.
 */
sealed interface PrivateFundsAccess {
    /** Resolving the PIN configuration; show no control. */
    data object Loading : PrivateFundsAccess

    /** No PIN configured; show no unlock control at all. */
    data object NoPin : PrivateFundsAccess

    /** A PIN is set and the area is locked; offer the discreet unlock. */
    data object Locked : PrivateFundsAccess

    /** Unlocked; private funds are folded into the pot and history. */
    data object Unlocked : PrivateFundsAccess
}

/**
 * Rate-limit and error state for the private-funds PIN prompt, snapshotted at
 * emission time. Mirrors the private area's locked-state fields so the two
 * prompts behave identically.
 *
 * @param isLockedOut whether the rate limiter is currently blocking attempts
 * @param remainingLockoutSeconds seconds left on the lockout (0 when not locked out)
 * @param remainingAttempts wrong PINs left before the next lockout
 * @param showError whether the last attempt was wrong
 */
data class PinPromptState(
    val isLockedOut: Boolean = false,
    val remainingLockoutSeconds: Int = 0,
    val remainingAttempts: Int = PrivateGate.MAX_ATTEMPTS,
    val showError: Boolean = false
)

/**
 * ViewModel for the Rewards screen.
 *
 * Handles cash-out operations and displays reward history.
 * The key psychological framing here is CELEBRATION - cashing out
 * should feel like a victory, not a loss!
 *
 * Balance and reward history follow the private-area unlock state: while locked
 * the pot and history span public habits only, and a cash-out that drew from a
 * private habit is hidden. While unlocked the pot spans public and private
 * habits and the full history shows. See [CashOutRepository].
 *
 * When a PIN is set but the area is locked, [privateFundsAccess] offers a
 * discreet unlock: entering the correct PIN flips the shared [PrivateGate], so
 * the pot grows to include private banks and the full history appears. The
 * existing background re-lock behaviour still applies. PBKDF2 verification is
 * CPU-bound, so it runs on [defaultDispatcher] rather than the main thread.
 *
 * No lifetime "earned - cashed out = balance" figure is surfaced here: with
 * gating a hidden private-funded claim would break the reconciliation and betray
 * that hidden claims exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewardsViewModel(
    application: Application,
    private val cashOutRepository: CashOutRepository,
    private val checkInRepository: CheckInRepository,
    private val habitRepository: HabitRepository,
    private val freezeRepository: FreezeRepository,
    private val privateGate: PrivateGate,
    private val preferencesRepository: PreferencesRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    /**
     * Current spendable balance: the shared pot. While locked it spans public
     * habits only; while unlocked it also spans private habits, so a claim can
     * draw from private banks. See [CashOutRepository.pot].
     */
    val currentBalance: StateFlow<Long> = privateGate.isUnlocked
        .flatMapLatest { unlocked -> cashOutRepository.pot(includePrivate = unlocked) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * Reward history. While locked this is the visible (gated) set - cash-outs
     * that touched a private habit are hidden; while unlocked it is the full set.
     */
    val allCashOuts: StateFlow<List<CashOut>> = privateGate.isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked) cashOutRepository.allCashOuts else cashOutRepository.visibleCashOuts
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Total amount cashed out, matching the visibility of [allCashOuts]: the
     * gated total while locked, the full total while unlocked.
     */
    val totalCashedOut: StateFlow<Long> = privateGate.isUnlocked
        .flatMapLatest { unlocked ->
            if (unlocked) cashOutRepository.totalCashedOut else cashOutRepository.visibleTotalCashedOut
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * Whether the most recent private-funds PIN attempt failed. Transient UI
     * signal, so it is a plain MutableStateFlow rather than persisted or derived.
     */
    private val _lastAttemptFailed = MutableStateFlow(false)

    /**
     * Drives the discreet private-funds unlock control. Keyed only on whether a
     * PIN exists and the unlock flag, so it reveals nothing about private
     * contents (see [PrivateFundsAccess]).
     */
    val privateFundsAccess: StateFlow<PrivateFundsAccess> = combine(
        preferencesRepository.hasPin,
        privateGate.isUnlocked
    ) { hasPin, unlocked ->
        when {
            unlocked -> PrivateFundsAccess.Unlocked
            hasPin -> PrivateFundsAccess.Locked
            else -> PrivateFundsAccess.NoPin
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrivateFundsAccess.Loading
    )

    /**
     * Rate-limit and error state for the PIN prompt. Re-emits on every attempt;
     * the live "still locked out?" answer is read from [PrivateGate]'s injected
     * clock at emission time.
     */
    val pinPrompt: StateFlow<PinPromptState> = combine(
        privateGate.attemptState,
        _lastAttemptFailed
    ) { _, attemptFailed ->
        buildPinPrompt(attemptFailed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PinPromptState()
    )

    /**
     * Total completed-day count summed across the same in-scope habit set the
     * pot spans, so the stat and the balance always agree on which habits count.
     * While locked it counts public habits only; while unlocked it also counts
     * private habits. With no habits in scope it is 0. See [CashOutRepository.pot].
     */
    val totalCompletedDays: StateFlow<Int> = privateGate.isUnlocked
        .flatMapLatest { unlocked -> completedDaysAcrossPot(includePrivate = unlocked) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Completed-day count across the in-scope habit set. Rebuilds whenever the
     * set of habits changes ([HabitRepository.allHabits]), then combines each
     * in-scope habit's completed-day flow. Combining over an empty iterable never
     * emits, so an empty in-scope set is short-circuited to a flow of 0.
     */
    private fun completedDaysAcrossPot(includePrivate: Boolean): Flow<Int> =
        habitRepository.allHabits.flatMapLatest { habits ->
            val inScope = if (includePrivate) habits else habits.filter { !it.isPrivate }
            if (inScope.isEmpty()) {
                flowOf(0)
            } else {
                combine(inScope.map { habit -> checkInRepository.completedDayCount(habit.id) }) { counts ->
                    counts.sum()
                }
            }
        }

    /**
     * The compact hero state for the Rewards bank card.
     *
     * Balance is the gated spendable pot ([currentBalance]); gain, streak, and the
     * mascot's mood are aggregated across the same in-scope habit set the pot spans
     * (public only while locked, public + private while unlocked), so the hero and
     * the balance always agree on which habits count. Milestone progress derives
     * from the balance. All precomputed here so the composable does no work.
     *
     * The Rewards hero shows a "Treat yourself" CTA instead of the home hero's
     * milestone bar, since the Rewards screen has a dedicated milestone track below.
     */
    val heroState: StateFlow<HeroBankState> = combine(
        currentBalance,
        heroSignals()
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
            subtitle = ""
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
            subtitle = ""
        )
    )

    /**
     * The milestone track: all four financial tiers, each tagged done / active /
     * locked, derived from cumulative lifetime earnings.
     *
     * Lifetime earnings = spendable balance + everything already cashed out, both
     * following the same unlock gating, so a trophy stays earned after the user
     * spends the money and locking the private area never revokes a publicly-earned
     * tier. See [Milestone.track].
     */
    val milestoneTrack: StateFlow<List<MilestoneTier>> = combine(
        currentBalance,
        totalCashedOut
    ) { balance, cashedOut ->
        Milestone.track(balance + cashedOut)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Milestone.track(0L)
    )

    /**
     * The highlight-reel timeline: earned milestone trophies interleaved with the
     * visible cash-out history. Derived purely by [RewardTimeline.build]; both
     * inputs already follow unlock gating.
     */
    val timeline: StateFlow<List<RewardTimelineEntry>> = combine(
        milestoneTrack,
        allCashOuts
    ) { track, cashOuts ->
        RewardTimeline.build(track, cashOuts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Hero signals aggregated across the in-scope habit set, gated by the unlock
     * flag so they match [currentBalance]'s scope. Rebuilds when the unlock flag or
     * the habit set changes, then folds each in-scope habit's signal. Combining
     * over an empty iterable never emits, so an empty in-scope set is
     * short-circuited to a resting signal rather than hanging.
     */
    private fun heroSignals(): Flow<HeroAggregate> = privateGate.isUnlocked
        .flatMapLatest { unlocked ->
            habitRepository.allHabits.flatMapLatest { habits ->
                val inScope = if (unlocked) habits else habits.filter { !it.isPrivate }
                if (inScope.isEmpty()) {
                    flowOf(HeroAggregate(streak = 0, dailyGainCents = 0L, lastCheckIn = null, balanceDelta = 0L))
                } else {
                    combine(inScope.map { habit -> habitSignalFlow(habit) }) { signals ->
                        aggregate(signals.toList())
                    }
                }
            }
        }

    /**
     * A single habit's hero signals: its streak, the reward it banked today (if
     * any), and the date and balance delta of its most recent check-in.
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
     * Fold per-habit signals into the one aggregate the hero renders: hottest
     * streak, summed gains, and the mascot delta from habits that acted on the most
     * recent day - a fresh loss on any of them wins over another's gain, matching
     * [Mascot]'s "a streak can't paper over a loss" precedence.
     */
    private fun aggregate(signals: List<HabitSignal>): HeroAggregate {
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

    /**
     * One in-scope habit's contribution to the hero, before aggregation.
     */
    private data class HabitSignal(
        val streak: Int,
        val gainCents: Long,
        val lastCheckIn: LocalDate?,
        val balanceDelta: Long
    )

    /**
     * The in-scope habit set folded into a single hero signal.
     */
    private data class HeroAggregate(
        val streak: Int,
        val dailyGainCents: Long,
        val lastCheckIn: LocalDate?,
        val balanceDelta: Long
    )

    /**
     * Loading state.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Error state.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Success state for celebrations!
     * Contains the CashOut record when a cash-out is successful.
     */
    private val _cashOutSuccess = MutableStateFlow<CashOut?>(null)
    val cashOutSuccess: StateFlow<CashOut?> = _cashOutSuccess.asStateFlow()

    /**
     * Currently selected reward for editing/viewing details.
     */
    private val _selectedCashOut = MutableStateFlow<CashOut?>(null)
    val selectedCashOut: StateFlow<CashOut?> = _selectedCashOut.asStateFlow()

    /**
     * State for showing delete confirmation dialog.
     */
    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    /**
     * Cash out from the piggy bank! 🎉
     *
     * This is the REWARD moment. Make it feel special!
     *
     * @param name What you're treating yourself to
     * @param amount How much to cash out
     * @param emoji Emoji for your reward
     */
    fun cashOut(name: String, amount: Long, emoji: String = "🎁") {
        if (name.isBlank()) {
            _error.value = "Give your reward a name! What are you treating yourself to?"
            return
        }
        if (amount <= 0) {
            _error.value = "Amount must be greater than $0"
            return
        }
        if (amount > currentBalance.value) {
            _error.value = "Not enough in the piggy bank! Keep showing up to earn more 💪"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                withContext(NonCancellable) {
                    // Empty scope draws the claim from the whole pot; unlock state
                    // decides whether private habits are in it.
                    val cashOut = cashOutRepository.cashOut(
                        name = name,
                        amount = amount,
                        emoji = emoji,
                        scope = emptySet(),
                        includePrivate = privateGate.isUnlocked.value
                    )
                    if (cashOut != null) {
                        _cashOutSuccess.value = cashOut
                        // Update widget to reflect new balance
                        updateWidget()
                    } else {
                        _error.value = "Failed to cash out. Please try again."
                    }
                }
            } catch (e: Exception) {
                _error.value = "Something went wrong: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear the success state (after showing celebration).
     */
    fun clearSuccess() {
        _cashOutSuccess.value = null
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    // ============================================================
    // Private-funds unlock
    // ============================================================

    /**
     * Verify a private-funds PIN attempt. Rejected outright while rate-limited.
     * On a correct PIN the limiter resets and the shared gate unlocks - so the
     * pot and history flows grow to include private banks - and on a wrong one
     * the failure is recorded (advancing the limiter) and surfaced as an error.
     *
     * Mirrors [PrivateViewModel.submitPin], including running the CPU-bound
     * PBKDF2 verify on [defaultDispatcher].
     */
    fun submitPin(pin: String) {
        if (privateGate.isLockedOut()) return
        viewModelScope.launch {
            val stored = preferencesRepository.getHashedPin() ?: return@launch
            val matches = withContext(defaultDispatcher) { PinHasher.verify(pin, stored) }
            if (matches) {
                _lastAttemptFailed.value = false
                privateGate.recordSuccess()
                privateGate.unlock()
            } else {
                privateGate.recordFailure()
                _lastAttemptFailed.value = true
            }
        }
    }

    /**
     * Clear the PIN error flag, so dismissing and reopening the prompt starts
     * clean rather than showing a stale "incorrect PIN".
     */
    fun clearPinError() {
        _lastAttemptFailed.value = false
    }

    /**
     * Snapshot the lockout window at emission time. While locked out, no error
     * text is shown (the countdown carries the message instead).
     */
    private fun buildPinPrompt(attemptFailed: Boolean): PinPromptState {
        val lockedOut = privateGate.isLockedOut()
        val remainingMillis = privateGate.remainingLockoutMillis()
        return PinPromptState(
            isLockedOut = lockedOut,
            remainingLockoutSeconds = ((remainingMillis + 999) / 1000).toInt(),
            remainingAttempts = privateGate.remainingAttempts(),
            showError = attemptFailed && !lockedOut
        )
    }

    // ============================================================
    // Edit/Delete Operations
    // ============================================================

    /**
     * Select a cash-out for editing or viewing details.
     */
    fun selectCashOut(cashOut: CashOut) {
        _selectedCashOut.value = cashOut
    }

    /**
     * Clear the selected cash-out.
     */
    fun clearSelection() {
        _selectedCashOut.value = null
        _showDeleteConfirmation.value = false
    }

    /**
     * Show delete confirmation dialog for the selected cash-out.
     */
    fun requestDelete() {
        if (_selectedCashOut.value != null) {
            _showDeleteConfirmation.value = true
        }
    }

    /**
     * Cancel delete confirmation.
     */
    fun cancelDelete() {
        _showDeleteConfirmation.value = false
    }

    /**
     * Update a cash-out's details.
     *
     * When the amount changes, the balance updates automatically:
     * - Increase amount → balance decreases
     * - Decrease amount → balance increases
     *
     * @param name New name
     * @param amount New amount
     * @param emoji New emoji
     */
    fun updateSelectedCashOut(name: String, amount: Long, emoji: String) {
        val selected = _selectedCashOut.value ?: return

        if (name.isBlank()) {
            _error.value = "Give your reward a name!"
            return
        }
        if (amount <= 0) {
            _error.value = "Amount must be greater than $0"
            return
        }

        // Calculate what the new balance would be
        // Old amount was deducted, new amount will be deducted
        val amountDifference = amount - selected.amount
        val newBalance = currentBalance.value - amountDifference

        if (newBalance < 0) {
            _error.value = "Can't increase reward to more than available balance!"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                withContext(NonCancellable) {
                    val updated = selected.copy(
                        name = name,
                        amount = amount,
                        emoji = emoji
                    )
                    // Amount edits re-split over the pot; unlock state decides
                    // whether private habits are in it (mirrors cashOut).
                    if (cashOutRepository.updateCashOut(
                            updated,
                            includePrivate = privateGate.isUnlocked.value
                        )
                    ) {
                        _selectedCashOut.value = null
                        updateWidget()
                    } else {
                        _error.value = "Failed to update reward"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Something went wrong: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Delete the selected cash-out.
     *
     * This gives the money "back" to the piggy bank - balance goes UP!
     * Framing: "Reward unclaimed" or "Changed your mind"
     */
    fun deleteSelectedCashOut() {
        val selected = _selectedCashOut.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                withContext(NonCancellable) {
                    if (cashOutRepository.deleteCashOut(selected)) {
                        _selectedCashOut.value = null
                        _showDeleteConfirmation.value = false
                        updateWidget()
                    } else {
                        _error.value = "Failed to delete reward"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Something went wrong: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update the home screen widget.
     */
    private suspend fun updateWidget() {
        try {
            OinkWidget.updateAllWidgets(getApplication())
        } catch (e: Exception) {
            // Widget update failure shouldn't crash the app
        }
    }

    companion object {
        private const val HERO_LABEL = "Piggy Bank"

        /**
         * Factory that builds the ViewModel from [CreationExtras]. Rewards are
         * pot-level (a cash-out draws from every public habit), so this ViewModel
         * takes no habit id: the [AppContainer] is closed over explicitly and the
         * [Application] comes from [APPLICATION_KEY].
         */
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[APPLICATION_KEY] as Application
                    RewardsViewModel(
                        application = application,
                        cashOutRepository = container.cashOutRepository,
                        checkInRepository = container.checkInRepository,
                        habitRepository = container.habitRepository,
                        freezeRepository = container.freezeRepository,
                        privateGate = container.privateGate,
                        preferencesRepository = container.preferencesRepository
                    )
                }
            }
    }
}

