package com.oink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [Mascot.stateFor].
 *
 * Pure function tests with no dependencies - `today` is injected, so there is no hidden clock.
 * Every branch and boundary of the precedence chain is covered.
 */
class MascotTest {

    private val today = LocalDate.of(2026, 7, 14)

    // =====================================================================
    // Branch 1: no activity yet -> SLEEPING
    // =====================================================================

    @Test
    fun `null last check-in returns sleeping`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 5,
            lastCheckIn = null,
            today = today
        )
        assertEquals(MascotState.SLEEPING, result)
    }

    @Test
    fun `null last check-in returns sleeping even with a loss`() {
        val result = Mascot.stateFor(
            balanceDelta = -500,
            currentStreak = 3,
            lastCheckIn = null,
            today = today
        )
        assertEquals(MascotState.SLEEPING, result)
    }

    // =====================================================================
    // Branch 2: gone quiet (>1 day) -> SLEEPING, boundary at exactly 1 day
    // =====================================================================

    @Test
    fun `checked in today is not sleeping`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 2,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.HAPPY, result)
    }

    @Test
    fun `exactly one day stale is not sleeping`() {
        val result = Mascot.stateFor(
            balanceDelta = 0,
            currentStreak = 1,
            lastCheckIn = today.minusDays(1),
            today = today
        )
        // 1 day is within tolerance -> falls through to NEUTRAL, not SLEEPING.
        assertEquals(MascotState.NEUTRAL, result)
    }

    @Test
    fun `two days stale returns sleeping`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 4,
            lastCheckIn = today.minusDays(2),
            today = today
        )
        assertEquals(MascotState.SLEEPING, result)
    }

    @Test
    fun `long absence returns sleeping`() {
        val result = Mascot.stateFor(
            balanceDelta = -500,
            currentStreak = 0,
            lastCheckIn = today.minusDays(30),
            today = today
        )
        assertEquals(MascotState.SLEEPING, result)
    }

    // =====================================================================
    // Branch 3: recent loss -> COMEBACK (takes precedence over HAPPY)
    // =====================================================================

    @Test
    fun `negative delta returns comeback`() {
        val result = Mascot.stateFor(
            balanceDelta = -250,
            currentStreak = 0,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.COMEBACK, result)
    }

    @Test
    fun `negative delta returns comeback even with a strong streak`() {
        // A fresh loss must not be masked by an existing streak.
        val result = Mascot.stateFor(
            balanceDelta = -100,
            currentStreak = 10,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.COMEBACK, result)
    }

    // =====================================================================
    // Branch 4: growing on a streak -> HAPPY, boundary at streak >= 2
    // =====================================================================

    @Test
    fun `positive delta with streak of two returns happy`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 2,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.HAPPY, result)
    }

    @Test
    fun `positive delta with a long streak returns happy`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 12,
            lastCheckIn = today.minusDays(1),
            today = today
        )
        assertEquals(MascotState.HAPPY, result)
    }

    @Test
    fun `positive delta with streak below two returns neutral`() {
        val result = Mascot.stateFor(
            balanceDelta = 500,
            currentStreak = 1,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.NEUTRAL, result)
    }

    // =====================================================================
    // Branch 5: baseline -> NEUTRAL
    // =====================================================================

    @Test
    fun `zero delta returns neutral`() {
        val result = Mascot.stateFor(
            balanceDelta = 0,
            currentStreak = 5,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.NEUTRAL, result)
    }

    @Test
    fun `zero delta with no streak returns neutral`() {
        val result = Mascot.stateFor(
            balanceDelta = 0,
            currentStreak = 0,
            lastCheckIn = today,
            today = today
        )
        assertEquals(MascotState.NEUTRAL, result)
    }
}
