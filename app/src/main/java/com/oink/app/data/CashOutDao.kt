package com.oink.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for CashOut records.
 */
@Dao
interface CashOutDao {

    /**
     * Insert a new cash-out record.
     */
    @Insert
    suspend fun insert(cashOut: CashOut): Long

    /**
     * Update an existing cash-out record.
     */
    @Update
    suspend fun update(cashOut: CashOut)

    /**
     * Delete a cash-out record.
     */
    @Delete
    suspend fun delete(cashOut: CashOut)

    /**
     * Get a cash-out by ID.
     */
    @Query("SELECT * FROM cash_outs WHERE id = :id")
    suspend fun getById(id: Long): CashOut?

    /**
     * Get all cash-outs, most recent first.
     */
    @Query("SELECT * FROM cash_outs ORDER BY cashedOutAt DESC")
    fun getAllCashOutsFlow(): Flow<List<CashOut>>

    /**
     * Get all cash-outs (non-flow, for one-time queries).
     */
    @Query("SELECT * FROM cash_outs ORDER BY cashedOutAt DESC")
    suspend fun getAllCashOuts(): List<CashOut>

    /**
     * Get total amount cashed out all-time.
     */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM cash_outs")
    suspend fun getTotalCashedOut(): Double

    /**
     * Get total amount cashed out as a Flow.
     */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM cash_outs")
    fun getTotalCashedOutFlow(): Flow<Double>

    /**
     * Get count of cash-outs.
     */
    @Query("SELECT COUNT(*) FROM cash_outs")
    suspend fun getCashOutCount(): Int

    /**
     * Get the most recent cash-out.
     */
    @Query("SELECT * FROM cash_outs ORDER BY cashedOutAt DESC LIMIT 1")
    suspend fun getMostRecentCashOut(): CashOut?
}

