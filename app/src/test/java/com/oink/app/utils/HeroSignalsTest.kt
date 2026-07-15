package com.oink.app.utils

import com.oink.app.data.CheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [HeroSignals.recent].
 *
 * Pure derivation of the mascot's "last action" signals from a check-in list.
 * Covers the empty case, the first check-in, a gain, and a fresh halving - the
 * negative delta that flips the mascot to a comeback.
 */
class HeroSignalsTest {

    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun `empty check-ins yield no date and zero delta`() {
        val result = HeroSignals.recent(emptyList())

        assertNull(result.lastCheckIn)
        assertEquals(0L, result.balanceDelta)
    }

    @Test
    fun `first check-in delta is measured from the starting balance of zero`() {
        val result = HeroSignals.recent(
            listOf(CheckIn(id = 1, date = today, didSucceed = true, balanceAfter = 500L))
        )

        assertEquals(today, result.lastCheckIn)
        assertEquals(500L, result.balanceDelta)
    }

    @Test
    fun `a gain produces a positive delta`() {
        val result = HeroSignals.recent(
            listOf(
                CheckIn(id = 1, date = today.minusDays(1), didSucceed = true, balanceAfter = 500L),
                CheckIn(id = 2, date = today, didSucceed = true, balanceAfter = 1_000L)
            )
        )

        assertEquals(today, result.lastCheckIn)
        assertEquals(500L, result.balanceDelta)
    }

    @Test
    fun `a fresh halving produces a negative delta`() {
        val result = HeroSignals.recent(
            listOf(
                CheckIn(id = 1, date = today.minusDays(1), didSucceed = true, balanceAfter = 1_000L),
                CheckIn(id = 2, date = today, didSucceed = false, balanceAfter = 500L)
            )
        )

        assertEquals(today, result.lastCheckIn)
        assertEquals(-500L, result.balanceDelta)
    }

    @Test
    fun `order of the input list does not matter`() {
        // Newest-first, as the DAO flows deliver them.
        val result = HeroSignals.recent(
            listOf(
                CheckIn(id = 2, date = today, didSucceed = false, balanceAfter = 500L),
                CheckIn(id = 1, date = today.minusDays(1), didSucceed = true, balanceAfter = 1_000L)
            )
        )

        assertEquals(today, result.lastCheckIn)
        assertEquals(-500L, result.balanceDelta)
    }
}
