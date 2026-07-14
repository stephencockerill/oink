package com.oink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for BalanceCalculator.
 *
 * These are pure function tests with no dependencies - exactly what
 * the Android testing guidelines recommend starting with.
 *
 * All money values are in cents (Long), e.g. $100.00 is 10000.
 */
class BalanceCalculatorTest {

    // =====================================================================
    // calculateActualBalance tests
    // =====================================================================

    @Test
    fun `calculate balance with no deductions returns check-in balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 10000,
            totalCashedOut = 0,
            totalFreezeSpending = 0
        )
        assertEquals(10000L, result)
    }

    @Test
    fun `calculate balance subtracts cash-outs correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 10000,
            totalCashedOut = 3000,
            totalFreezeSpending = 0
        )
        assertEquals(7000L, result)
    }

    @Test
    fun `calculate balance subtracts freeze spending correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 10000,
            totalCashedOut = 0,
            totalFreezeSpending = 2000
        )
        assertEquals(8000L, result)
    }

    @Test
    fun `calculate balance subtracts both deductions correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 13000,
            totalCashedOut = 5000,
            totalFreezeSpending = 1000
        )
        // 130 - 50 - 10 = 70
        assertEquals(7000L, result)
    }

    @Test
    fun `calculate balance returns zero when deductions exceed balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 5000,
            totalCashedOut = 10000,
            totalFreezeSpending = 0
        )
        // Should be coerced to 0, not negative
        assertEquals(0L, result)
    }

    @Test
    fun `calculate balance is exact for odd cent amounts`() {
        // Integer cents means subtraction is always exact - no rounding needed.
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 10033,
            totalCashedOut = 5011,
            totalFreezeSpending = 1022
        )
        // 10033 - 5011 - 1022 = 4000
        assertEquals(4000L, result)
    }

    @Test
    fun `calculate balance with zero check-in balance`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 0,
            totalCashedOut = 0,
            totalFreezeSpending = 0
        )
        assertEquals(0L, result)
    }

    // =====================================================================
    // calculateBalanceAfterDeduction tests
    // =====================================================================

    @Test
    fun `calculate balance after deduction includes additional amount`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 10000,
            totalCashedOut = 2000,
            totalFreezeSpending = 0,
            additionalDeduction = 3000
        )
        // Current actual: 100 - 20 = 80
        // After deduction: 100 - (20 + 30) = 50
        assertEquals(5000L, result)
    }

    @Test
    fun `calculate balance after deduction with existing spending`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 13000,
            totalCashedOut = 5000,
            totalFreezeSpending = 1000,
            additionalDeduction = 2000
        )
        // 130 - 50 - 10 - 20 = 50
        assertEquals(5000L, result)
    }

    @Test
    fun `calculate balance after deduction returns zero when over-deducting`() {
        val result = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = 5000,
            totalCashedOut = 3000,
            totalFreezeSpending = 0,
            additionalDeduction = 10000
        )
        // 50 - 30 - 100 = -80, should be coerced to 0
        assertEquals(0L, result)
    }

    // =====================================================================
    // Edge cases - the stuff that catches bugs
    // =====================================================================

    @Test
    fun `handles very small amounts correctly`() {
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 1, // one cent
            totalCashedOut = 0,
            totalFreezeSpending = 0
        )
        assertEquals(1L, result)
    }

    @Test
    fun `handles typical user scenario - earned then cashed out`() {
        // User earned $130 (26 completed days at $5)
        // Cashed out $50 for darts
        // No freezes used
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 13000,
            totalCashedOut = 5000,
            totalFreezeSpending = 0
        )
        assertEquals(8000L, result)
    }

    @Test
    fun `handles user scenario with freeze`() {
        // User earned $130
        // Cashed out $50
        // Used one freeze at $10
        val result = BalanceCalculator.calculateActualBalance(
            checkInBalance = 13000,
            totalCashedOut = 5000,
            totalFreezeSpending = 1000
        )
        assertEquals(7000L, result)
    }
}
