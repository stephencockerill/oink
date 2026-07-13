package com.oink.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [CashOutAllocation] entities.
 */
@Dao
interface CashOutAllocationDao {

    /**
     * Insert an allocation. Aborts on a duplicate (cashOut, habit) pair so a
     * cash-out is never double-allocated to the same habit.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(allocation: CashOutAllocation): Long

    /**
     * Update an existing allocation.
     */
    @Update
    suspend fun update(allocation: CashOutAllocation)

    /**
     * Delete an allocation.
     */
    @Delete
    suspend fun delete(allocation: CashOutAllocation)

    /**
     * Get the allocations that make up a single cash-out.
     */
    @Query("SELECT * FROM cash_out_allocations WHERE cashOutId = :cashOutId")
    suspend fun getForCashOut(cashOutId: Long): List<CashOutAllocation>

    /**
     * Get every allocation attributed to a habit (one-shot).
     */
    @Query("SELECT * FROM cash_out_allocations WHERE habitId = :habitId")
    suspend fun getForHabit(habitId: Long): List<CashOutAllocation>

    /**
     * Get every allocation across all cash-outs and habits (one-shot).
     */
    @Query("SELECT * FROM cash_out_allocations")
    suspend fun getAll(): List<CashOutAllocation>

    /**
     * Observe every allocation attributed to a habit.
     */
    @Query("SELECT * FROM cash_out_allocations WHERE habitId = :habitId")
    fun getForHabitFlow(habitId: Long): Flow<List<CashOutAllocation>>

    /**
     * Total cents attributed to a habit across all cash-outs.
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_out_allocations WHERE habitId = :habitId")
    suspend fun getTotalForHabit(habitId: Long): Long

    /**
     * Observe the total cents attributed to a habit across all cash-outs.
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_out_allocations WHERE habitId = :habitId")
    fun getTotalForHabitFlow(habitId: Long): Flow<Long>
}
