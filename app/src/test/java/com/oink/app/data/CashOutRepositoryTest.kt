package com.oink.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for CashOutRepository.
 *
 * Tests the reward/cash-out business logic:
 * - Creating cash-outs
 * - Updating cash-outs (edit amount, name, emoji)
 * - Deleting cash-outs (money returns to balance)
 *
 * Uses fakes instead of mocks (per Android guidelines).
 */
class CashOutRepositoryTest {

    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var repository: CashOutRepository

    @Before
    fun setup() {
        fakeCashOutDao = FakeCashOutDao()
        fakeCheckInDao = FakeCheckInDao()
        fakePreferencesRepository = FakePreferencesRepository()
        checkInRepository = CheckInRepository(fakeCheckInDao, fakePreferencesRepository)
        repository = CashOutRepository(fakeCashOutDao, checkInRepository, fakePreferencesRepository)
    }

    // =====================================================================
    // Cash Out Creation Tests
    // =====================================================================

    @Test
    fun `cashOut with sufficient balance should succeed`() = runTest {
        // Setup: User has $50 in check-in balance
        setupBalance(50.0)

        // Act
        val result = repository.cashOut("New Darts", 25.0, "🎯")

        // Assert
        assertNotNull(result)
        assertEquals("New Darts", result!!.name)
        assertEquals(25.0, result.amount, 0.001)
        assertEquals("🎯", result.emoji)
        assertEquals(50.0, result.balanceBefore, 0.001)
        assertEquals(25.0, result.balanceAfter, 0.001)
    }

    @Test
    fun `cashOut with insufficient balance should return null`() = runTest {
        // Setup: User has $20
        setupBalance(20.0)

        // Act: Try to cash out $50
        val result = repository.cashOut("Expensive Thing", 50.0, "💸")

        // Assert
        assertNull(result)
    }

    @Test
    fun `cashOut with zero amount should return null`() = runTest {
        setupBalance(50.0)
        val result = repository.cashOut("Nothing", 0.0, "🎁")
        assertNull(result)
    }

    @Test
    fun `cashOut with negative amount should return null`() = runTest {
        setupBalance(50.0)
        val result = repository.cashOut("Negative", -10.0, "🎁")
        assertNull(result)
    }

    @Test
    fun `multiple cashOuts should accumulate totalCashedOut`() = runTest {
        // Setup: User has $100
        setupBalance(100.0)

        // Act: Cash out twice
        repository.cashOut("First", 30.0, "1️⃣")
        repository.cashOut("Second", 20.0, "2️⃣")

        // Assert
        assertEquals(50.0, repository.getTotalCashedOut(), 0.001)
        assertEquals(2, repository.getRewardCount())
    }

    // =====================================================================
    // Update Cash Out Tests
    // =====================================================================

    @Test
    fun `updateCashOut should update name and emoji`() = runTest {
        setupBalance(50.0)
        val original = repository.cashOut("Old Name", 20.0, "🎁")!!

        // Act: Update name and emoji
        val updated = original.copy(name = "New Name", emoji = "🎯")
        val success = repository.updateCashOut(updated)

        // Assert
        assertTrue(success)
        val retrieved = repository.getCashOutById(original.id)
        assertEquals("New Name", retrieved?.name)
        assertEquals("🎯", retrieved?.emoji)
    }

    @Test
    fun `updateCashOut decreasing amount should succeed`() = runTest {
        // Setup: $50 balance, cash out $30 (leaves $20 actual balance)
        setupBalance(50.0)
        val original = repository.cashOut("Reward", 30.0, "🎁")!!

        // Act: Reduce cash-out to $10 (should add $20 back to balance)
        val updated = original.copy(amount = 10.0)
        val success = repository.updateCashOut(updated)

        // Assert
        assertTrue(success)
        assertEquals(10.0, repository.getTotalCashedOut(), 0.001)
    }

    @Test
    fun `updateCashOut increasing amount within balance should succeed`() = runTest {
        // Setup: $100 balance, cash out $20 (leaves $80 actual balance)
        setupBalance(100.0)
        val original = repository.cashOut("Reward", 20.0, "🎁")!!

        // Act: Increase cash-out to $50 (uses $30 more of balance)
        val updated = original.copy(amount = 50.0)
        val success = repository.updateCashOut(updated)

        // Assert
        assertTrue(success)
        assertEquals(50.0, repository.getTotalCashedOut(), 0.001)
    }

    @Test
    fun `updateCashOut for non-existent id should fail`() = runTest {
        val fakeCashOut = CashOut(
            id = 999L,
            name = "Fake",
            amount = 10.0,
            emoji = "🎁",
            balanceBefore = 100.0,
            balanceAfter = 90.0
        )

        val success = repository.updateCashOut(fakeCashOut)

        assertFalse(success)
    }

    // =====================================================================
    // Delete Cash Out Tests
    // =====================================================================

    @Test
    fun `deleteCashOut should remove the record`() = runTest {
        setupBalance(50.0)
        val cashOut = repository.cashOut("To Delete", 20.0, "🗑️")!!

        // Act
        val success = repository.deleteCashOut(cashOut)

        // Assert
        assertTrue(success)
        assertNull(repository.getCashOutById(cashOut.id))
        assertEquals(0, repository.getRewardCount())
    }

    @Test
    fun `deleteCashOut should return money to balance`() = runTest {
        // Setup: $100 balance, cash out $30 (leaves $70)
        setupBalance(100.0)
        val cashOut = repository.cashOut("Deleted Reward", 30.0, "🎁")!!

        // Verify balance is reduced
        assertEquals(30.0, repository.getTotalCashedOut(), 0.001)

        // Act: Delete the cash-out
        repository.deleteCashOut(cashOut)

        // Assert: Total cashed out is now 0 (money "returned")
        assertEquals(0.0, repository.getTotalCashedOut(), 0.001)
    }

    @Test
    fun `deleteCashOutById should work`() = runTest {
        setupBalance(50.0)
        val cashOut = repository.cashOut("To Delete", 20.0, "🗑️")!!

        val success = repository.deleteCashOutById(cashOut.id)

        assertTrue(success)
        assertNull(repository.getCashOutById(cashOut.id))
    }

    @Test
    fun `deleteCashOutById for non-existent id should fail`() = runTest {
        val success = repository.deleteCashOutById(999L)
        assertFalse(success)
    }

    @Test
    fun `delete one of multiple cashOuts should only remove that one`() = runTest {
        setupBalance(100.0)
        val first = repository.cashOut("First", 20.0, "1️⃣")!!
        val second = repository.cashOut("Second", 30.0, "2️⃣")!!

        // Delete first one
        repository.deleteCashOut(first)

        // Assert: Only second remains
        assertEquals(1, repository.getRewardCount())
        assertNull(repository.getCashOutById(first.id))
        assertNotNull(repository.getCashOutById(second.id))
        assertEquals(30.0, repository.getTotalCashedOut(), 0.001)
    }

    // =====================================================================
    // Workout Count Tests
    // =====================================================================

    @Test
    fun `getTotalWorkoutsRewarded should sum workouts from all cashOuts`() = runTest {
        // Setup: $5 per workout reward
        fakePreferencesRepository.setExerciseReward(5.0)
        setupBalance(100.0)

        // Cash out $25 (5 workouts worth) and $15 (3 workouts worth)
        repository.cashOut("First", 25.0, "🎁")
        repository.cashOut("Second", 15.0, "🎁")

        // Assert
        assertEquals(8, repository.getTotalWorkoutsRewarded())
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    /**
     * Set up a check-in balance for testing.
     * Creates a single check-in with the specified balance.
     */
    private suspend fun setupBalance(balance: Double) {
        // We need to set up check-ins to establish the balance
        // A simple way: create a check-in with the desired balanceAfter
        val checkIn = CheckIn(
            id = 1L,
            date = LocalDate.now().minusDays(1),
            didExercise = true,
            balanceAfter = balance
        )
        fakeCheckInDao.setCheckIns(listOf(checkIn))
    }
}

