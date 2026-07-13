package com.oink.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [FrozenDay] entities.
 */
@Dao
interface FrozenDayDao {

    /**
     * Freeze a day. Ignores the insert if the (habit, date) pair is already
     * frozen, so freezing the same day twice is a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(frozenDay: FrozenDay): Long

    /**
     * Unfreeze a specific frozen-day record.
     */
    @Delete
    suspend fun delete(frozenDay: FrozenDay)

    /**
     * Observe the frozen days for a habit, newest first.
     */
    @Query("SELECT * FROM frozen_days WHERE habitId = :habitId ORDER BY date DESC")
    fun getFrozenDaysFlow(habitId: Long): Flow<List<FrozenDay>>

    /**
     * Get the frozen days for a habit (one-shot).
     */
    @Query("SELECT * FROM frozen_days WHERE habitId = :habitId ORDER BY date DESC")
    suspend fun getFrozenDays(habitId: Long): List<FrozenDay>

    /**
     * Get the frozen day for a habit on a specific date, or null.
     */
    @Query("SELECT * FROM frozen_days WHERE habitId = :habitId AND date = :epochDay LIMIT 1")
    suspend fun getFrozenDay(habitId: Long, epochDay: Long): FrozenDay?

    /**
     * Unfreeze a habit's day by date.
     */
    @Query("DELETE FROM frozen_days WHERE habitId = :habitId AND date = :epochDay")
    suspend fun deleteByDate(habitId: Long, epochDay: Long)
}
