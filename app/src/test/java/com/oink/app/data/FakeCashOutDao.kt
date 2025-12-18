package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake implementation of CashOutDao for testing.
 *
 * Uses in-memory storage to simulate Room database operations.
 */
class FakeCashOutDao : CashOutDao {

    private val cashOuts = MutableStateFlow<List<CashOut>>(emptyList())
    private val idCounter = AtomicLong(0L)

    override suspend fun insert(cashOut: CashOut): Long {
        val id = cashOut.id.takeIf { it != 0L } ?: idCounter.incrementAndGet()
        val newCashOut = cashOut.copy(id = id)
        cashOuts.update { current ->
            (current + newCashOut).sortedByDescending { it.cashedOutAt }
        }
        return id
    }

    override fun getAllCashOutsFlow(): Flow<List<CashOut>> {
        return cashOuts
    }

    override suspend fun getAllCashOuts(): List<CashOut> {
        return cashOuts.value
    }

    override suspend fun getTotalCashedOut(): Double {
        return cashOuts.value.sumOf { it.amount }
    }

    override fun getTotalCashedOutFlow(): Flow<Double> {
        return cashOuts.map { list -> list.sumOf { it.amount } }
    }

    override suspend fun getCashOutCount(): Int {
        return cashOuts.value.size
    }

    override suspend fun getMostRecentCashOut(): CashOut? {
        return cashOuts.value.firstOrNull()
    }

    override suspend fun update(cashOut: CashOut) {
        cashOuts.update { current ->
            current.map { if (it.id == cashOut.id) cashOut else it }
        }
    }

    override suspend fun delete(cashOut: CashOut) {
        cashOuts.update { current ->
            current.filter { it.id != cashOut.id }
        }
    }

    override suspend fun getById(id: Long): CashOut? {
        return cashOuts.value.find { it.id == id }
    }

    // ============================================================
    // Test Helpers
    // ============================================================

    /**
     * Set initial cash-outs for testing.
     */
    fun setCashOuts(newCashOuts: List<CashOut>) {
        cashOuts.value = newCashOuts.sortedByDescending { it.cashedOutAt }
    }

    /**
     * Clear all cash-outs.
     */
    fun clear() {
        cashOuts.value = emptyList()
    }
}

