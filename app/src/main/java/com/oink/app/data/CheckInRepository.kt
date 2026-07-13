package com.oink.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

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
 *
 * The exerciseRewardProvider parameter uses an interface instead of
 * the concrete PreferencesRepository to enable testing with fakes.
 * PreferencesRepository implements ExerciseRewardProvider.
 *
 * The deductionProvider supplies cash-out + freeze spending totals so that a
 * miss halves the SPENDABLE balance (raw - deductions) rather than the raw
 * ledger. See [calculateNewBalance].
 */
class CheckInRepository(
    private val checkInDao: CheckInDao,
    private val exerciseRewardProvider: ExerciseRewardProvider,
    private val deductionProvider: DeductionProvider,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    companion object {
        const val DEFAULT_EXERCISE_REWARD = 500L // cents ($5.00)
        const val STARTING_BALANCE = 0L
    }

    /**
     * Today's date in the clock's zone.
     *
     * Every "now" in the repository routes through the injected [clock], so
     * tests pin it to a fixed instant instead of the real system clock and the
     * app has one authoritative source of "today".
     */
    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Get the current exercise reward from preferences, in cents.
     */
    suspend fun getExerciseReward(): Long {
        return exerciseRewardProvider.getExerciseReward()
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
    val currentBalance: Flow<Long> = latestCheckIn.map { checkIn ->
        checkIn?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Flow of today's check-in status.
     *
     * The query re-subscribes whenever the calendar date rolls over (see
     * [currentDateFlow]), so leaving the app open past midnight shows the new
     * day's status rather than a stale "today" captured when the flow was first
     * collected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayCheckIn(): Flow<CheckIn?> {
        return currentDateFlow().flatMapLatest { date ->
            checkInDao.getTodayCheckInFlow(date.toEpochDay())
        }
    }

    /**
     * Emits the current date, then re-emits each time the date rolls over.
     *
     * Sleeps until the clock's next local midnight between emissions, so an
     * idle subscription costs nothing yet still advances "today" at the day
     * boundary. Driven by the injected [clock] so tests stay deterministic: a
     * fixed clock emits once and then suspends (its next-midnight delay never
     * elapses in virtual time).
     */
    private fun currentDateFlow(): Flow<LocalDate> = flow {
        var current = today()
        emit(current)
        while (true) {
            val nextMidnight = current.plusDays(1).atStartOfDay(clock.zone).toInstant()
            delay((nextMidnight.toEpochMilli() - clock.millis()).coerceAtLeast(0L))
            current = today()
            emit(current)
        }
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
        val deductions = deductionProvider.getDeductionsAsOf(date)
        val newBalance = calculateNewBalance(previousBalance, didExercise, exerciseReward, deductions)

        val checkIn = CheckIn(
            date = date,
            didExercise = didExercise,
            balanceAfter = newBalance,
            exerciseRewardAtTime = exerciseReward
        )

        val id = checkInDao.insert(checkIn)

        // If this is a past date, we need to recalculate all subsequent balances
        if (date < today()) {
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

        // Find the balance BEFORE this check-in. Reuse the reward stored on the
        // check-in so editing a past day never rewrites it with today's rate.
        val previousBalance = getPreviousBalance(existing.date)
        val deductions = deductionProvider.getDeductionsAsOf(existing.date)
        val newBalance = calculateNewBalance(previousBalance, didExercise, existing.exerciseRewardAtTime, deductions)

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
     * Bulk record check-ins for multiple dates.
     *
     * This is optimized for batch operations like selecting multiple days
     * in the calendar and marking them all as exercised or missed.
     *
     * Processes dates in chronological order and only recalculates
     * balances once after all updates are complete.
     *
     * @param dates The dates to update
     * @param didExercise Whether these days were exercise days
     */
    suspend fun bulkRecordCheckIns(dates: Set<LocalDate>, didExercise: Boolean) {
        if (dates.isEmpty()) return

        val exerciseReward = getExerciseReward()
        val sortedDates = dates.sorted()
        val earliestDate = sortedDates.first()

        // Process each date
        for (date in sortedDates) {
            val existing = checkInDao.getCheckInForDate(date.toEpochDay())
            val previousBalance = getPreviousBalance(date)
            val deductions = deductionProvider.getDeductionsAsOf(date)

            if (existing != null) {
                // Update existing, keeping its recorded reward so a past day
                // isn't rewritten with today's rate.
                if (existing.didExercise != didExercise) {
                    val newBalance = calculateNewBalance(
                        previousBalance, didExercise, existing.exerciseRewardAtTime, deductions
                    )
                    checkInDao.update(
                        existing.copy(
                            didExercise = didExercise,
                            balanceAfter = newBalance
                        )
                    )
                }
            } else {
                // Create new
                val newBalance = calculateNewBalance(previousBalance, didExercise, exerciseReward, deductions)
                checkInDao.insert(
                    CheckIn(
                        date = date,
                        didExercise = didExercise,
                        balanceAfter = newBalance,
                        exerciseRewardAtTime = exerciseReward
                    )
                )
            }
        }

        // Recalculate all balances after the earliest date we touched
        recalculateBalancesAfter(earliestDate)
    }

    /**
     * Get the balance before a given date.
     * If no check-ins exist before this date, returns the starting balance.
     *
     * Uses a targeted DAO query instead of loading all check-ins.
     */
    private suspend fun getPreviousBalance(date: LocalDate): Long {
        val previousCheckIn = checkInDao.getCheckInBefore(date.toEpochDay())
        return previousCheckIn?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Calculate the new raw check-in balance based on the rules:
     * - Exercise: + exercise reward (configurable, default $5.00)
     * - Miss: halve the SPENDABLE balance, expressed back in raw terms
     *
     * A miss must halve what the user can actually spend, which is
     * `raw - deductions` (cash-outs + freeze spending), not the raw ledger.
     * Solving for the new raw balance:
     *   spendable' = (raw - deductions) / 2
     *   raw'       = spendable' + deductions = (raw + deductions) / 2
     * Deductions stay tracked separately in their own tables; this only
     * references them, so toggling a check-in never loses track of spending.
     *
     * All values are cents. Halving uses round-half-up on the whole-cent
     * result; since `raw + deductions` is never negative, `(x + 1) / 2` gives
     * the correctly rounded half.
     *
     * @param deductions Total cash-outs + freeze spending in force at this
     *   check-in's date. Zero for exercise days (unused) and for users who
     *   have never cashed out, in which case a miss reduces to raw / 2.
     */
    private fun calculateNewBalance(
        previousBalance: Long,
        didExercise: Boolean,
        exerciseReward: Long,
        deductions: Long
    ): Long {
        return if (didExercise) {
            previousBalance + exerciseReward
        } else {
            (previousBalance + deductions + 1) / 2
        }
    }

    /**
     * Recalculate all balances after a given date.
     *
     * This is necessary when updating a past check-in because
     * each check-in's balance depends on the previous one.
     *
     * Each check-in is replayed with the reward that applied on its own day
     * (`exerciseRewardAtTime`), never today's setting, so history stays stable
     * when the user changes their reward.
     *
     * Yeah, this could be O(n) expensive, but in practice nobody's
     * gonna have thousands of check-ins. If they do, they're a
     * fucking legend and deserve the slight delay.
     */
    private suspend fun recalculateBalancesAfter(afterDate: LocalDate) {
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
                val deductions = deductionProvider.getDeductionsAsOf(checkIn.date)
                val newBalance = calculateNewBalance(
                    currentBalance, checkIn.didExercise, checkIn.exerciseRewardAtTime, deductions
                )
                // Integer comparison - no float-equality fuzziness.
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
        val today = today()
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
        val today = today()
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
    suspend fun previewExerciseBalance(): Long {
        val exerciseReward = getExerciseReward()
        val current = checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
        val deductions = deductionProvider.getDeductionsAsOf(today())
        return calculateNewBalance(current, didExercise = true, exerciseReward, deductions)
    }

    /**
     * Get what the raw balance would be if user misses today.
     *
     * Returns the raw check-in balance; callers subtract current deductions to
     * get the spendable preview (see MainViewModel). Because a miss halves
     * spendable, the deduction-aware raw result yields exactly half the current
     * spendable balance once deductions are subtracted.
     */
    suspend fun previewMissBalance(): Long {
        val exerciseReward = getExerciseReward()
        val current = checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
        val deductions = deductionProvider.getDeductionsAsOf(today())
        return calculateNewBalance(current, didExercise = false, exerciseReward, deductions)
    }

    /**
     * Get current balance (one-time query, not a Flow).
     * Useful for operations that need to know the current balance
     * at a specific point in time.
     */
    suspend fun getCurrentBalanceOnce(): Long {
        return checkInDao.getLatestCheckIn()?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Get total number of workout days (days where user exercised).
     */
    suspend fun getTotalWorkoutCount(): Int {
        return checkInDao.getTotalWorkoutCount()
    }

    // NOTE: We intentionally DO NOT have a deductBalance() method!
    //
    // Deductions (cash-outs, freeze costs) are tracked SEPARATELY from check-ins.
    // The actual balance is calculated as:
    //   Check-in balance - Total cashed out - Total freeze spending
    //
    // This prevents the bug where toggling a check-in (exercise ↔ miss) would
    // lose track of deductions that were previously baked into the check-in.
    //
    // See MainViewModel.currentBalance for the calculation.
}

