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
import com.oink.app.data.HabitRepository
import com.oink.app.data.PrivateGate
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * No lifetime "earned - cashed out = balance" figure is surfaced here: with
 * gating a hidden private-funded claim would break the reconciliation and betray
 * that hidden claims exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RewardsViewModel(
    application: Application,
    private val cashOutRepository: CashOutRepository,
    private val checkInRepository: CheckInRepository,
    private val freezeRepository: FreezeRepository,
    private val privateGate: PrivateGate
) : AndroidViewModel(application) {

    /**
     * The habit this screen operates on. The app is single-habit for now, so
     * freeze spending is read from the seeded default habit.
     */
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID

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
     * Total workout count (all exercise days logged).
     */
    private val _totalWorkouts = MutableStateFlow(0)
    val totalWorkouts: StateFlow<Int> = _totalWorkouts.asStateFlow()

    init {
        // Load total workout count on init
        viewModelScope.launch {
            refreshStats()
        }
    }

    /**
     * Refresh stats that need manual loading.
     */
    private suspend fun refreshStats() {
        _totalWorkouts.value = checkInRepository.getTotalWorkoutCount(habitId)
    }

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
            _error.value = "Not enough in the piggy bank! Keep working out to earn more 💪"
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
                        freezeRepository = container.freezeRepository,
                        privateGate = container.privateGate
                    )
                }
            }
    }
}

