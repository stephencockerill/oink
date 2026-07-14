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
 * Every query, streak, and halving is scoped to a habit: the repository serves
 * all habits, and each method takes a `habitId` (defaulting to
 * [HabitRepository.DEFAULT_HABIT_ID] for the single-habit call sites). Two
 * habits therefore never collide - each has its own check-in per date, its own
 * streak, and its own deductions.
 *
 * The dailyRewardProvider parameter uses an interface instead of the concrete
 * Room-backed source to enable testing with fakes; in production
 * [HabitRewardProvider] reads [Habit.rewardValue].
 *
 * The deductionProvider supplies a habit's cash-out + freeze spending totals so
 * that a miss halves the SPENDABLE balance (raw - deductions) rather than the
 * raw ledger. See [calculateNewBalance].
 */
class CheckInRepository(
    private val checkInDao: CheckInDao,
    private val dailyRewardProvider: DailyRewardProvider,
    private val deductionProvider: DeductionProvider,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    companion object {
        const val DEFAULT_DAILY_REWARD = 500L // cents ($5.00)
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
     * Get a habit's per-day reward, in cents. Sourced from [Habit.rewardValue].
     */
    suspend fun getDailyReward(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Long {
        return dailyRewardProvider.getDailyReward(habitId)
    }

    /**
     * Flow of a habit's check-ins, newest first.
     */
    fun allCheckIns(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Flow<List<CheckIn>> =
        checkInDao.getAllCheckInsFlow(habitId)

    /**
     * Flow of a habit's latest check-in.
     */
    fun latestCheckIn(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Flow<CheckIn?> =
        checkInDao.getLatestCheckInFlow(habitId)

    /**
     * Flow of a habit's current balance.
     * Derived from the latest check-in's balanceAfter field.
     */
    fun currentBalance(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Flow<Long> =
        latestCheckIn(habitId).map { checkIn ->
            checkIn?.balanceAfter ?: STARTING_BALANCE
        }

    /**
     * Flow of a habit's today check-in status.
     *
     * The query re-subscribes whenever the calendar date rolls over (see
     * [currentDateFlow]), so leaving the app open past midnight shows the new
     * day's status rather than a stale "today" captured when the flow was first
     * collected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTodayCheckIn(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Flow<CheckIn?> {
        return currentDateFlow().flatMapLatest { date ->
            checkInDao.getTodayCheckInFlow(habitId, date.toEpochDay())
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
     * Get a habit's check-in for a specific date.
     */
    suspend fun getCheckInForDate(
        date: LocalDate,
        habitId: Long = HabitRepository.DEFAULT_HABIT_ID
    ): CheckIn? {
        return checkInDao.getCheckInForDate(habitId, date.toEpochDay())
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
     * @param completed Whether the habit was completed
     * @param habitId The habit to record against
     * @return The recorded check-in
     */
    suspend fun recordCheckIn(
        date: LocalDate,
        completed: Boolean,
        habitId: Long = HabitRepository.DEFAULT_HABIT_ID
    ): CheckIn {
        val existingCheckIn = getCheckInForDate(date, habitId)

        if (existingCheckIn != null) {
            // Update existing check-in
            return updateCheckIn(existingCheckIn, completed, habitId)
        }

        // New check-in
        val dailyReward = getDailyReward(habitId)
        val previousBalance = getPreviousBalance(date, habitId)
        val deductions = deductionProvider.getDeductionsAsOf(habitId, date)
        val newBalance = calculateNewBalance(previousBalance, completed, dailyReward, deductions)

        val checkIn = CheckIn(
            date = date,
            completed = completed,
            balanceAfter = newBalance,
            rewardAtTime = dailyReward,
            habitId = habitId
        )

        val id = checkInDao.insert(checkIn)

        // If this is a past date, we need to recalculate all subsequent balances
        if (date < today()) {
            recalculateBalancesAfter(date, habitId)
        }

        return checkIn.copy(id = id)
    }

    /**
     * Update an existing check-in.
     * This will also recalculate all subsequent balances for its habit.
     */
    private suspend fun updateCheckIn(existing: CheckIn, completed: Boolean, habitId: Long): CheckIn {
        if (existing.completed == completed) {
            // No change needed
            return existing
        }

        // Find the balance BEFORE this check-in. Reuse the reward stored on the
        // check-in so editing a past day never rewrites it with today's rate.
        val previousBalance = getPreviousBalance(existing.date, habitId)
        val deductions = deductionProvider.getDeductionsAsOf(habitId, existing.date)
        val newBalance = calculateNewBalance(previousBalance, completed, existing.rewardAtTime, deductions)

        val updated = existing.copy(
            completed = completed,
            balanceAfter = newBalance
        )

        checkInDao.update(updated)

        // Recalculate all subsequent balances
        recalculateBalancesAfter(existing.date, habitId)

        return updated
    }

    /**
     * Bulk record check-ins for multiple dates.
     *
     * This is optimized for batch operations like selecting multiple days
     * in the calendar and marking them all as completed or missed.
     *
     * Processes dates in chronological order and only recalculates
     * balances once after all updates are complete.
     *
     * @param dates The dates to update
     * @param completed Whether these days were completed
     * @param habitId The habit to record against
     */
    suspend fun bulkRecordCheckIns(
        dates: Set<LocalDate>,
        completed: Boolean,
        habitId: Long = HabitRepository.DEFAULT_HABIT_ID
    ) {
        if (dates.isEmpty()) return

        val dailyReward = getDailyReward(habitId)
        val sortedDates = dates.sorted()
        val earliestDate = sortedDates.first()

        // Process each date
        for (date in sortedDates) {
            val existing = checkInDao.getCheckInForDate(habitId, date.toEpochDay())
            val previousBalance = getPreviousBalance(date, habitId)
            val deductions = deductionProvider.getDeductionsAsOf(habitId, date)

            if (existing != null) {
                // Update existing, keeping its recorded reward so a past day
                // isn't rewritten with today's rate.
                if (existing.completed != completed) {
                    val newBalance = calculateNewBalance(
                        previousBalance, completed, existing.rewardAtTime, deductions
                    )
                    checkInDao.update(
                        existing.copy(
                            completed = completed,
                            balanceAfter = newBalance
                        )
                    )
                }
            } else {
                // Create new
                val newBalance = calculateNewBalance(previousBalance, completed, dailyReward, deductions)
                checkInDao.insert(
                    CheckIn(
                        date = date,
                        completed = completed,
                        balanceAfter = newBalance,
                        rewardAtTime = dailyReward,
                        habitId = habitId
                    )
                )
            }
        }

        // Recalculate all balances after the earliest date we touched
        recalculateBalancesAfter(earliestDate, habitId)
    }

    /**
     * Get the balance before a given date within a habit.
     * If no check-ins exist before this date, returns the starting balance.
     *
     * Uses a targeted DAO query instead of loading all check-ins.
     */
    private suspend fun getPreviousBalance(date: LocalDate, habitId: Long): Long {
        val previousCheckIn = checkInDao.getCheckInBefore(habitId, date.toEpochDay())
        return previousCheckIn?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Calculate the new raw check-in balance based on the rules:
     * - Completed: + daily reward (configurable, default $5.00)
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
     *   check-in's date. Zero for completed days (unused) and for users who
     *   have never cashed out, in which case a miss reduces to raw / 2.
     */
    private fun calculateNewBalance(
        previousBalance: Long,
        completed: Boolean,
        dailyReward: Long,
        deductions: Long
    ): Long {
        return if (completed) {
            previousBalance + dailyReward
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
     * (`rewardAtTime`), never today's setting, so history stays stable
     * when the user changes their reward.
     *
     * Yeah, this could be O(n) expensive, but in practice nobody's
     * gonna have thousands of check-ins. If they do, they're a
     * legend and deserve the slight delay.
     */
    private suspend fun recalculateBalancesAfter(afterDate: LocalDate, habitId: Long) {
        val allCheckIns = checkInDao.getAllCheckInsAsc(habitId)
        var currentBalance = getPreviousBalance(afterDate.plusDays(1), habitId)

        // Find the check-in for afterDate and use its balance as starting point
        val checkInOnDate = allCheckIns.find { it.date == afterDate }
        if (checkInOnDate != null) {
            currentBalance = checkInOnDate.balanceAfter
        }

        // Update all subsequent check-ins
        allCheckIns
            .filter { it.date > afterDate }
            .forEach { checkIn ->
                val deductions = deductionProvider.getDeductionsAsOf(habitId, checkIn.date)
                val newBalance = calculateNewBalance(
                    currentBalance, checkIn.completed, checkIn.rewardAtTime, deductions
                )
                // Integer comparison - no float-equality fuzziness.
                if (newBalance != checkIn.balanceAfter) {
                    checkInDao.update(checkIn.copy(balanceAfter = newBalance))
                }
                currentBalance = newBalance
            }
    }

    /**
     * Calculate the current streak (consecutive completed days).
     *
     * A streak is broken when:
     * - User misses a day (completed = false) AND it's not frozen
     * - There's a gap in dates (user didn't log at all) AND it's not frozen
     *
     * @param habitId The habit whose streak to calculate
     * @param frozenDates Set of dates that are "frozen" and don't break the streak
     */
    suspend fun calculateStreak(
        habitId: Long = HabitRepository.DEFAULT_HABIT_ID,
        frozenDates: Set<LocalDate> = emptySet()
    ): Int =
        calculateStreak(checkInDao.getAllCheckInsAsc(habitId), frozenDates)

    /**
     * Pure streak calculation over an already-loaded list of check-ins.
     *
     * This overload does no I/O, so callers holding a reactive Flow of
     * check-ins (see MainViewModel) can recompute the streak on every
     * emission without a redundant DAO read.
     *
     * @param checkIns All check-ins (order irrelevant; keyed by date internally)
     * @param frozenDates Set of dates that are "frozen" and don't break the streak
     */
    fun calculateStreak(checkIns: List<CheckIn>, frozenDates: Set<LocalDate> = emptySet()): Int {
        if (checkIns.isEmpty() && frozenDates.isEmpty()) return 0

        var streak = 0
        val today = today()
        var currentDate = today

        // Work backwards from today
        val checkInMap = checkIns.associateBy { it.date }

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

            if (!checkIn.completed) {
                if (isFrozen) {
                    // Logged as missed but frozen - continue without breaking
                    currentDate = currentDate.minusDays(1)
                    continue
                }
                // User logged but didn't complete and not frozen - streak broken
                break
            }

            // User completed this day!
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
     * - A logged "rest day" (completed = false)
     *
     * @param habitId The habit to search
     * @param frozenDates Dates already frozen (to exclude)
     */
    suspend fun findMissedDayForFreeze(
        habitId: Long = HabitRepository.DEFAULT_HABIT_ID,
        frozenDates: Set<LocalDate> = emptySet()
    ): LocalDate? =
        findMissedDayForFreeze(checkInDao.getAllCheckInsAsc(habitId), frozenDates)

    /**
     * Pure freeze-candidate search over an already-loaded list of check-ins.
     *
     * Does no I/O so reactive callers can recompute on every check-in emission.
     *
     * @param checkIns All check-ins (order irrelevant; keyed by date internally)
     * @param frozenDates Dates to skip over (already frozen, or dismissed by the user)
     */
    fun findMissedDayForFreeze(checkIns: List<CheckIn>, frozenDates: Set<LocalDate> = emptySet()): LocalDate? {
        val today = today()
        var currentDate = today.minusDays(1) // Start from yesterday

        val checkInMap = checkIns.associateBy { it.date }

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

            if (!checkIn.completed) {
                // Logged as rest day - this could be frozen too
                return currentDate
            }

            // Completed day - continue looking back
            currentDate = currentDate.minusDays(1)
        }

        return null
    }

    /**
     * Get what the raw balance would be if the user completes today.
     */
    suspend fun previewCompletedBalance(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Long {
        val dailyReward = getDailyReward(habitId)
        val current = checkInDao.getLatestCheckIn(habitId)?.balanceAfter ?: STARTING_BALANCE
        val deductions = deductionProvider.getDeductionsAsOf(habitId, today())
        return previewCompletedBalance(current, dailyReward, deductions)
    }

    /**
     * Pure completed-day preview calculation over already-loaded inputs.
     *
     * Does no I/O so reactive callers (see MainViewModel) can recompute on
     * every balance/reward/deduction emission.
     *
     * @param rawBalance Current raw check-in balance
     * @param dailyReward Per-day reward in cents
     * @param deductions Total cash-outs + freeze spending in force today
     */
    fun previewCompletedBalance(rawBalance: Long, dailyReward: Long, deductions: Long): Long =
        calculateNewBalance(rawBalance, completed = true, dailyReward, deductions)

    /**
     * Get what the raw balance would be if user misses today.
     *
     * Returns the raw check-in balance; callers subtract current deductions to
     * get the spendable preview (see MainViewModel). Because a miss halves
     * spendable, the deduction-aware raw result yields exactly half the current
     * spendable balance once deductions are subtracted.
     */
    suspend fun previewMissBalance(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Long {
        val current = checkInDao.getLatestCheckIn(habitId)?.balanceAfter ?: STARTING_BALANCE
        val deductions = deductionProvider.getDeductionsAsOf(habitId, today())
        return previewMissBalance(current, deductions)
    }

    /**
     * Pure miss-preview calculation over already-loaded inputs.
     *
     * Does no I/O so reactive callers (see MainViewModel) can recompute on
     * every balance/deduction emission. A miss ignores the daily reward.
     *
     * @param rawBalance Current raw check-in balance
     * @param deductions Total cash-outs + freeze spending in force today
     */
    fun previewMissBalance(rawBalance: Long, deductions: Long): Long =
        calculateNewBalance(rawBalance, completed = false, dailyReward = 0L, deductions)

    /**
     * Get current balance (one-time query, not a Flow).
     * Useful for operations that need to know the current balance
     * at a specific point in time.
     */
    suspend fun getCurrentBalanceOnce(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Long {
        return checkInDao.getLatestCheckIn(habitId)?.balanceAfter ?: STARTING_BALANCE
    }

    /**
     * Get a habit's total number of completed days (days marked done).
     */
    suspend fun getCompletedDayCount(habitId: Long = HabitRepository.DEFAULT_HABIT_ID): Int {
        return checkInDao.getCompletedDayCount(habitId)
    }

    // NOTE: We intentionally DO NOT have a deductBalance() method!
    //
    // Deductions (cash-outs, freeze costs) are tracked SEPARATELY from check-ins.
    // The actual balance is calculated as:
    //   Check-in balance - Total cashed out - Total freeze spending
    //
    // This prevents the bug where toggling a check-in (completed ↔ miss) would
    // lose track of deductions that were previously baked into the check-in.
    //
    // See MainViewModel.currentBalance for the calculation.
}

