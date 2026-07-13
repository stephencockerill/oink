package com.oink.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Habit] entities.
 */
@Dao
interface HabitDao {

    /**
     * Insert a new habit. Returns the row ID of the inserted habit.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(habit: Habit): Long

    /**
     * Update an existing habit.
     */
    @Update
    suspend fun update(habit: Habit)

    /**
     * Delete a habit. Cascades to its check-ins, frozen days, and cash-out
     * allocations.
     */
    @Delete
    suspend fun delete(habit: Habit)

    /**
     * Get a habit by ID, or null if none exists.
     */
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    /**
     * Overwrite the per-habit settings copied from legacy DataStore preferences
     * onto a single habit. Writes absolute values (not increments), so re-running
     * the one-time preferences migration cannot double [totalFreezeSpending].
     * Leaves name/emoji/isPrivate/sortOrder/createdAt untouched. Returns the
     * number of rows updated (0 when no habit with [id] exists).
     */
    @Query(
        "UPDATE habits SET rewardValue = :rewardValue, " +
            "availableFreezes = :availableFreezes, " +
            "totalFreezeSpending = :totalFreezeSpending WHERE id = :id"
    )
    suspend fun applyMigratedPreferences(
        id: Long,
        rewardValue: Long,
        availableFreezes: Int,
        totalFreezeSpending: Long
    ): Int

    /**
     * Observe all habits in display order (sortOrder, then id).
     */
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    fun getAllHabitsFlow(): Flow<List<Habit>>

    /**
     * Get all habits in display order (one-shot).
     */
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllHabits(): List<Habit>

    /**
     * Observe a single habit by ID.
     */
    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitFlow(id: Long): Flow<Habit?>
}
