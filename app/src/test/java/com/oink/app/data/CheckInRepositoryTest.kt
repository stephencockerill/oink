package com.oink.app.data

import com.oink.app.utils.BalanceCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for CheckInRepository.
 *
 * Tests the core business logic:
 * - Balance calculations (exercise +$5, miss /2)
 * - Streak calculations
 * - Check-in recording and updating
 *
 * All money values are in cents (Long), e.g. $5.00 is 500.
 *
 * Uses fakes instead of mocks (per Android guidelines).
 */
class CheckInRepositoryTest {

    private lateinit var fakeDao: FakeCheckInDao
    private lateinit var repository: CheckInRepository

    // Simple fake for exercise reward - using SAM interface
    private var exerciseReward = 500L
    private val fakeRewardProvider = ExerciseRewardProvider { exerciseReward }

    // Total deductions (cash-outs + freeze) in cents the fake reports for every
    // date. Zero by default, so a miss reduces to raw / 2 for the majority of tests.
    private var deductions = 0L
    private val fakeDeductionProvider = DeductionProvider { deductions }

    @Before
    fun setup() {
        fakeDao = FakeCheckInDao()
        repository = CheckInRepository(fakeDao, fakeRewardProvider, fakeDeductionProvider)
        exerciseReward = 500L // Reset to default ($5.00)
        deductions = 0L // Reset to default
    }

    // =====================================================================
    // Balance calculation tests
    // =====================================================================

    @Test
    fun `new user starts with zero balance`() = runTest {
        val balance = repository.currentBalance.first()
        assertEquals(0L, balance)
    }

    @Test
    fun `first exercise adds reward to starting balance`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today, didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(500L, balance)
    }

    @Test
    fun `first miss halves starting balance (stays at zero)`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today, didExercise = false)

        val balance = repository.currentBalance.first()
        assertEquals(0L, balance) // 0 / 2 = 0
    }

    @Test
    fun `consecutive exercises accumulate balance`() = runTest {
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = true) // $10
        repository.recordCheckIn(day3, didExercise = true) // $15

        val balance = repository.currentBalance.first()
        assertEquals(1500L, balance)
    }

    @Test
    fun `miss halves accumulated balance`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = false) // $2.50

        val balance = repository.currentBalance.first()
        assertEquals(250L, balance)
    }

    @Test
    fun `exercise after miss adds to halved balance`() = runTest {
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true)  // $5
        repository.recordCheckIn(day2, didExercise = false) // $2.50
        repository.recordCheckIn(day3, didExercise = true)  // $7.50

        val balance = repository.currentBalance.first()
        assertEquals(750L, balance)
    }

    @Test
    fun `custom exercise reward is used`() = runTest {
        exerciseReward = 1000L
        val today = LocalDate.now()

        repository.recordCheckIn(today, didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
    }

    // =====================================================================
    // Miss halves SPENDABLE, not raw (issue #6)
    //
    // Spendable = raw check-in balance - deductions (cash-outs + freeze).
    // A miss must halve what the user can actually spend, regardless of how
    // much has been cashed out. These assert the invariant end-to-end by
    // running the result through the real BalanceCalculator.
    // =====================================================================

    @Test
    fun `miss halves spendable balance after a cash-out`() = runTest {
        // The issue's exact example: raw $100, cashed out $10 -> spendable $90.
        exerciseReward = 10000L
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // raw $100

        deductions = 1000L // simulate a $10 cash-out already recorded
        repository.recordCheckIn(LocalDate.now(), didExercise = false) // miss

        val raw = repository.getCurrentBalanceOnce()
        val spendable = BalanceCalculator.calculateActualBalance(
            checkInBalance = raw,
            totalCashedOut = deductions,
            totalFreezeSpending = 0L
        )

        // Spendable must be exactly half of the pre-miss $90 - NOT $40, which is
        // what halving raw ($100 -> $50, then - $10) would have produced.
        assertEquals(4500L, spendable) // $45.00
        assertEquals(5500L, raw) // raw' = (10000 + 1000) / 2
    }

    @Test
    fun `miss halves raw when nothing has been cashed out`() = runTest {
        // Regression guard: with zero deductions, spendable == raw and a miss
        // is the classic raw / 2.
        exerciseReward = 10000L
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // raw $100

        deductions = 0L
        repository.recordCheckIn(LocalDate.now(), didExercise = false) // miss

        val raw = repository.getCurrentBalanceOnce()
        assertEquals(5000L, raw) // $50.00
    }

    @Test
    fun `preview miss reflects halved spendable after a cash-out`() = runTest {
        exerciseReward = 10000L
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // raw $100

        deductions = 1000L // $10 cashed out
        val rawPreview = repository.previewMissBalance()
        // MainViewModel subtracts current deductions from the raw preview.
        val spendablePreview = (rawPreview - deductions).coerceAtLeast(0L)

        assertEquals(4500L, spendablePreview) // $45.00
    }

    @Test
    fun `recalculating a past miss uses deductions in force`() = runTest {
        // Two exercise days then a miss, with money cashed out throughout.
        exerciseReward = 10000L
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // raw $100
        repository.recordCheckIn(day2, didExercise = true) // raw $200

        deductions = 2000L // $20 cashed out; spendable now $180
        repository.recordCheckIn(day3, didExercise = false) // miss -> spendable $90

        // Retroactively flip day2 to a miss; day3's balance must recalculate.
        repository.recordCheckIn(day2, didExercise = false)

        // day2 miss: (10000 + 2000) / 2 = 6000
        // day3 miss: (6000 + 2000) / 2 = 4000
        val raw = repository.getCurrentBalanceOnce()
        val spendable = BalanceCalculator.calculateActualBalance(raw, deductions, 0L)
        assertEquals(4000L, raw) // $40.00
        assertEquals(2000L, spendable) // $20.00
    }

    // =====================================================================
    // Check-in update tests (the bug that started all this!)
    // =====================================================================

    @Test
    fun `updating check-in from exercise to miss recalculates balance`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = true) // $10

        // Now change day2 to miss
        repository.recordCheckIn(day2, didExercise = false) // $5 / 2 = $2.50

        val balance = repository.currentBalance.first()
        assertEquals(250L, balance)
    }

    @Test
    fun `updating check-in from miss to exercise recalculates balance`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = false) // $2.50

        // Now change day2 to exercise
        repository.recordCheckIn(day2, didExercise = true) // $5 + $5 = $10

        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `updating past check-in recalculates all subsequent balances`() = runTest {
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true)  // $5
        repository.recordCheckIn(day2, didExercise = true)  // $10
        repository.recordCheckIn(day3, didExercise = true)  // $15

        // Change day1 to miss - should cascade
        repository.recordCheckIn(day1, didExercise = false) // $0
        // day2: 0 + 5 = $5
        // day3: 5 + 5 = $10

        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
    }

    // =====================================================================
    // Reward-at-time tests (issue #10)
    //
    // Each check-in records the reward in force on its own day. Recomputing
    // historical balances must replay each day with its stored reward, never
    // whatever the reward is set to today.
    // =====================================================================

    @Test
    fun `check-in records the reward in force at the time`() = runTest {
        exerciseReward = 700L
        val date = LocalDate.now().minusDays(1)

        repository.recordCheckIn(date, didExercise = true)

        val checkIn = repository.getCheckInForDate(date)
        assertEquals(700L, checkIn!!.exerciseRewardAtTime)
    }

    @Test
    fun `recompute uses each day's stored reward, not today's`() = runTest {
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        // Record three exercise days while the reward is $5.00.
        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = true) // $10
        repository.recordCheckIn(day3, didExercise = true) // $15

        // User later bumps the reward to $10.00.
        exerciseReward = 1000L

        // Editing the earliest day triggers a full recompute of day2 and day3.
        // Those days were earned at $5.00 and must NOT be re-valued at $10.00.
        repository.recordCheckIn(day1, didExercise = false) // $0

        // day1 miss: 0. day2: 0 + 5 = $5. day3: 5 + 5 = $10 (both at the OLD rate).
        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `toggling a past day keeps its original reward`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        exerciseReward = 500L
        repository.recordCheckIn(day1, didExercise = true)  // $5 at $5.00 rate
        repository.recordCheckIn(day2, didExercise = false) // $2.50

        // Reward changes, then the user flips day2 from miss to exercise.
        exerciseReward = 2000L
        repository.recordCheckIn(day2, didExercise = true)

        // day2 must use its own recorded $5.00 reward: $5 + $5 = $10, not $25.
        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
        assertEquals(500L, repository.getCheckInForDate(day2)!!.exerciseRewardAtTime)
    }

    // =====================================================================
    // Streak calculation tests
    // =====================================================================

    @Test
    fun `no check-ins means zero streak`() = runTest {
        val streak = repository.calculateStreak()
        assertEquals(0, streak)
    }

    @Test
    fun `single exercise day is streak of 1`() = runTest {
        repository.recordCheckIn(LocalDate.now(), didExercise = true)
        val streak = repository.calculateStreak()
        assertEquals(1, streak)
    }

    @Test
    fun `consecutive exercise days build streak`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        repository.recordCheckIn(today.minusDays(1), didExercise = true)
        repository.recordCheckIn(today, didExercise = true)

        val streak = repository.calculateStreak()
        assertEquals(3, streak)
    }

    @Test
    fun `miss day breaks streak`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        repository.recordCheckIn(today.minusDays(1), didExercise = false) // Streak broken!
        repository.recordCheckIn(today, didExercise = true)

        val streak = repository.calculateStreak()
        assertEquals(1, streak) // Only today counts
    }

    @Test
    fun `gap in dates breaks streak`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today.minusDays(3), didExercise = true)
        // No check-in for day -2 or -1
        repository.recordCheckIn(today, didExercise = true)

        val streak = repository.calculateStreak()
        assertEquals(1, streak) // Only today counts
    }

    @Test
    fun `frozen date does not break streak`() = runTest {
        val today = LocalDate.now()
        val frozenDate = today.minusDays(1)

        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        // Day -1 is frozen (no check-in needed)
        repository.recordCheckIn(today, didExercise = true)

        val streak = repository.calculateStreak(frozenDates = setOf(frozenDate))
        assertEquals(2, streak) // Frozen day doesn't break, but doesn't add either
    }

    @Test
    fun `frozen miss does not break streak`() = runTest {
        val today = LocalDate.now()
        val frozenDate = today.minusDays(1)

        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        repository.recordCheckIn(today.minusDays(1), didExercise = false) // Miss but frozen!
        repository.recordCheckIn(today, didExercise = true)

        val streak = repository.calculateStreak(frozenDates = setOf(frozenDate))
        assertEquals(2, streak)
    }

    @Test
    fun `today not checked in yet continues yesterday streak`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        repository.recordCheckIn(today.minusDays(1), didExercise = true)
        // Today not checked in yet

        val streak = repository.calculateStreak()
        assertEquals(2, streak)
    }

    // =====================================================================
    // Preview balance tests
    // =====================================================================

    @Test
    fun `preview exercise shows current plus reward`() = runTest {
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // $5

        val preview = repository.previewExerciseBalance()
        assertEquals(1000L, preview) // $5 + $5
    }

    @Test
    fun `preview miss shows current halved`() = runTest {
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // $5

        val preview = repository.previewMissBalance()
        assertEquals(250L, preview) // $5 / 2
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    @Test
    fun `very long streak calculation is correct`() = runTest {
        val today = LocalDate.now()
        // Create 30 days of consecutive exercise
        repeat(30) { i ->
            repository.recordCheckIn(today.minusDays((29 - i).toLong()), didExercise = true)
        }

        val streak = repository.calculateStreak()
        assertEquals(30, streak)
    }

    @Test
    fun `balance halving rounds to the nearest cent`() = runTest {
        // Start with some odd balance through calculations
        repository.recordCheckIn(LocalDate.now().minusDays(3), didExercise = true)  // 500
        repository.recordCheckIn(LocalDate.now().minusDays(2), didExercise = false) // 250
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = false) // 125
        repository.recordCheckIn(LocalDate.now(), didExercise = false) // 125 / 2 = 62.5 → 63

        val balance = repository.currentBalance.first()
        assertEquals(63L, balance) // 62.5 cents rounds UP to 63 (round half up)
    }

    @Test
    fun `get check-in for date returns correct check-in`() = runTest {
        val targetDate = LocalDate.now().minusDays(5)
        repository.recordCheckIn(targetDate, didExercise = true)

        val checkIn = repository.getCheckInForDate(targetDate)
        assertNotNull(checkIn)
        assertEquals(targetDate, checkIn!!.date)
        assertTrue(checkIn.didExercise)
    }

    @Test
    fun `get check-in for non-existent date returns null`() = runTest {
        val checkIn = repository.getCheckInForDate(LocalDate.now().minusDays(100))
        assertNull(checkIn)
    }

    @Test
    fun `total workout count is accurate`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today.minusDays(4), didExercise = true)
        repository.recordCheckIn(today.minusDays(3), didExercise = false)
        repository.recordCheckIn(today.minusDays(2), didExercise = true)
        repository.recordCheckIn(today.minusDays(1), didExercise = true)
        repository.recordCheckIn(today, didExercise = false)

        val count = repository.getTotalWorkoutCount()
        assertEquals(3, count) // 3 exercise days
    }

    // =====================================================================
    // Bulk Check-In Tests
    // =====================================================================

    @Test
    fun `bulkRecordCheckIns with empty set does nothing`() = runTest {
        val initialBalance = repository.currentBalance.first()

        repository.bulkRecordCheckIns(emptySet(), didExercise = true)

        val balanceAfter = repository.currentBalance.first()
        assertEquals(initialBalance, balanceAfter)
    }

    @Test
    fun `bulkRecordCheckIns marks multiple new dates as exercised`() = runTest {
        val today = LocalDate.now()
        val dates = setOf(
            today.minusDays(3),
            today.minusDays(2),
            today.minusDays(1)
        )

        repository.bulkRecordCheckIns(dates, didExercise = true)

        // Should have 3 check-ins, all exercised, balance = $15
        val balance = repository.currentBalance.first()
        assertEquals(1500L, balance)
        assertEquals(3, repository.getTotalWorkoutCount())
    }

    @Test
    fun `bulkRecordCheckIns marks multiple new dates as missed`() = runTest {
        // First, create some balance
        repository.recordCheckIn(LocalDate.now().minusDays(5), didExercise = true) // $5

        val dates = setOf(
            LocalDate.now().minusDays(4),
            LocalDate.now().minusDays(3)
        )

        repository.bulkRecordCheckIns(dates, didExercise = false)

        // $5 -> $2.50 -> $1.25
        val balance = repository.currentBalance.first()
        assertEquals(125L, balance)
    }

    @Test
    fun `bulkRecordCheckIns updates existing check-ins`() = runTest {
        val today = LocalDate.now()
        val day1 = today.minusDays(2)
        val day2 = today.minusDays(1)

        // Create as missed
        repository.recordCheckIn(day1, didExercise = false) // $0
        repository.recordCheckIn(day2, didExercise = false) // $0

        // Bulk update to exercised
        repository.bulkRecordCheckIns(setOf(day1, day2), didExercise = true)

        // Now should be $10
        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `bulkRecordCheckIns recalculates subsequent balances`() = runTest {
        val today = LocalDate.now()

        // Day 1: exercise ($5)
        // Day 2: miss ($2.50)
        // Day 3: exercise ($7.50)
        repository.recordCheckIn(today.minusDays(3), didExercise = true)
        repository.recordCheckIn(today.minusDays(2), didExercise = false)
        repository.recordCheckIn(today.minusDays(1), didExercise = true)

        assertEquals(750L, repository.currentBalance.first())

        // Bulk update day 2 to exercised
        repository.bulkRecordCheckIns(setOf(today.minusDays(2)), didExercise = true)

        // Now: $5 -> $10 -> $15
        val balance = repository.currentBalance.first()
        assertEquals(1500L, balance)
    }

    @Test
    fun `bulkRecordCheckIns handles mix of new and existing dates`() = runTest {
        val today = LocalDate.now()
        val existingDate = today.minusDays(3)
        val newDate = today.minusDays(2)

        // Create one existing check-in
        repository.recordCheckIn(existingDate, didExercise = false) // $0

        // Bulk update both
        repository.bulkRecordCheckIns(setOf(existingDate, newDate), didExercise = true)

        // Both should now be exercise: $5 + $5 = $10
        val balance = repository.currentBalance.first()
        assertEquals(1000L, balance)

        // Both should be marked as exercised
        val checkIn1 = repository.getCheckInForDate(existingDate)
        val checkIn2 = repository.getCheckInForDate(newDate)
        assertTrue(checkIn1!!.didExercise)
        assertTrue(checkIn2!!.didExercise)
    }

    @Test
    fun `bulkRecordCheckIns preserves unchanged check-ins`() = runTest {
        val today = LocalDate.now()

        // Create check-ins for 3 days
        repository.recordCheckIn(today.minusDays(3), didExercise = true)  // $5
        repository.recordCheckIn(today.minusDays(2), didExercise = true)  // $10
        repository.recordCheckIn(today.minusDays(1), didExercise = true)  // $15

        // Only update day -2 to missed
        repository.bulkRecordCheckIns(setOf(today.minusDays(2)), didExercise = false)

        // Day -3: $5 (unchanged)
        // Day -2: $2.50 (was $10, now halved from $5)
        // Day -1: $7.50 (recalculated: $2.50 + $5)
        val balance = repository.currentBalance.first()
        assertEquals(750L, balance)

        // Verify day -3 is still exercised
        val checkIn = repository.getCheckInForDate(today.minusDays(3))
        assertTrue(checkIn!!.didExercise)
    }

    @Test
    fun `bulkRecordCheckIns with single date works`() = runTest {
        val date = LocalDate.now().minusDays(1)

        repository.bulkRecordCheckIns(setOf(date), didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(500L, balance)

        val checkIn = repository.getCheckInForDate(date)
        assertNotNull(checkIn)
        assertTrue(checkIn!!.didExercise)
    }

    @Test
    fun `bulkRecordCheckIns respects custom exercise reward`() = runTest {
        exerciseReward = 1000L // Custom reward ($10.00)

        val dates = setOf(
            LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1)
        )

        repository.bulkRecordCheckIns(dates, didExercise = true)

        // $10 + $10 = $20
        val balance = repository.currentBalance.first()
        assertEquals(2000L, balance)
    }
}
