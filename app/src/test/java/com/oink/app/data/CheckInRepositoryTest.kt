package com.oink.app.data

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
 * Uses fakes instead of mocks (per Android guidelines).
 */
class CheckInRepositoryTest {

    private lateinit var fakeDao: FakeCheckInDao
    private lateinit var repository: CheckInRepository

    // Simple fake for exercise reward - using SAM interface
    private var exerciseReward = 5.0
    private val fakeRewardProvider = ExerciseRewardProvider { exerciseReward }

    @Before
    fun setup() {
        fakeDao = FakeCheckInDao()
        repository = CheckInRepository(fakeDao, fakeRewardProvider)
        exerciseReward = 5.0 // Reset to default
    }

    // =====================================================================
    // Balance calculation tests
    // =====================================================================

    @Test
    fun `new user starts with zero balance`() = runTest {
        val balance = repository.currentBalance.first()
        assertEquals(0.0, balance, 0.001)
    }

    @Test
    fun `first exercise adds reward to starting balance`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today, didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(5.0, balance, 0.001)
    }

    @Test
    fun `first miss halves starting balance (stays at zero)`() = runTest {
        val today = LocalDate.now()
        repository.recordCheckIn(today, didExercise = false)

        val balance = repository.currentBalance.first()
        assertEquals(0.0, balance, 0.001) // 0 / 2 = 0
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
        assertEquals(15.0, balance, 0.001)
    }

    @Test
    fun `miss halves accumulated balance`() = runTest {
        val day1 = LocalDate.now().minusDays(1)
        val day2 = LocalDate.now()

        repository.recordCheckIn(day1, didExercise = true) // $5
        repository.recordCheckIn(day2, didExercise = false) // $2.50

        val balance = repository.currentBalance.first()
        assertEquals(2.5, balance, 0.001)
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
        assertEquals(7.5, balance, 0.001)
    }

    @Test
    fun `custom exercise reward is used`() = runTest {
        exerciseReward = 10.0
        val today = LocalDate.now()

        repository.recordCheckIn(today, didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(10.0, balance, 0.001)
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
        assertEquals(2.5, balance, 0.001)
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
        assertEquals(10.0, balance, 0.001)
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
        assertEquals(10.0, balance, 0.001)
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
        assertEquals(10.0, preview, 0.001) // $5 + $5
    }

    @Test
    fun `preview miss shows current halved`() = runTest {
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = true) // $5

        val preview = repository.previewMissBalance()
        assertEquals(2.5, preview, 0.001) // $5 / 2
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
    fun `balance rounding handles precision correctly`() = runTest {
        // Start with some odd balance through calculations
        repository.recordCheckIn(LocalDate.now().minusDays(3), didExercise = true)  // $5
        repository.recordCheckIn(LocalDate.now().minusDays(2), didExercise = false) // $2.50
        repository.recordCheckIn(LocalDate.now().minusDays(1), didExercise = false) // $1.25
        repository.recordCheckIn(LocalDate.now(), didExercise = false) // $0.625 → rounds to $0.63

        val balance = repository.currentBalance.first()
        assertEquals(0.63, balance, 0.001) // 0.625 rounds UP to 0.63 (standard rounding)
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
        assertEquals(initialBalance, balanceAfter, 0.001)
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
        assertEquals(15.0, balance, 0.001)
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
        assertEquals(1.25, balance, 0.001)
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
        assertEquals(10.0, balance, 0.001)
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

        assertEquals(7.5, repository.currentBalance.first(), 0.001)

        // Bulk update day 2 to exercised
        repository.bulkRecordCheckIns(setOf(today.minusDays(2)), didExercise = true)

        // Now: $5 -> $10 -> $15
        val balance = repository.currentBalance.first()
        assertEquals(15.0, balance, 0.001)
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
        assertEquals(10.0, balance, 0.001)

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
        assertEquals(7.5, balance, 0.001)

        // Verify day -3 is still exercised
        val checkIn = repository.getCheckInForDate(today.minusDays(3))
        assertTrue(checkIn!!.didExercise)
    }

    @Test
    fun `bulkRecordCheckIns with single date works`() = runTest {
        val date = LocalDate.now().minusDays(1)

        repository.bulkRecordCheckIns(setOf(date), didExercise = true)

        val balance = repository.currentBalance.first()
        assertEquals(5.0, balance, 0.001)

        val checkIn = repository.getCheckInForDate(date)
        assertNotNull(checkIn)
        assertTrue(checkIn!!.didExercise)
    }

    @Test
    fun `bulkRecordCheckIns respects custom exercise reward`() = runTest {
        exerciseReward = 10.0 // Custom reward

        val dates = setOf(
            LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1)
        )

        repository.bulkRecordCheckIns(dates, didExercise = true)

        // $10 + $10 = $20
        val balance = repository.currentBalance.first()
        assertEquals(20.0, balance, 0.001)
    }
}

