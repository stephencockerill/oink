package com.oink.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [FreezeRepository].
 *
 * Proves freeze purchase, use, and spending accrual are keyed by habit: an
 * operation on one habit never touches another habit's banked count,
 * frozen days, or spend. Uses in-memory fake DAOs (per the repo's "prefer
 * fakes" convention) so no database is required.
 *
 * All money values are in cents (Long), e.g. $5.00 is 500.
 */
class FreezeRepositoryTest {

    private lateinit var habitDao: FakeHabitDao
    private lateinit var frozenDayDao: FakeFrozenDayDao
    private lateinit var repository: FreezeRepository

    private val habitA = 1L
    private val habitB = 2L
    private val day = LocalDate.of(2025, 1, 10)

    @Before
    fun setup() {
        habitDao = FakeHabitDao().apply {
            seed(
                Habit(id = habitA, name = "Workout"),
                Habit(id = habitB, name = "Meditate")
            )
        }
        frozenDayDao = FakeFrozenDayDao()
        repository = FreezeRepository(habitDao, frozenDayDao)
    }

    // =====================================================================
    // Purchase
    // =====================================================================

    @Test
    fun `purchaseFreeze increments the target habit up to the max`() = runTest {
        assertTrue(repository.purchaseFreeze(habitA))
        assertEquals(1, repository.getAvailableFreezes(habitA))

        assertTrue(repository.purchaseFreeze(habitA))
        assertEquals(2, repository.getAvailableFreezes(habitA))
    }

    @Test
    fun `purchaseFreeze at the max returns false and does not exceed the cap`() = runTest {
        repeat(PreferencesRepository.MAX_FREEZES) { repository.purchaseFreeze(habitA) }

        assertFalse(repository.purchaseFreeze(habitA))
        assertEquals(PreferencesRepository.MAX_FREEZES, repository.getAvailableFreezes(habitA))
    }

    @Test
    fun `purchaseFreeze for a missing habit returns false`() = runTest {
        assertFalse(repository.purchaseFreeze(999L))
    }

    @Test
    fun `purchaseFreeze only affects the target habit`() = runTest {
        repository.purchaseFreeze(habitA)

        assertEquals(1, repository.getAvailableFreezes(habitA))
        assertEquals(0, repository.getAvailableFreezes(habitB))
    }

    // =====================================================================
    // Use
    // =====================================================================

    @Test
    fun `useFreeze decrements the count and records the frozen day`() = runTest {
        repository.purchaseFreeze(habitA)

        assertTrue(repository.useFreeze(habitA, day))
        assertEquals(0, repository.getAvailableFreezes(habitA))
        assertTrue(repository.isDateFrozen(habitA, day))
        assertEquals(setOf(day), repository.getFrozenDates(habitA))
    }

    @Test
    fun `useFreeze with no banked freezes returns false and records nothing`() = runTest {
        assertFalse(repository.useFreeze(habitA, day))
        assertFalse(repository.isDateFrozen(habitA, day))
        assertEquals(emptySet<LocalDate>(), repository.getFrozenDates(habitA))
    }

    @Test
    fun `useFreeze only freezes the target habit`() = runTest {
        repository.purchaseFreeze(habitA)
        repository.useFreeze(habitA, day)

        assertTrue(repository.isDateFrozen(habitA, day))
        assertFalse(repository.isDateFrozen(habitB, day))
        assertEquals(emptySet<LocalDate>(), repository.getFrozenDates(habitB))
        assertEquals(0, repository.getAvailableFreezes(habitB))
    }

    @Test
    fun `freezing the same day twice inserts no duplicate`() = runTest {
        repository.purchaseFreeze(habitA)
        repository.purchaseFreeze(habitA)
        repository.useFreeze(habitA, day)
        repository.useFreeze(habitA, day)

        assertEquals(setOf(day), repository.getFrozenDates(habitA))
    }

    @Test
    fun `the same day can be frozen independently for two habits`() = runTest {
        repository.purchaseFreeze(habitA)
        repository.purchaseFreeze(habitB)

        repository.useFreeze(habitA, day)
        repository.useFreeze(habitB, day)

        assertTrue(repository.isDateFrozen(habitA, day))
        assertTrue(repository.isDateFrozen(habitB, day))
    }

    // =====================================================================
    // Spending accrual
    // =====================================================================

    @Test
    fun `addFreezeSpending accumulates on the target habit only`() = runTest {
        repository.addFreezeSpending(habitA, 1000)
        repository.addFreezeSpending(habitA, 500)

        assertEquals(1500L, repository.getTotalFreezeSpending(habitA))
        assertEquals(0L, repository.getTotalFreezeSpending(habitB))
    }

    @Test
    fun `addFreezeSpending for a missing habit is a no-op`() = runTest {
        repository.addFreezeSpending(999L, 1000)

        assertEquals(0L, repository.getTotalFreezeSpending(habitA))
        assertEquals(0L, repository.getTotalFreezeSpending(habitB))
    }

    // =====================================================================
    // Observable reads
    // =====================================================================

    @Test
    fun `availableFreezes flow reflects the latest count`() = runTest {
        repository.purchaseFreeze(habitA)
        assertEquals(1, repository.availableFreezes(habitA).first())
    }

    @Test
    fun `totalFreezeSpending flow reflects the latest total`() = runTest {
        repository.addFreezeSpending(habitA, 750)
        assertEquals(750L, repository.totalFreezeSpending(habitA).first())
    }

    @Test
    fun `frozenDates flow reflects the frozen days`() = runTest {
        repository.purchaseFreeze(habitA)
        repository.useFreeze(habitA, day)
        assertEquals(setOf(day), repository.frozenDates(habitA).first())
    }
}
