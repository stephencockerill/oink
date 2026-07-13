package com.oink.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fake [CashOutPreferencesProvider] for testing cash-out balance math.
 *
 * Holds freeze spending in memory so [CashOutRepository] can be exercised
 * without a database. Mirrors the production [HabitCashOutPreferencesProvider]
 * contract, minus the per-habit keying that single-habit consumer does not need.
 */
class FakeCashOutPreferencesProvider(
    initialFreezeSpending: Long = 0L
) : CashOutPreferencesProvider {

    private val _totalFreezeSpending = MutableStateFlow(initialFreezeSpending)

    override suspend fun getTotalFreezeSpending(): Long = _totalFreezeSpending.value

    override suspend fun addFreezeSpending(amount: Long) {
        _totalFreezeSpending.update { it + amount }
    }
}
