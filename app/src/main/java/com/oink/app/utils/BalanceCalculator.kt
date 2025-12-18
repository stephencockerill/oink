package com.oink.app.utils

import kotlin.math.roundToLong

/**
 * Centralized balance calculation logic.
 *
 * The ACTUAL balance a user can spend is calculated as:
 *   Check-in Balance - Total Cashed Out - Total Freeze Spending
 *
 * This utility exists because balance calculation was duplicated in:
 * - MainViewModel (for reactive UI)
 * - RewardsViewModel (for reactive UI)
 * - CashOutRepository (for validation)
 * - OinkWidget (for widget display)
 *
 * Having one source of truth prevents bugs where different parts
 * of the app calculate balance differently.
 *
 * WHY THIS FORMULA?
 * - Check-in balance = Total accumulated through exercise/miss calculations
 * - Cash-outs and freeze costs are tracked SEPARATELY, not baked into check-ins
 * - This prevents the bug where toggling a check-in would lose track of spending
 */
object BalanceCalculator {

    /**
     * Calculate the actual spendable balance.
     *
     * @param checkInBalance The raw balance from check-ins (result of exercise/miss calculations)
     * @param totalCashedOut Sum of all cash-out amounts
     * @param totalFreezeSpending Sum of all freeze costs paid
     * @return The actual balance, rounded to 2 decimal places, minimum 0.0
     */
    fun calculateActualBalance(
        checkInBalance: Double,
        totalCashedOut: Double,
        totalFreezeSpending: Double
    ): Double {
        val actualBalance = checkInBalance - totalCashedOut - totalFreezeSpending
        // Round to 2 decimal places to avoid floating point fuckery
        return ((actualBalance * 100).roundToLong() / 100.0).coerceAtLeast(0.0)
    }

    /**
     * Calculate what the balance would be after a potential action.
     *
     * @param currentCheckInBalance Current raw check-in balance
     * @param totalCashedOut Current total cashed out
     * @param totalFreezeSpending Current freeze spending
     * @param additionalDeduction Additional amount being deducted (e.g., new cash-out amount)
     * @return The projected balance after the deduction
     */
    fun calculateBalanceAfterDeduction(
        currentCheckInBalance: Double,
        totalCashedOut: Double,
        totalFreezeSpending: Double,
        additionalDeduction: Double
    ): Double {
        return calculateActualBalance(
            checkInBalance = currentCheckInBalance,
            totalCashedOut = totalCashedOut + additionalDeduction,
            totalFreezeSpending = totalFreezeSpending
        )
    }
}

