package com.oink.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for CheckIn entities.
 *
 * Why Flow? Because we want the UI to react automatically when
 * the database changes. Room + Flow + Compose = reactive goodness.
 */
@Dao
interface CheckInDao {

    /**
     * Insert a new check-in.
     * Returns the row ID of the newly inserted check-in.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckIn): Long

    /**
     * Update an existing check-in.
     * Used when user wants to change their answer for a past date.
     */
    @Update
    suspend fun update(checkIn: CheckIn)

    /**
     * Get all check-ins ordered by date descending (newest first).
     * This is what powers the history screen.
     */
    @Query("SELECT * FROM check_ins ORDER BY date DESC")
    fun getAllCheckInsFlow(): Flow<List<CheckIn>>

    /**
     * Get all check-ins ordered by date ascending (oldest first).
     * Useful for calculating streaks and recalculating balances.
     */
    @Query("SELECT * FROM check_ins ORDER BY date ASC")
    suspend fun getAllCheckInsAsc(): List<CheckIn>

    /**
     * Get a specific check-in by date.
     * Returns null if no check-in exists for that date.
     */
    @Query("SELECT * FROM check_ins WHERE date = :epochDay LIMIT 1")
    suspend fun getCheckInForDate(epochDay: Long): CheckIn?

    /**
     * Get the most recent check-in.
     * Used to determine the current balance.
     */
    @Query("SELECT * FROM check_ins ORDER BY date DESC LIMIT 1")
    suspend fun getLatestCheckIn(): CheckIn?

    /**
     * Get the most recent check-in as a Flow.
     * UI observes this to update the balance display.
     */
    @Query("SELECT * FROM check_ins ORDER BY date DESC LIMIT 1")
    fun getLatestCheckInFlow(): Flow<CheckIn?>

    /**
     * Get the check-in for today.
     * Used to determine if user has already checked in today.
     */
    @Query("SELECT * FROM check_ins WHERE date = :todayEpochDay LIMIT 1")
    fun getTodayCheckInFlow(todayEpochDay: Long): Flow<CheckIn?>

    /**
     * Delete all check-ins.
     * Nuclear option - use carefully!
     */
    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()

    /**
     * Get total count of workout days (days where user exercised).
     * Used for stats and calculating workout counts.
     */
    @Query("SELECT COUNT(*) FROM check_ins WHERE didExercise = 1")
    suspend fun getTotalWorkoutCount(): Int

    /**
     * Get the check-in immediately before a given date.
     * Efficient single query instead of loading all check-ins and filtering.
     */
    @Query("SELECT * FROM check_ins WHERE date < :epochDay ORDER BY date DESC LIMIT 1")
    suspend fun getCheckInBefore(epochDay: Long): CheckIn?
}

