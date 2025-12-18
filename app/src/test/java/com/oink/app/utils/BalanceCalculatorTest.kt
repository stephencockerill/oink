package com.oink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for BalanceCalculator.
 *
 * These are pure function tests with no dependencies - exactly what
 * the Android testing guidelines recommend starting with.
 */
class BalanceCalculatorTest {

    // =====================================================================
    // calculateActualBalance tests
    // =====================================================================

    @Test
    fun `calculate balance with no deductions returns check-in balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 100.0,
            totalCashedOut = 0.0,
            totalFreezeSpending = 0.0
        )
        assertEquals(100.0, result, 0.001)
    }

    @Test
    fun `calculate balance subtracts cash-outs correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 100.0,
            totalCashedOut = 30.0,
            totalFreezeSpending = 0.0
        )
        assertEquals(70.0, result, 0.001)
    }

    @Test
    fun `calculate balance subtracts freeze spending correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 100.0,
            totalCashedOut = 0.0,
            totalFreezeSpending = 20.0
        )
        assertEquals(80.0, result, 0.001)
    }

    @Test
    fun `calculate balance subtracts both deductions correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 130.0,
            totalCashedOut = 50.0,
            totalFreezeSpending = 10.0
        )
        // 130 - 50 - 10 = 70
        assertEquals(70.0, result, 0.001)
    }

    @Test
    fun `calculate balance returns zero when deductions exceed balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 50.0,
            totalCashedOut = 100.0,
            totalFreezeSpending = 0.0
        )
        // Should be coerced to 0, not negative
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `calculate balance handles floating point precision`() {
        // This tests the "floating point fuckery" protection
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 100.33,
            totalCashedOut = 50.11,
            totalFreezeSpending = 10.22
        )
        // 100.33 - 50.11 - 10.22 = 40.00 (should be rounded to 2 decimal places)
        assertEquals(40.0, result, 0.001)
    }

    @Test
    fun `calculate balance with zero check-in balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 0.0,
            totalCashedOut = 0.0,
            totalFreezeSpending = 0.0
        )
        assertEquals(0.0, result, 0.001)
    }

    // =====================================================================
    // calculateBalanceAfterDeduction tests
    // =====================================================================

    @Test
    fun `calculate balance after deduction includes additional amount`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 100.0,
            totalCashedOut = 20.0,
            totalFreezeSpending = 0.0,
            additionalDeduction = 30.0
        )
        // Current actual: 100 - 20 = 80
        // After deduction: 100 - (20 + 30) = 50
        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `calculate balance after deduction with existing spending`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 130.0,
            totalCashedOut = 50.0,
            totalFreezeSpending = 10.0,
            additionalDeduction = 20.0
        )
        // 130 - 50 - 10 - 20 = 50
        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `calculate balance after deduction returns zero when over-deducting`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 50.0,
            totalCashedOut = 30.0,
            totalFreezeSpending = 0.0,
            additionalDeduction = 100.0
        )
        // 50 - 30 - 100 = -80, should be coerced to 0
        assertEquals(0.0, result, 0.001)
    }

    // =====================================================================
    // Edge cases - the stuff that catches bugs
    // =====================================================================

    @Test
    fun `handles very small amounts correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 0.01,
            totalCashedOut = 0.0,
            totalFreezeSpending = 0.0
        )
        assertEquals(0.01, result, 0.001)
    }

    @Test
    fun `handles typical user scenario - earned then cashed out`() {
        // User earned $130 (26 workouts at $5)
        // Cashed out $50 for darts
        // No freezes used
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 130.0,
            totalCashedOut = 50.0,
            totalFreezeSpending = 0.0
        )
        assertEquals(80.0, result, 0.001)
    }

    @Test
    fun `handles user scenario with freeze`() {
        // User earned $130
        // Cashed out $50
        // Used one freeze at $10
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 130.0,
            totalCashedOut = 50.0,
            totalFreezeSpending = 10.0
        )
        assertEquals(70.0, result, 0.001)
    }
}

