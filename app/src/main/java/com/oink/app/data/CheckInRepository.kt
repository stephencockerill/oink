package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Repository for managing check-in data.
 *
 * This is the single source of truth for all check-in operations.
 * The ViewModel talks to this, NOT directly to the DAO.
 *
 * Why the abstraction? Because:
 * 1. It keeps business logic out of the ViewModel
 * 2. Makes testing easier (you can mock this)
 * 3. If we ever switch from Room to something else, only this changes
 */
class CheckInRepository(
    private val checkInDao: CheckInDao,
    private val preferencesRepository: PreferencesRepository
) {

    companion object {
        const val DEFAULT_EXERCISE_REWARD = 5.00
        const val STARTING_BALANCE = 0.00
    }

    /**
     * Get the current exercise reward from preferences.
     */
    suspend fun getExerciseReward(): Double {
        return preferencesRepository.getExerciseReward()
    }

    /**
     * Flow of all check-ins, newest first.
     */
    val allCheckIns: Flow<List<CheckIn>> = checkInDao.getAllCheckInsFlow()

    /**
     * Flow of the latest check-in.
     */
    val latestCheckIn: Flow<CheckIn?> = checkInDao.getLatestCheckInFlow()

    /**
     * Flow of the current balance.
     * Derived from the latest check-in's balanceAfter field.
     */
    val currentBalance: Flow<Double> = latestCheckIn.map { checkIn ->
        checkIn?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Flow of today's check-in status.
     */
    fun getTodayCheckIn(): Flow<CheckIn?> {
        return checkInDao.getTodayCheckInFlow(LocalDate.now().toEpochDay())
    }

    /**
     * Get check-in for a specific date.
     */
    suspend fun getCheckInForDate(date: LocalDate): CheckIn? {
        return checkInDao.getCheckInForDate(date.toEpochDay())
    }

    /**
     * Record a check-in for a given date.
     *
     * This handles all the business logic:
     * - Calculating the new balance
     * - Preventing duplicate check-ins (by updating existing)
     * - Recalculating subsequent balances if updating a past date
     *
     * @param date The date to record the check-in for
     * @param didExercise Whether the user exercised
     * @return The recorded check-in
     */
    suspend fun recordCheckIn(date: LocalDate, didExercise: Boolean): CheckIn {
        val existingCheckIn = getCheckInForDate(date)

        if (existingCheckIn != null) {
            // Update existing check-in
            return updateCheckIn(existingCheckIn, didExercise)
        }

        // New check-in
        val exerciseReward = getExerciseReward()
        val previousBalance = getPreviousBalance(date)
        val newBalance = calculateNewBalance(previousBalance, didExercise, exerciseReward)

        val checkIn = CheckIn(
            date = date,
            didExercise = didExercise,
            balanceAfter = newBalance
        )

        val id = checkInDao.insert(checkIn)

        // If this is a past date, we need to recalculate all subsequent balances
        if (date < LocalDate.now()) {
            recalculateBalancesAfter(date)
        }

        return checkIn.copy(id = id)
    }

    /**
     * Update an existing check-in.
     * This will also recalculate all subsequent balances.
     */
    private suspend fun updateCheckIn(existing: CheckIn, didExercise: Boolean): CheckIn {
        if (existing.didExercise == didExercise) {
            // No change needed
            return existing
        }

        // Find the balance BEFORE this check-in
        val exerciseReward = getExerciseReward()
        val previousBalance = getPreviousBalance(existing.date)
        val newBalance = calculateNewBalance(previousBalance, didExercise, exerciseReward)

        val updated = existing.copy(
            didExercise = didExercise,
            balanceAfter = newBalance
        )

        checkInDao.update(updated)

        // Recalculate all subsequent balances
        recalculateBalancesAfter(existing.date)

        return updated
    }

    /**
     * Get the balance before a given date.
     * If no check-ins exist before this date, returns the starting balance.
     *
     * Uses a targeted DAO query instead of loading all check-ins.
     */
    private suspend fun getPreviousBalance(date: LocalDate): Double {
        val previousCheckIn = checkInDao.getCheckInBefore(date.toEpochDay())
        return previousCheckIn?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Calculate the new balance based on the rules:
     * - Exercise: + exercise reward (configurable, default $5.00)
     * - Miss: balance / 2 (rounded to 2 decimal places)
     */
    private fun calculateNewBalance(
        previousBalance: Double,
        didExercise: Boolean,
        exerciseReward: Double
    ): Double {
        return if (didExercise) {
            previousBalance + exerciseReward
        } else {
            // Round to 2 decimal places
            (previousBalance / 2.0 * 100).roundToLong() / 100.0
        }
    }

    /**
     * Recalculate all balances after a given date.
     *
     * This is necessary when updating a past check-in because
     * each check-in's balance depends on the previous one.
     *
     * Yeah, this could be O(n) expensive, but in practice nobody's
     * gonna have thousands of check-ins. If they do, they're a
     * fucking legend and deserve the slight delay.
     */
    private suspend fun recalculateBalancesAfter(afterDate: LocalDate) {
        val exerciseReward = getExerciseReward()
        val allCheckIns = checkInDao.getAllCheckInsAsc()
        var currentBalance = getPreviousBalance(afterDate.plusDays(1))

        // Find the check-in for afterDate and use its balance as starting point
        val checkInOnDate = allCheckIns.find { it.date == afterDate }
        if (checkInOnDate != null) {
            currentBalance = checkInOnDate.balanceAfter
        }

        // Update all subsequent check-ins
        allCheckIns
            .filter { it.date > afterDate }
            .forEach { checkIn ->
                val newBalance = calculateNewBalance(currentBalance, checkIn.didExercise, exerciseReward)
                if (newBalance != checkIn.balanceAfter) {
                    checkInDao.update(checkIn.copy(balanceAfter = newBalance))
                }
                currentBalance = newBalance
            }
    }

    /**
     * Calculate the current streak (consecutive exercise days).
     *
     * A streak is broken when:
     * - User misses a day (didExercise = false) AND it's not frozen
     * - There's a gap in dates (user didn't log at all) AND it's not frozen
     *
     * @param frozenDates Set of dates that are "frozen" and don't break the streak
     */
    suspend fun calculateStreak(frozenDates: Set<LocalDate> = emptySet()): Int {
        val allCheckIns = checkInDao.getAllCheckInsAsc()
        if (allCheckIns.isEmpty() && frozenDates.isEmpty()) return 0

        var streak = 0
        val today = LocalDate.now()
        var currentDate = today

        // Work backwards from today
        val checkInMap = allCheckIns.associateBy { it.date }

        while (true) {
            val checkIn = checkInMap[currentDate]
            val isFrozen = frozenDates.contains(currentDate)

            if (checkIn == null) {
                // No check-in for this date
                if (currentDate == today) {
                    // Today and not checked in yet - continue checking yesterday
                    currentDate = currentDate.minusDays(1)
                    continue
                }

                if (isFrozen) {
                    // Day is frozen - doesn't break streak, but doesn't add to it either
                    currentDate = currentDate.minusDays(1)
                    continue
                }

                // Not frozen, no check-in - streak is broken
                break
            }

            if (!checkIn.didExercise) {
                if (isFrozen) {
                    // Logged as missed but frozen - continue without breaking
                    currentDate = currentDate.minusDays(1)
                    continue
                }
                // User logged but didn't exercise and not frozen - streak broken
                break
            }

            // User exercised this day!
            streak++
            currentDate = currentDate.minusDays(1)
        }

        return streak
    }

    /**
     * Find the first missed day that could benefit from a freeze.
     * Returns null if no streak-breaking gap exists.
     *
     * A "missed day" is either:
     * - A gap in dates (no check-in)
     * - A logged "rest day" (didExercise = false)
     *
     * @param frozenDates Dates already frozen (to exclude)
     */
    suspend fun findMissedDayForFreeze(frozenDates: Set<LocalDate> = emptySet()): LocalDate? {
        val allCheckIns = checkInDao.getAllCheckInsAsc()
        val today = LocalDate.now()
        var currentDate = today.minusDays(1) // Start from yesterday

        val checkInMap = allCheckIns.associateBy { it.date }

        // Look back up to 7 days for a missed day
        repeat(7) {
            if (frozenDates.contains(currentDate)) {
                currentDate = currentDate.minusDays(1)
                return@repeat
            }

            val checkIn = checkInMap[currentDate]

            if (checkIn == null) {
                // Gap - this is a missed day
                return currentDate
            }

            if (!checkIn.didExercise) {
                // Logged as rest day - this could be frozen too
                return currentDate
            }

            // Exercise day - continue looking back
            currentDate = currentDate.minusDays(1)
        }

        return null
    }

    /**
     * Get what the balance would be if user exercises today.
     */
    suspend fun previewExerciseBalance(): Double {
        val exerciseReward = getExerciseReward()
        val current = checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
        return calculateNewBalance(current, didExercise = true, exerciseReward)
    }

    /**
     * Get what the balance would be if user misses today.
     */
    suspend fun previewMissBalance(): Double {
        val exerciseReward = getExerciseReward()
        val current = checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
        return calculateNewBalance(current, didExercise = false, exerciseReward)
    }

    /**
     * Get current balance (one-time query, not a Flow).
     * Useful for operations that need to know the current balance
     * at a specific point in time.
     */
    suspend fun getCurrentBalanceOnce(): Double {
        return checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Get total number of workout days (days where user exercised).
     */
    suspend fun getTotalWorkoutCount(): Int {
        return checkInDao.getTotalWorkoutCount()
    }

    /**
     * Deduct an amount from the current balance.
     * Used for streak freeze purchases, etc.
     *
     * This updates the most recent check-in's balanceAfter.
     * If no check-ins exist, this is a no-op (can't go negative from 0).
     *
     * @param amount Amount to deduct
     * @return true if successful, false if insufficient balance
     */
    suspend fun deductBalance(amount: Double): Boolean {
        val latestCheckIn = checkInDao.getLatestCheckIn() ?: return false
        val currentBalance = latestCheckIn.balanceAfter

        if (currentBalance < amount) {
            return false
        }

        val newBalance = ((currentBalance - amount) * 100).roundToLong() / 100.0
        checkInDao.update(latestCheckIn.copy(balanceAfter = newBalance))
        return true
    }
}

