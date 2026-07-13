package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake implementation of [CashOutAllocationDao] for testing.
 *
 * In-memory storage that mirrors the real Room DAO, including the unique
 * (cashOut, habit) constraint: [insert] rejects a duplicate pair and returns -1,
 * matching [androidx.room.OnConflictStrategy.ABORT] in production.
 */
class FakeCashOutAllocationDao : CashOutAllocationDao {

    private val allocations = MutableStateFlow<List<CashOutAllocation>>(emptyList())
    private val idCounter = AtomicLong(0L)

    override suspend fun insert(allocation: CashOutAllocation): Long {
        val exists = allocations.value.any {
            it.cashOutId == allocation.cashOutId && it.habitId == allocation.habitId
        }
        if (exists) return -1L

        val id = allocation.id.takeIf { it != 0L } ?: idCounter.incrementAndGet()
        allocations.update { current -> current + allocation.copy(id = id) }
        return id
    }

    override suspend fun update(allocation: CashOutAllocation) {
        allocations.update { current ->
            current.map { if (it.id == allocation.id) allocation else it }
        }
    }

    override suspend fun delete(allocation: CashOutAllocation) {
        allocations.update { current -> current.filter { it.id != allocation.id } }
    }

    override suspend fun getForCashOut(cashOutId: Long): List<CashOutAllocation> =
        allocations.value.filter { it.cashOutId == cashOutId }

    override suspend fun getForHabit(habitId: Long): List<CashOutAllocation> =
        allocations.value.filter { it.habitId == habitId }

    override suspend fun getAll(): List<CashOutAllocation> =
        allocations.value

    override fun getForHabitFlow(habitId: Long): Flow<List<CashOutAllocation>> =
        allocations.map { list -> list.filter { it.habitId == habitId } }

    override suspend fun getTotalForHabit(habitId: Long): Long =
        allocations.value.filter { it.habitId == habitId }.sumOf { it.amount }

    override fun getTotalForHabitFlow(habitId: Long): Flow<Long> =
        allocations.map { list -> list.filter { it.habitId == habitId }.sumOf { it.amount } }

    // ============================================================
    // Test Helpers
    // ============================================================

    /**
     * Seed the fake with initial allocations for testing.
     */
    fun seed(vararg items: CashOutAllocation) {
        allocations.value = items.toList()
        items.forEach { item -> idCounter.updateAndGet { existing -> maxOf(existing, item.id) } }
    }
}
