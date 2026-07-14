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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Unit tests for CheckInRepository.
 *
 * Tests the core business logic:
 * - Balance calculations (completed +$5, miss /2)
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

    // Simple fake for daily reward - using SAM interface. Habit-agnostic:
    // reports the same reward for whatever habit the repository asks about.
    private var dailyReward = 500L
    private val fakeRewardProvider = DailyRewardProvider { _ -> dailyReward }

    // Total deductions (cash-outs + freeze) in cents the fake reports for every
    // (habit, date). Zero by default, so a miss reduces to raw / 2 for the
    // majority of tests.
    private var deductions = 0L
    private val fakeDeductionProvider = DeductionProvider { _, _ -> deductions }

    // Pin "today" to a fixed instant so streak/freeze logic - which counts back
    // from the repository's today() - is deterministic instead of bound to the
    // real system clock. The tests below derive their dates from the same clock.
    private val fixedClock: Clock = Clock.fixed(
        LocalDate.of(2025, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant(),
        ZoneOffset.UTC
    )

    @Before
    fun setup() {
        fakeDao = FakeCheckInDao()
        repository = CheckInRepository(fakeDao, fakeRewardProvider, fakeDeductionProvider, fixedClock)
        dailyReward = 500L // Reset to default ($5.00)
        deductions = 0L // Reset to default
    }

    // =====================================================================
    // Balance calculation tests
    // =====================================================================

    @Test
    fun `new user starts with zero balance`() = runTest {
        val balance = repository.currentBalance().first()
        assertEquals(0L, balance)
    }

    @Test
    fun `first completed day adds reward to starting balance`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today, completed = true)

        val balance = repository.currentBalance().first()
        assertEquals(500L, balance)
    }

    @Test
    fun `first miss halves starting balance (stays at zero)`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today, completed = false)

        val balance = repository.currentBalance().first()
        assertEquals(0L, balance) // 0 / 2 = 0
    }

    @Test
    fun `consecutive completed days accumulate balance`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(2)
        val day2 = LocalDate.now(fixedClock).minusDays(1)
        val day3 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true) // $5
        repository.recordCheckIn(day2, completed = true) // $10
        repository.recordCheckIn(day3, completed = true) // $15

        val balance = repository.currentBalance().first()
        assertEquals(1500L, balance)
    }

    @Test
    fun `miss halves accumulated balance`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(1)
        val day2 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true) // $5
        repository.recordCheckIn(day2, completed = false) // $2.50

        val balance = repository.currentBalance().first()
        assertEquals(250L, balance)
    }

    @Test
    fun `completed day after miss adds to halved balance`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(2)
        val day2 = LocalDate.now(fixedClock).minusDays(1)
        val day3 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true)  // $5
        repository.recordCheckIn(day2, completed = false) // $2.50
        repository.recordCheckIn(day3, completed = true)  // $7.50

        val balance = repository.currentBalance().first()
        assertEquals(750L, balance)
    }

    @Test
    fun `custom daily reward is used`() = runTest {
        dailyReward = 1000L
        val today = LocalDate.now(fixedClock)

        repository.recordCheckIn(today, completed = true)

        val balance = repository.currentBalance().first()
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
        dailyReward = 10000L
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true) // raw $100

        deductions = 1000L // simulate a $10 cash-out already recorded
        repository.recordCheckIn(LocalDate.now(fixedClock), completed = false) // miss

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
        dailyReward = 10000L
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true) // raw $100

        deductions = 0L
        repository.recordCheckIn(LocalDate.now(fixedClock), completed = false) // miss

        val raw = repository.getCurrentBalanceOnce()
        assertEquals(5000L, raw) // $50.00
    }

    @Test
    fun `preview miss reflects halved spendable after a cash-out`() = runTest {
        dailyReward = 10000L
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true) // raw $100

        deductions = 1000L // $10 cashed out
        val rawPreview = repository.previewMissBalance()
        // MainViewModel subtracts current deductions from the raw preview.
        val spendablePreview = (rawPreview - deductions).coerceAtLeast(0L)

        assertEquals(4500L, spendablePreview) // $45.00
    }

    @Test
    fun `recalculating a past miss uses deductions in force`() = runTest {
        // Two completed days then a miss, with money cashed out throughout.
        dailyReward = 10000L
        val day1 = LocalDate.now(fixedClock).minusDays(2)
        val day2 = LocalDate.now(fixedClock).minusDays(1)
        val day3 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true) // raw $100
        repository.recordCheckIn(day2, completed = true) // raw $200

        deductions = 2000L // $20 cashed out; spendable now $180
        repository.recordCheckIn(day3, completed = false) // miss -> spendable $90

        // Retroactively flip day2 to a miss; day3's balance must recalculate.
        repository.recordCheckIn(day2, completed = false)

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
    fun `updating check-in from completed to miss recalculates balance`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(1)
        val day2 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true) // $5
        repository.recordCheckIn(day2, completed = true) // $10

        // Now change day2 to miss
        repository.recordCheckIn(day2, completed = false) // $5 / 2 = $2.50

        val balance = repository.currentBalance().first()
        assertEquals(250L, balance)
    }

    @Test
    fun `updating check-in from miss to completed recalculates balance`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(1)
        val day2 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true) // $5
        repository.recordCheckIn(day2, completed = false) // $2.50

        // Now change day2 to completed
        repository.recordCheckIn(day2, completed = true) // $5 + $5 = $10

        val balance = repository.currentBalance().first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `updating past check-in recalculates all subsequent balances`() = runTest {
        val day1 = LocalDate.now(fixedClock).minusDays(2)
        val day2 = LocalDate.now(fixedClock).minusDays(1)
        val day3 = LocalDate.now(fixedClock)

        repository.recordCheckIn(day1, completed = true)  // $5
        repository.recordCheckIn(day2, completed = true)  // $10
        repository.recordCheckIn(day3, completed = true)  // $15

        // Change day1 to miss - should cascade
        repository.recordCheckIn(day1, completed = false) // $0
        // day2: 0 + 5 = $5
        // day3: 5 + 5 = $10

        val balance = repository.currentBalance().first()
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
        dailyReward = 700L
        val date = LocalDate.now().minusDays(1)

        repository.recordCheckIn(date, completed = true)

        val checkIn = repository.getCheckInForDate(date)
        assertEquals(700L, checkIn!!.rewardAtTime)
    }

    @Test
    fun `recompute uses each day's stored reward, not today's`() = runTest {
        val day1 = LocalDate.now().minusDays(2)
        val day2 = LocalDate.now().minusDays(1)
        val day3 = LocalDate.now()

        // Record three completed days while the reward is $5.00.
        repository.recordCheckIn(day1, completed = true) // $5
        repository.recordCheckIn(day2, completed = true) // $10
        repository.recordCheckIn(day3, completed = true) // $15

        // User later bumps the reward to $10.00.
        dailyReward = 1000L

        // Editing the earliest day triggers a full recompute of day2 and day3.
        // Those days were earned at $5.00 and must NOT be re-valued at $10.00.
        repository.recordCheckIn(day1, completed = false) // $0

        // day1 miss: 0. day2: 0 + 5 = $5. day3: 5 + 5 = $10 (both at the OLD rate).
        val balance = repository.currentBalance().first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `toggling a past day keeps its original reward`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        dailyReward = 500L
        repository.recordCheckIn(day1, completed = true)  // $5 at $5.00 rate
        repository.recordCheckIn(day2, completed = false) // $2.50

        // Reward changes, then the user flips day2 from miss to completed.
        dailyReward = 2000L
        repository.recordCheckIn(day2, completed = true)

        // day2 must use its own recorded $5.00 reward: $5 + $5 = $10, not $25.
        val balance = repository.currentBalance().first()
        assertEquals(1000L, balance)
        assertEquals(500L, repository.getCheckInForDate(day2)!!.rewardAtTime)
    }

    // =====================================================================
    // Streak calculation tests
    // =====================================================================

    @Test
    fun `no check-ins means zero streak`() = runTest {
        val streak = repository.calculateStreak()
        assertEquals(0, streak)
    }

    // =====================================================================
    // Today check-in flow tests
    //
    // "Today" is resolved from the injected clock, not captured at
    // subscription time, so the flow reflects the clock's current date.
    // =====================================================================

    @Test
    fun `today check-in flow reflects a check-in dated today`() = runTest {
        repository.recordCheckIn(LocalDate.now(fixedClock), completed = true)

        val todayCheckIn = repository.getTodayCheckIn().first()
        assertNotNull(todayCheckIn)
        assertEquals(LocalDate.now(fixedClock), todayCheckIn!!.date)
        assertTrue(todayCheckIn.completed)
    }

    @Test
    fun `today check-in flow is null when only past days are logged`() = runTest {
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true)

        val todayCheckIn = repository.getTodayCheckIn().first()
        assertNull(todayCheckIn)
    }

    @Test
    fun `single completed day is streak of 1`() = runTest {
        repository.recordCheckIn(LocalDate.now(fixedClock), completed = true)
        val streak = repository.calculateStreak()
        assertEquals(1, streak)
    }

    @Test
    fun `consecutive completed days build streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today.minusDays(2), completed = true)
        repository.recordCheckIn(today.minusDays(1), completed = true)
        repository.recordCheckIn(today, completed = true)

        val streak = repository.calculateStreak()
        assertEquals(3, streak)
    }

    @Test
    fun `miss day breaks streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today.minusDays(2), completed = true)
        repository.recordCheckIn(today.minusDays(1), completed = false) // Streak broken!
        repository.recordCheckIn(today, completed = true)

        val streak = repository.calculateStreak()
        assertEquals(1, streak) // Only today counts
    }

    @Test
    fun `gap in dates breaks streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today.minusDays(3), completed = true)
        // No check-in for day -2 or -1
        repository.recordCheckIn(today, completed = true)

        val streak = repository.calculateStreak()
        assertEquals(1, streak) // Only today counts
    }

    @Test
    fun `frozen date does not break streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        val frozenDate = today.minusDays(1)

        repository.recordCheckIn(today.minusDays(2), completed = true)
        // Day -1 is frozen (no check-in needed)
        repository.recordCheckIn(today, completed = true)

        val streak = repository.calculateStreak(frozenDates = setOf(frozenDate))
        assertEquals(2, streak) // Frozen day doesn't break, but doesn't add either
    }

    @Test
    fun `frozen miss does not break streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        val frozenDate = today.minusDays(1)

        repository.recordCheckIn(today.minusDays(2), completed = true)
        repository.recordCheckIn(today.minusDays(1), completed = false) // Miss but frozen!
        repository.recordCheckIn(today, completed = true)

        val streak = repository.calculateStreak(frozenDates = setOf(frozenDate))
        assertEquals(2, streak)
    }

    @Test
    fun `today not checked in yet continues yesterday streak`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today.minusDays(2), completed = true)
        repository.recordCheckIn(today.minusDays(1), completed = true)
        // Today not checked in yet

        val streak = repository.calculateStreak()
        assertEquals(2, streak)
    }

    // =====================================================================
    // Freeze-candidate search (findMissedDayForFreeze)
    //
    // A day is only a valid freeze candidate if the habit was already active on
    // or before it: the search is floored at the earliest check-in date. A habit
    // can't miss a day before it had any activity, and a brand-new habit (or one
    // that has only checked in today) has no streak to protect.
    // =====================================================================

    @Test
    fun `no check-ins offers no freeze candidate`() = runTest {
        assertNull(repository.findMissedDayForFreeze(emptyList()))
    }

    @Test
    fun `a habit that only checked in today offers no freeze candidate`() = runTest {
        // The reported bug: a brand-new habit checked in today. Yesterday is a
        // gap, but the habit did not exist to miss it, so there is nothing to
        // freeze.
        val today = LocalDate.now(fixedClock)
        val checkIns = listOf(
            CheckIn(id = 1L, date = today, completed = true, balanceAfter = 500L)
        )

        assertNull(repository.findMissedDayForFreeze(checkIns))
    }

    @Test
    fun `a gap before the first check-in is not a freeze candidate`() = runTest {
        // Earliest activity was yesterday (a completed day). Two days ago is a
        // gap, but it predates the habit, so it is not offered.
        val today = LocalDate.now(fixedClock)
        val checkIns = listOf(
            CheckIn(id = 1L, date = today.minusDays(1), completed = true, balanceAfter = 500L)
        )

        assertNull(repository.findMissedDayForFreeze(checkIns))
    }

    @Test
    fun `a genuine interior gap after activity is a freeze candidate`() = runTest {
        // Active two days ago, then yesterday is a gap (no check-in) - a real
        // missed day the user can freeze.
        val today = LocalDate.now(fixedClock)
        val checkIns = listOf(
            CheckIn(id = 1L, date = today.minusDays(2), completed = true, balanceAfter = 500L)
        )

        assertEquals(today.minusDays(1), repository.findMissedDayForFreeze(checkIns))
    }

    @Test
    fun `a logged rest day after activity is a freeze candidate`() = runTest {
        // Checked in day 1, logged a miss yesterday: yesterday can be frozen.
        val today = LocalDate.now(fixedClock)
        val checkIns = listOf(
            CheckIn(id = 1L, date = today.minusDays(1), completed = false, balanceAfter = 250L),
            CheckIn(id = 2L, date = today.minusDays(2), completed = true, balanceAfter = 500L)
        )

        assertEquals(today.minusDays(1), repository.findMissedDayForFreeze(checkIns))
    }

    @Test
    fun `a migrated habit's interior gap is found even though createdAt is today`() = runTest {
        // A migrated habit's createdAt is the migration timestamp (today), but its
        // check-ins predate it. Bounding on the earliest CHECK-IN (not createdAt)
        // means a real interior gap is still found. Earliest check-in is 4 days
        // ago; yesterday is a gap.
        val today = LocalDate.now(fixedClock)
        val checkIns = listOf(
            CheckIn(id = 1L, date = today.minusDays(4), completed = true, balanceAfter = 500L),
            CheckIn(id = 2L, date = today.minusDays(3), completed = true, balanceAfter = 1000L),
            CheckIn(id = 3L, date = today.minusDays(2), completed = true, balanceAfter = 1500L)
            // yesterday: gap
        )

        assertEquals(today.minusDays(1), repository.findMissedDayForFreeze(checkIns))
    }

    // =====================================================================
    // Preview balance tests
    // =====================================================================

    @Test
    fun `preview completed shows current plus reward`() = runTest {
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true) // $5

        val preview = repository.previewCompletedBalance()
        assertEquals(1000L, preview) // $5 + $5
    }

    @Test
    fun `preview miss shows current halved`() = runTest {
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true) // $5

        val preview = repository.previewMissBalance()
        assertEquals(250L, preview) // $5 / 2
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    @Test
    fun `very long streak calculation is correct`() = runTest {
        val today = LocalDate.now(fixedClock)
        // Create 30 consecutive completed days
        repeat(30) { i ->
            repository.recordCheckIn(today.minusDays((29 - i).toLong()), completed = true)
        }

        val streak = repository.calculateStreak()
        assertEquals(30, streak)
    }

    @Test
    fun `balance halving rounds to the nearest cent`() = runTest {
        // Start with some odd balance through calculations
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(3), completed = true)  // 500
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(2), completed = false) // 250
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = false) // 125
        repository.recordCheckIn(LocalDate.now(fixedClock), completed = false) // 125 / 2 = 62.5 → 63

        val balance = repository.currentBalance().first()
        assertEquals(63L, balance) // 62.5 cents rounds UP to 63 (round half up)
    }

    @Test
    fun `get check-in for date returns correct check-in`() = runTest {
        val targetDate = LocalDate.now(fixedClock).minusDays(5)
        repository.recordCheckIn(targetDate, completed = true)

        val checkIn = repository.getCheckInForDate(targetDate)
        assertNotNull(checkIn)
        assertEquals(targetDate, checkIn!!.date)
        assertTrue(checkIn.completed)
    }

    @Test
    fun `get check-in for non-existent date returns null`() = runTest {
        val checkIn = repository.getCheckInForDate(LocalDate.now(fixedClock).minusDays(100))
        assertNull(checkIn)
    }

    @Test
    fun `total completed-day count is accurate`() = runTest {
        val today = LocalDate.now(fixedClock)
        repository.recordCheckIn(today.minusDays(4), completed = true)
        repository.recordCheckIn(today.minusDays(3), completed = false)
        repository.recordCheckIn(today.minusDays(2), completed = true)
        repository.recordCheckIn(today.minusDays(1), completed = true)
        repository.recordCheckIn(today, completed = false)

        val count = repository.getCompletedDayCount()
        assertEquals(3, count) // 3 completed days
    }

    // =====================================================================
    // Bulk Check-In Tests
    // =====================================================================

    @Test
    fun `bulkRecordCheckIns with empty set does nothing`() = runTest {
        val initialBalance = repository.currentBalance().first()

        repository.bulkRecordCheckIns(emptySet(), completed = true)

        val balanceAfter = repository.currentBalance().first()
        assertEquals(initialBalance, balanceAfter)
    }

    @Test
    fun `bulkRecordCheckIns marks multiple new dates as completed`() = runTest {
        val today = LocalDate.now(fixedClock)
        val dates = setOf(
            today.minusDays(3),
            today.minusDays(2),
            today.minusDays(1)
        )

        repository.bulkRecordCheckIns(dates, completed = true)

        // Should have 3 check-ins, all completed, balance = $15
        val balance = repository.currentBalance().first()
        assertEquals(1500L, balance)
        assertEquals(3, repository.getCompletedDayCount())
    }

    @Test
    fun `bulkRecordCheckIns marks multiple new dates as missed`() = runTest {
        // First, create some balance
        repository.recordCheckIn(LocalDate.now(fixedClock).minusDays(5), completed = true) // $5

        val dates = setOf(
            LocalDate.now(fixedClock).minusDays(4),
            LocalDate.now(fixedClock).minusDays(3)
        )

        repository.bulkRecordCheckIns(dates, completed = false)

        // $5 -> $2.50 -> $1.25
        val balance = repository.currentBalance().first()
        assertEquals(125L, balance)
    }

    @Test
    fun `bulkRecordCheckIns updates existing check-ins`() = runTest {
        val today = LocalDate.now(fixedClock)
        val day1 = today.minusDays(2)
        val day2 = today.minusDays(1)

        // Create as missed
        repository.recordCheckIn(day1, completed = false) // $0
        repository.recordCheckIn(day2, completed = false) // $0

        // Bulk update to completed
        repository.bulkRecordCheckIns(setOf(day1, day2), completed = true)

        // Now should be $10
        val balance = repository.currentBalance().first()
        assertEquals(1000L, balance)
    }

    @Test
    fun `bulkRecordCheckIns recalculates subsequent balances`() = runTest {
        val today = LocalDate.now(fixedClock)

        // Day 1: completed ($5)
        // Day 2: miss ($2.50)
        // Day 3: completed ($7.50)
        repository.recordCheckIn(today.minusDays(3), completed = true)
        repository.recordCheckIn(today.minusDays(2), completed = false)
        repository.recordCheckIn(today.minusDays(1), completed = true)

        assertEquals(750L, repository.currentBalance().first())

        // Bulk update day 2 to completed
        repository.bulkRecordCheckIns(setOf(today.minusDays(2)), completed = true)

        // Now: $5 -> $10 -> $15
        val balance = repository.currentBalance().first()
        assertEquals(1500L, balance)
    }

    @Test
    fun `bulkRecordCheckIns handles mix of new and existing dates`() = runTest {
        val today = LocalDate.now(fixedClock)
        val existingDate = today.minusDays(3)
        val newDate = today.minusDays(2)

        // Create one existing check-in
        repository.recordCheckIn(existingDate, completed = false) // $0

        // Bulk update both
        repository.bulkRecordCheckIns(setOf(existingDate, newDate), completed = true)

        // Both should now be completed: $5 + $5 = $10
        val balance = repository.currentBalance().first()
        assertEquals(1000L, balance)

        // Both should be marked as completed
        val checkIn1 = repository.getCheckInForDate(existingDate)
        val checkIn2 = repository.getCheckInForDate(newDate)
        assertTrue(checkIn1!!.completed)
        assertTrue(checkIn2!!.completed)
    }

    @Test
    fun `bulkRecordCheckIns preserves unchanged check-ins`() = runTest {
        val today = LocalDate.now(fixedClock)

        // Create check-ins for 3 days
        repository.recordCheckIn(today.minusDays(3), completed = true)  // $5
        repository.recordCheckIn(today.minusDays(2), completed = true)  // $10
        repository.recordCheckIn(today.minusDays(1), completed = true)  // $15

        // Only update day -2 to missed
        repository.bulkRecordCheckIns(setOf(today.minusDays(2)), completed = false)

        // Day -3: $5 (unchanged)
        // Day -2: $2.50 (was $10, now halved from $5)
        // Day -1: $7.50 (recalculated: $2.50 + $5)
        val balance = repository.currentBalance().first()
        assertEquals(750L, balance)

        // Verify day -3 is still completed
        val checkIn = repository.getCheckInForDate(today.minusDays(3))
        assertTrue(checkIn!!.completed)
    }

    @Test
    fun `bulkRecordCheckIns with single date works`() = runTest {
        val date = LocalDate.now(fixedClock).minusDays(1)

        repository.bulkRecordCheckIns(setOf(date), completed = true)

        val balance = repository.currentBalance().first()
        assertEquals(500L, balance)

        val checkIn = repository.getCheckInForDate(date)
        assertNotNull(checkIn)
        assertTrue(checkIn!!.completed)
    }

    @Test
    fun `bulkRecordCheckIns respects custom daily reward`() = runTest {
        dailyReward = 1000L // Custom reward ($10.00)

        val dates = setOf(
            LocalDate.now(fixedClock).minusDays(2),
            LocalDate.now(fixedClock).minusDays(1)
        )

        repository.bulkRecordCheckIns(dates, completed = true)

        // $10 + $10 = $20
        val balance = repository.currentBalance().first()
        assertEquals(2000L, balance)
    }

    // =====================================================================
    // Per-habit scoping (issue #34)
    //
    // The repository serves all habits; each habitId owns its own check-in per
    // date, its own streak, and its own halving. These prove two habits never
    // collide and that halving reads only the target habit's deductions.
    // =====================================================================

    @Test
    fun `two habits check in on the same date without colliding`() = runTest {
        val today = LocalDate.now(fixedClock)

        repository.recordCheckIn(today, completed = true, habitId = 1L)
        repository.recordCheckIn(today, completed = false, habitId = 2L)

        val habit1 = repository.getCheckInForDate(today, habitId = 1L)
        val habit2 = repository.getCheckInForDate(today, habitId = 2L)
        assertNotNull(habit1)
        assertNotNull(habit2)
        assertTrue(habit1!!.completed)
        assertEquals(false, habit2!!.completed)
        // Independent balances: habit 1 earned a reward, habit 2 halved from zero.
        assertEquals(500L, repository.currentBalance(habitId = 1L).first())
        assertEquals(0L, repository.currentBalance(habitId = 2L).first())
    }

    @Test
    fun `each habit tracks its own streak independently`() = runTest {
        val today = LocalDate.now(fixedClock)

        // Habit 1: an unbroken two-day streak.
        repository.recordCheckIn(today.minusDays(1), completed = true, habitId = 1L)
        repository.recordCheckIn(today, completed = true, habitId = 1L)
        // Habit 2: a two-day run then a miss today breaks it.
        repository.recordCheckIn(today.minusDays(1), completed = true, habitId = 2L)
        repository.recordCheckIn(today, completed = false, habitId = 2L)

        assertEquals(2, repository.calculateStreak(habitId = 1L))
        assertEquals(0, repository.calculateStreak(habitId = 2L))
    }

    // =====================================================================
    // Halving with real per-habit deductions (allocations + freeze)
    //
    // Wires a real DefaultDeductionProvider so the miss halving reads the
    // habit's cash-out allocation shares, not a stub - the production path.
    // =====================================================================

    @Test
    fun `miss halving accounts for the habit's cash-out allocations`() = runTest {
        // Habit 1 raw $100, with a $10 cash-out allocated to it -> spendable $90.
        val repo = repoWithAllocation(reward = 10000L, allocationHabitId = 1L, allocationAmount = 1000L)

        repo.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true, habitId = 1L)
        repo.recordCheckIn(LocalDate.now(fixedClock), completed = false, habitId = 1L)

        val raw = repo.getCurrentBalanceOnce(habitId = 1L)
        assertEquals(5500L, raw) // raw' = (10000 + 1000) / 2
        val spendable = BalanceCalculator.calculateActualBalance(raw, 1000L, 0L)
        assertEquals(4500L, spendable) // half of the pre-miss $90
    }

    @Test
    fun `miss halving ignores another habit's allocations`() = runTest {
        // The $10 allocation belongs to habit 2, so habit 1's miss sees no
        // deduction and simply halves its raw balance.
        val repo = repoWithAllocation(reward = 10000L, allocationHabitId = 2L, allocationAmount = 1000L)

        repo.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true, habitId = 1L)
        repo.recordCheckIn(LocalDate.now(fixedClock), completed = false, habitId = 1L)

        assertEquals(5000L, repo.getCurrentBalanceOnce(habitId = 1L)) // 10000 / 2
    }

    @Test
    fun `miss halving rounds half up with an allocation present`() = runTest {
        // raw 100, deduction 1 -> (100 + 1) / 2 = 50.5, which rounds up to 51.
        val repo = repoWithAllocation(reward = 100L, allocationHabitId = 1L, allocationAmount = 1L)

        repo.recordCheckIn(LocalDate.now(fixedClock).minusDays(1), completed = true, habitId = 1L)
        repo.recordCheckIn(LocalDate.now(fixedClock), completed = false, habitId = 1L)

        assertEquals(51L, repo.getCurrentBalanceOnce(habitId = 1L))
    }

    /**
     * Build a repository whose deductions come from a real
     * [DefaultDeductionProvider]: one cash-out (dated 10 days before today, so it
     * counts for any recent check-in) fully allocated to [allocationHabitId].
     */
    private fun repoWithAllocation(
        reward: Long,
        allocationHabitId: Long,
        allocationAmount: Long
    ): CheckInRepository {
        val cashOutDao = FakeCashOutDao()
        val allocationDao = FakeCashOutAllocationDao()
        val habitDao = FakeHabitDao().apply {
            seed(Habit(id = 1L, name = "Workout"), Habit(id = 2L, name = "Read"))
        }
        val freezeRepository = FreezeRepository(habitDao, FakeFrozenDayDao())

        val cashOutDate = LocalDate.now(fixedClock).minusDays(10)
        val cashedOutAt = cashOutDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        cashOutDao.setCashOuts(
            listOf(
                CashOut(
                    id = 1L,
                    name = "Reward",
                    amount = allocationAmount,
                    cashedOutAt = cashedOutAt,
                    balanceBefore = 0,
                    balanceAfter = 0
                )
            )
        )
        allocationDao.seed(
            CashOutAllocation(
                id = 1L,
                cashOutId = 1L,
                habitId = allocationHabitId,
                amount = allocationAmount,
                rewardAtTime = 500L
            )
        )

        return CheckInRepository(
            FakeCheckInDao(),
            DailyRewardProvider { _ -> reward },
            DefaultDeductionProvider(cashOutDao, allocationDao, freezeRepository, ZoneOffset.UTC),
            fixedClock
        )
    }
}
