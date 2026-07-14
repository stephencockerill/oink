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
import com.oink.app.data.PinHasher
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import com.oink.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The private area's gate state, as a closed set of modes.
 *
 * Crucially, [NeedsPinSetup] and [Locked] carry nothing about whether any
 * private habits exist or what they are: both are computed purely from the PIN
 * configuration and the unlock flag, never from habit contents. Only [Unlocked]
 * exposes habit data. That is the plausible-deniability guarantee - a locked
 * screen looks identical whether the user hides ten habits or none.
 */
sealed interface PrivateUiState {
    /** Resolving the PIN configuration; shows a neutral placeholder. */
    data object Loading : PrivateUiState

    /** No PIN configured yet; offer to create one. */
    data object NeedsPinSetup : PrivateUiState

    /**
     * A PIN is set and the area is locked.
     *
     * @param isLockedOut whether the rate limiter is currently blocking attempts
     * @param remainingLockoutSeconds seconds left on the lockout (0 when not locked out)
     * @param remainingAttempts wrong PINs left before the next lockout
     * @param showError whether the last attempt was wrong
     */
    data class Locked(
        val isLockedOut: Boolean = false,
        val remainingLockoutSeconds: Int = 0,
        val remainingAttempts: Int = PrivateGate.MAX_ATTEMPTS,
        val showError: Boolean = false
    ) : PrivateUiState

    /** Unlocked; shows the private habits (possibly empty). */
    data class Unlocked(val habits: List<HabitCardState>) : PrivateUiState
}

/**
 * ViewModel for the private area behind the PIN gate.
 *
 * The single [uiState] is derived reactively from the PIN configuration
 * ([PreferencesRepository.hasPin]), the shared [PrivateGate] unlock flag and
 * rate limiter, and - only while unlocked - the private habits. A background
 * re-lock (see [com.oink.app.OinkApplication]) flips [PrivateGate.isUnlocked],
 * which flows straight through here so the UI drops back to [PrivateUiState.Locked]
 * live.
 *
 * PBKDF2 hashing is CPU-bound, so it runs on [defaultDispatcher] rather than the
 * main thread; the dispatcher is injected so tests drive it deterministically.
 *
 * A plain [ViewModel]: it needs no Application context.
 */
class PrivateViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val privateGate: PrivateGate,
    private val habitRepository: HabitRepository,
    private val checkInRepository: CheckInRepository,
    private val freezeRepository: FreezeRepository,
    private val cashOutRepository: CashOutRepository,
    private val widgetUpdater: WidgetUpdater = WidgetUpdater.NoOp,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    /**
     * Whether the most recent PIN attempt failed. Transient UI signal, so it is
     * a plain MutableStateFlow rather than persisted or derived state.
     */
    private val _lastAttemptFailed = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PrivateUiState> = combine(
        preferencesRepository.hasPin,
        privateGate.isUnlocked,
        privateGate.attemptState,
        _lastAttemptFailed
    ) { hasPin, unlocked, _, attemptFailed ->
        GateInputs(hasPin = hasPin, unlocked = unlocked, attemptFailed = attemptFailed)
    }.flatMapLatest { inputs ->
        when {
            !inputs.hasPin -> flowOf(PrivateUiState.NeedsPinSetup)
            !inputs.unlocked -> flowOf(buildLockedState(inputs.attemptFailed))
            else -> privateCardsFlow().map { PrivateUiState.Unlocked(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrivateUiState.Loading
    )

    /**
     * Create the first PIN and unlock. A no-op for a PIN outside 4-6 digits
     * (the UI gates the button with the same rule).
     */
    fun createPin(pin: String) {
        if (!isValidPin(pin)) return
        viewModelScope.launch {
            val hashed = withContext(defaultDispatcher) { PinHasher.hash(pin) }
            preferencesRepository.setPin(hashed)
            privateGate.recordSuccess()
            privateGate.unlock()
        }
    }

    /**
     * Verify a PIN attempt. Rejected outright while rate-limited. On a correct
     * PIN the limiter resets and the area unlocks; on a wrong one the failure is
     * recorded (advancing the limiter) and surfaced as an error.
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
     * Snapshot the lockout window at emission time. While locked out, no error
     * text is shown (the countdown carries the message instead).
     */
    private fun buildLockedState(attemptFailed: Boolean): PrivateUiState.Locked {
        val lockedOut = privateGate.isLockedOut()
        val remainingMillis = privateGate.remainingLockoutMillis()
        return PrivateUiState.Locked(
            isLockedOut = lockedOut,
            remainingLockoutSeconds = ((remainingMillis + 999) / 1000).toInt(),
            remainingAttempts = privateGate.remainingAttempts(),
            showError = attemptFailed && !lockedOut
        )
    }

    /**
     * The private habits as card states, built exactly like the home list but
     * filtered to [Habit.isPrivate]. Subscribed only in the unlocked branch, so
     * no private habit data is read while the area is locked.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun privateCardsFlow(): Flow<List<HabitCardState>> = habitRepository.allHabits
        .flatMapLatest { habits ->
            val private = habits.filter { it.isPrivate }
            if (private.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(private.map { habit -> cardStateFlow(habit) }) { cards -> cards.toList() }
            }
        }

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
     * the same [CheckInRepository.allCheckIns] flow the streak uses (not
     * [CheckInRepository.getTodayCheckIn]) so the card's status and streak stay
     * consistent and no perpetual until-midnight timer is subscribed per card.
     * See [com.oink.app.viewmodel.HabitListViewModel] for the rationale.
     */
    private fun todayCompletedFlow(habitId: Long): Flow<Boolean?> =
        checkInRepository.allCheckIns(habitId).map { checkIns ->
            val today = checkInRepository.today()
            checkIns.find { it.date == today }?.didSucceed
        }

    /**
     * Log (or change) today's check-in for a private habit from its card.
     *
     * Behaves exactly like the home list's action (see
     * [com.oink.app.viewmodel.HabitListViewModel.recordCheckIn]); it is only ever
     * invoked from the unlocked private list. Private-habit widgets never render
     * (the widget loader returns null for private habits), but the refresh is
     * harmless and keeps the two surfaces' behaviour uniform.
     */
    fun recordCheckIn(habitId: Long, didSucceed: Boolean) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed, habitId)
                widgetUpdater.update()
            }
        }
    }

    private fun streakFlow(habitId: Long): Flow<Int> = combine(
        checkInRepository.allCheckIns(habitId),
        freezeRepository.frozenDates(habitId)
    ) { checkIns, frozen ->
        checkInRepository.calculateStreak(checkIns, frozen)
    }

    /** Inputs the gate mode is derived from; the attempt state only signals re-emission. */
    private data class GateInputs(
        val hasPin: Boolean,
        val unlocked: Boolean,
        val attemptFailed: Boolean
    )

    companion object {
        /** Allowed PIN length range. */
        val PIN_LENGTH = 4..6

        /** Whether [pin] is a well-formed PIN: [PIN_LENGTH] digits, nothing else. */
        fun isValidPin(pin: String): Boolean =
            pin.length in PIN_LENGTH && pin.all { it.isDigit() }

        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PrivateViewModel(
                        preferencesRepository = container.preferencesRepository,
                        privateGate = container.privateGate,
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
