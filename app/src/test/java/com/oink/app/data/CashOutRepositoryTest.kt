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
 * All money values are in cents (Long), e.g. $50.00 is 5000.
 *
 * Uses fakes instead of mocks (per Android guidelines).
 */
class CashOutRepositoryTest {

    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCashOutAllocationDao: FakeCashOutAllocationDao
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var habitRepository: HabitRepository
    private lateinit var freezeRepository: FreezeRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var repository: CashOutRepository

    @Before
    fun setup() {
        fakeCashOutDao = FakeCashOutDao()
        fakeCashOutAllocationDao = FakeCashOutAllocationDao()
        fakeCheckInDao = FakeCheckInDao()
        fakeHabitDao = FakeHabitDao().apply {
            seed(Habit(id = HabitRepository.DEFAULT_HABIT_ID, name = "Workout"))
        }
        habitRepository = HabitRepository(fakeHabitDao)
        freezeRepository = FreezeRepository(fakeHabitDao, FakeFrozenDayDao())
        checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(fakeCashOutDao, fakeCashOutAllocationDao, freezeRepository)
        )
        repository = CashOutRepository(
            fakeCashOutDao,
            fakeCashOutAllocationDao,
            checkInRepository,
            habitRepository,
            freezeRepository,
            FakeTransactionRunner()
        )
    }

    // =====================================================================
    // Cash Out Creation Tests
    // =====================================================================

    @Test
    fun `cashOut with sufficient balance should succeed`() = runTest {
        // Setup: User has $50 in check-in balance
        setupBalance(5000)

        // Act
        val result = repository.cashOut("New Darts", 2500, "🎯")

        // Assert
        assertNotNull(result)
        assertEquals("New Darts", result!!.name)
        assertEquals(2500L, result.amount)
        assertEquals("🎯", result.emoji)
        assertEquals(5000L, result.balanceBefore)
        assertEquals(2500L, result.balanceAfter)
    }

    @Test
    fun `cashOut with insufficient balance should return null`() = runTest {
        // Setup: User has $20
        setupBalance(2000)

        // Act: Try to cash out $50
        val result = repository.cashOut("Expensive Thing", 5000, "💸")

        // Assert
        assertNull(result)
    }

    @Test
    fun `cashOut with zero amount should return null`() = runTest {
        setupBalance(5000)
        val result = repository.cashOut("Nothing", 0, "🎁")
        assertNull(result)
    }

    @Test
    fun `cashOut with negative amount should return null`() = runTest {
        setupBalance(5000)
        val result = repository.cashOut("Negative", -1000, "🎁")
        assertNull(result)
    }

    @Test
    fun `multiple cashOuts should accumulate totalCashedOut`() = runTest {
        // Setup: User has $100
        setupBalance(10000)

        // Act: Cash out twice
        repository.cashOut("First", 3000, "1️⃣")
        repository.cashOut("Second", 2000, "2️⃣")

        // Assert
        assertEquals(5000L, repository.getTotalCashedOut())
        assertEquals(2, repository.getRewardCount())
    }

    // =====================================================================
    // Update Cash Out Tests
    // =====================================================================

    @Test
    fun `updateCashOut should update name and emoji`() = runTest {
        setupBalance(5000)
        val original = repository.cashOut("Old Name", 2000, "🎁")!!

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
        setupBalance(5000)
        val original = repository.cashOut("Reward", 3000, "🎁")!!

        // Act: Reduce cash-out to $10 (should add $20 back to balance)
        val updated = original.copy(amount = 1000)
        val success = repository.updateCashOut(updated)

        // Assert
        assertTrue(success)
        assertEquals(1000L, repository.getTotalCashedOut())
    }

    @Test
    fun `updateCashOut increasing amount within balance should succeed`() = runTest {
        // Setup: $100 balance, cash out $20 (leaves $80 actual balance)
        setupBalance(10000)
        val original = repository.cashOut("Reward", 2000, "🎁")!!

        // Act: Increase cash-out to $50 (uses $30 more of balance)
        val updated = original.copy(amount = 5000)
        val success = repository.updateCashOut(updated)

        // Assert
        assertTrue(success)
        assertEquals(5000L, repository.getTotalCashedOut())
    }

    @Test
    fun `updateCashOut for non-existent id should fail`() = runTest {
        val fakeCashOut = CashOut(
            id = 999L,
            name = "Fake",
            amount = 1000,
            emoji = "🎁",
            balanceBefore = 10000,
            balanceAfter = 9000
        )

        val success = repository.updateCashOut(fakeCashOut)

        assertFalse(success)
    }

    // =====================================================================
    // Delete Cash Out Tests
    // =====================================================================

    @Test
    fun `deleteCashOut should remove the record`() = runTest {
        setupBalance(5000)
        val cashOut = repository.cashOut("To Delete", 2000, "🗑️")!!

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
        setupBalance(10000)
        val cashOut = repository.cashOut("Deleted Reward", 3000, "🎁")!!

        // Verify balance is reduced
        assertEquals(3000L, repository.getTotalCashedOut())

        // Act: Delete the cash-out
        repository.deleteCashOut(cashOut)

        // Assert: Total cashed out is now 0 (money "returned")
        assertEquals(0L, repository.getTotalCashedOut())
    }

    @Test
    fun `deleteCashOutById should work`() = runTest {
        setupBalance(5000)
        val cashOut = repository.cashOut("To Delete", 2000, "🗑️")!!

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
        setupBalance(10000)
        val first = repository.cashOut("First", 2000, "1️⃣")!!
        val second = repository.cashOut("Second", 3000, "2️⃣")!!

        // Delete first one
        repository.deleteCashOut(first)

        // Assert: Only second remains
        assertEquals(1, repository.getRewardCount())
        assertNull(repository.getCashOutById(first.id))
        assertNotNull(repository.getCashOutById(second.id))
        assertEquals(3000L, repository.getTotalCashedOut())
    }

    // =====================================================================
    // Workout Count Tests
    // =====================================================================

    @Test
    fun `getTotalWorkoutsRewarded should sum workouts from all cashOuts`() = runTest {
        // Workouts-to-earn divides each allocation by the habit's captured reward.
        setupBalance(10000)

        // Cash out $25 (5 workouts worth) and $15 (3 workouts worth)
        repository.cashOut("First", 2500, "🎁")
        repository.cashOut("Second", 1500, "🎁")

        // Assert
        assertEquals(8, repository.getTotalWorkoutsRewarded())
    }

    @Test
    fun `getTotalWorkoutsRewarded divides each allocation by its captured reward`() = runTest {
        // Allocations captured at different reward rates each count on their own
        // rate; a non-positive captured reward contributes zero, not a crash.
        fakeCashOutAllocationDao.seed(
            CashOutAllocation(id = 1, cashOutId = 1, habitId = 1, amount = 2000, exerciseRewardAtTime = 500),
            CashOutAllocation(id = 2, cashOutId = 2, habitId = 2, amount = 3000, exerciseRewardAtTime = 1000),
            CashOutAllocation(id = 3, cashOutId = 3, habitId = 3, amount = 1000, exerciseRewardAtTime = 0)
        )

        // 2000/500 = 4, 3000/1000 = 3, 1000/0 guarded to 0 → total 7.
        assertEquals(7, repository.getTotalWorkoutsRewarded())
    }

    // =====================================================================
    // Waterfall (shared-pot) Tests
    // =====================================================================

    @Test
    fun `waterfall drains highest-first with the last habit taking the remainder`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 3000L, 2L to 1000L)

        val result = repository.cashOut("Treat", 3500)!!

        // Highest balance (habit 1) drains fully; habit 2 covers the remainder.
        assertEquals(3000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(500L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        // Allocations sum to the claim exactly; pot drops by exactly the claim.
        assertEquals(3500L, allocationsFor(result.id).sumOf { it.amount })
        assertEquals(4000L, result.balanceBefore)
        assertEquals(500L, result.balanceAfter)
        assertEquals(3500L, result.balanceBefore - result.balanceAfter)
    }

    @Test
    fun `waterfall breaks equal spendable ties by sortOrder then id`() = runTest {
        // Three habits with equal spendable. Ordering must be sortOrder ASC then
        // id ASC: habit 2 (so=1) and habit 3 (so=1) precede habit 1 (so=2), and
        // among the tie habit 2 precedes habit 3 by id.
        seedHabits(
            HabitSeed(id = 1, sortOrder = 2),
            HabitSeed(id = 2, sortOrder = 1),
            HabitSeed(id = 3, sortOrder = 1)
        )
        seedRawBalances(1L to 1000L, 2L to 1000L, 3L to 1000L)

        val result = repository.cashOut("Treat", 1500)!!

        // habit 2 drains fully, habit 3 covers the remainder, habit 1 untouched.
        assertEquals(1000L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        assertEquals(500L, fakeCashOutAllocationDao.getTotalForHabit(3L))
        assertEquals(0L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(1500L, allocationsFor(result.id).sumOf { it.amount })
    }

    @Test
    fun `waterfall for amount equal to pot drains everything to zero`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 2000L, 2L to 3000L)

        val result = repository.cashOut("All of it", 5000)!!

        // Both habits fully drained; per-habit spendable is now exactly zero.
        assertEquals(2000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(3000L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        assertEquals(5000L, allocationsFor(result.id).sumOf { it.amount })
        assertEquals(0L, spendableOf(1L))
        assertEquals(0L, spendableOf(2L))
        assertEquals(0L, result.balanceAfter)
    }

    @Test
    fun `waterfall rejects an amount above the pot without writing anything`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 2000L, 2L to 1000L)

        // Pot is 3000; asking for 3001 is rejected.
        val result = repository.cashOut("Too much", 3001)

        assertNull(result)
        assertEquals(0, repository.getRewardCount())
        assertEquals(0L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(0L, fakeCashOutAllocationDao.getTotalForHabit(2L))
    }

    @Test
    fun `waterfall skips private habits when scope is empty`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0, isPrivate = false),
            HabitSeed(id = 2, sortOrder = 1, isPrivate = true)
        )
        seedRawBalances(1L to 2000L, 2L to 5000L)

        // Only the public habit's 2000 counts toward the pot.
        assertNull(repository.cashOut("Beyond public pot", 2500))

        val result = repository.cashOut("Within public pot", 2000)!!
        assertEquals(2000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(0L, fakeCashOutAllocationDao.getTotalForHabit(2L))
    }

    @Test
    fun `per-habit spendable stays exact after a partial claim`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 3000L, 2L to 1000L)

        repository.cashOut("Treat", 3500)

        // habit 1 drained fully (0 left); habit 2 has 500 of its 1000 left.
        assertEquals(0L, spendableOf(1L))
        assertEquals(500L, spendableOf(2L))
    }

    // =====================================================================
    // Edit Re-split Tests
    // =====================================================================

    @Test
    fun `editing the amount re-runs the waterfall at current balances`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 5000L, 2L to 5000L)

        val original = repository.cashOut("Treat", 6000)!!
        // Highest-first (tie → id): habit 1 gives 5000, habit 2 gives 1000.
        assertEquals(5000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(1000L, fakeCashOutAllocationDao.getTotalForHabit(2L))

        // Balances shift: habit 2 grows. Re-split must follow the new balances.
        seedRawBalances(1L to 5000L, 2L to 8000L)

        val success = repository.updateCashOut(original.copy(amount = 8000))

        assertTrue(success)
        // Old allocations are gone; the claim is re-drawn highest-first (habit 2).
        assertEquals(8000L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        assertEquals(0L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(8000L, allocationsFor(original.id).sumOf { it.amount })
    }

    @Test
    fun `editing the amount above the re-split pot is rejected without mutating`() = runTest {
        seedHabits(
            HabitSeed(id = 1, sortOrder = 0),
            HabitSeed(id = 2, sortOrder = 1)
        )
        seedRawBalances(1L to 5000L, 2L to 5000L)

        val original = repository.cashOut("Treat", 6000)!!

        // Pot available for the re-split is 10000 (its own allocations added
        // back). Asking for 20000 must fail and leave allocations untouched.
        val success = repository.updateCashOut(original.copy(amount = 20000))

        assertFalse(success)
        assertEquals(5000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
        assertEquals(1000L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        assertEquals(6000L, repository.getCashOutById(original.id)?.amount)
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    /**
     * Set up a check-in balance for testing, in cents.
     * Creates a single check-in with the specified balance for the default habit.
     */
    private fun setupBalance(balance: Long) {
        val checkIn = CheckIn(
            id = 1L,
            date = LocalDate.now().minusDays(1),
            didExercise = true,
            balanceAfter = balance
        )
        fakeCheckInDao.setCheckIns(listOf(checkIn))
    }

    /**
     * A habit to seed for a multi-habit waterfall test.
     */
    private data class HabitSeed(
        val id: Long,
        val sortOrder: Int = 0,
        val reward: Long = 500L,
        val isPrivate: Boolean = false
    )

    /**
     * Replace the seeded habits. The default habit (id 1) is already present
     * from [setup]; this overwrites the habit set entirely.
     */
    private fun seedHabits(vararg habits: HabitSeed) {
        fakeHabitDao.seed(
            *habits.map { seed ->
                Habit(
                    id = seed.id,
                    name = "Habit ${seed.id}",
                    rewardValue = seed.reward,
                    sortOrder = seed.sortOrder,
                    isPrivate = seed.isPrivate
                )
            }.toTypedArray()
        )
    }

    /**
     * Seed a raw check-in balance per habit. Each pair is habitId to balance.
     */
    private fun seedRawBalances(vararg balances: Pair<Long, Long>) {
        val checkIns = balances.mapIndexed { index, (habitId, balance) ->
            CheckIn(
                id = (index + 1).toLong(),
                date = LocalDate.now().minusDays(1),
                didExercise = true,
                balanceAfter = balance,
                habitId = habitId
            )
        }
        fakeCheckInDao.setCheckIns(checkIns)
    }

    /**
     * A habit's current spendable balance: raw check-in balance minus its
     * allocations minus its freeze spending, floored at zero.
     */
    private suspend fun spendableOf(habitId: Long): Long {
        val raw = checkInRepository.getCurrentBalanceOnce(habitId)
        val allocated = fakeCashOutAllocationDao.getTotalForHabit(habitId)
        val freeze = freezeRepository.getTotalFreezeSpending(habitId)
        return (raw - allocated - freeze).coerceAtLeast(0L)
    }

    private suspend fun allocationsFor(cashOutId: Long): List<CashOutAllocation> =
        fakeCashOutAllocationDao.getForCashOut(cashOutId)
}
