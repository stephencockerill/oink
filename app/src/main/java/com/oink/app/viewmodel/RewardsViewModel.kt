package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oink.app.data.CashOut
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.utils.BalanceCalculator
import com.oink.app.widget.OinkWidget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * IMPORTANT: Balance calculation follows the same pattern as MainViewModel:
 *   Actual Balance = Check-in balance - Total cashed out - Freeze spending
 *
 * We also track "Total Earned" which is the raw check-in balance (total
 * accumulated through exercise before any spending).
 */
class RewardsViewModel(
    application: Application,
    private val cashOutRepository: CashOutRepository,
    private val checkInRepository: CheckInRepository,
    private val preferencesRepository: PreferencesRepository
) : AndroidViewModel(application) {

    /**
     * Current ACTUAL balance (after all deductions).
     * This is what the user can actually spend.
     */
    val currentBalance: StateFlow<Double> = combine(
        checkInRepository.currentBalance,
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
     * Total earned through exercise (raw check-in balance).
     * This is the total accumulated before any spending.
     * Used to show "Total Lifetime Earned" on the rewards screen.
     */
    val totalEarned: StateFlow<Double> = checkInRepository.currentBalance
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    /**
     * All cash-outs (reward history).
     */
    val allCashOuts: StateFlow<List<CashOut>> = cashOutRepository.allCashOuts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Total amount cashed out all-time.
     */
    val totalCashedOut: StateFlow<Double> = cashOutRepository.totalCashedOut
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
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
        _totalWorkouts.value = checkInRepository.getTotalWorkoutCount()
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
    fun cashOut(name: String, amount: Double, emoji: String = "🎁") {
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
                    val cashOut = cashOutRepository.cashOut(name, amount, emoji)
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
    fun updateSelectedCashOut(name: String, amount: Double, emoji: String) {
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
                    if (cashOutRepository.updateCashOut(updated)) {
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

    /**
     * Factory for creating RewardsViewModel.
     */
    class Factory(
        private val application: Application,
        private val cashOutRepository: CashOutRepository,
        private val checkInRepository: CheckInRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RewardsViewModel::class.java)) {
                return RewardsViewModel(application, cashOutRepository, checkInRepository, preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

