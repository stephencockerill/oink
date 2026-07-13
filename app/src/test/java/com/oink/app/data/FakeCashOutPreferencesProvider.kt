package com.oink.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fake [CashOutPreferencesProvider] for testing cash-out balance math.
 *
 * Holds freeze spending and the exercise reward in memory so CashOutRepository
 * and DefaultDeductionProvider can be exercised without a database. Mirrors the
 * production [HabitCashOutPreferencesProvider] contract, minus the per-habit
 * keying those single-habit consumers do not need.
 */
class FakeCashOutPreferencesProvider(
    private val exerciseReward: Long = PreferencesRepository.DEFAULT_EXERCISE_REWARD,
    initialFreezeSpending: Long = 0L
) : CashOutPreferencesProvider {

    private val _totalFreezeSpending = MutableStateFlow(initialFreezeSpending)

    override suspend fun getExerciseReward(): Long = exerciseReward

    override suspend fun getTotalFreezeSpending(): Long = _totalFreezeSpending.value

    override suspend fun addFreezeSpending(amount: Long) {
        _totalFreezeSpending.update { it + amount }
    }
}
