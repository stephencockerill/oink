package com.oink.app.utils

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
 * - Check-in balance = Total accumulated through completed/miss calculations
 * - Cash-outs and freeze costs are tracked SEPARATELY, not baked into check-ins
 * - This prevents the bug where toggling a check-in would lose track of spending
 */
object BalanceCalculator {

    /**
     * Calculate the actual spendable balance.
     *
     * @param checkInBalance The raw balance from check-ins (result of completed/miss calculations), in cents
     * @param totalCashedOut Sum of all cash-out amounts, in cents
     * @param totalFreezeSpending Sum of all freeze costs paid, in cents
     * @return The actual balance in cents, minimum 0
     */
    fun calculateActualBalance(
        checkInBalance: Long,
        totalCashedOut: Long,
        totalFreezeSpending: Long
    ): Long {
        // Integer cents, so subtraction is exact - no floating point errors.
        return (checkInBalance - totalCashedOut - totalFreezeSpending).coerceAtLeast(0L)
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
        currentCheckInBalance: Long,
        totalCashedOut: Long,
        totalFreezeSpending: Long,
        additionalDeduction: Long
    ): Long {
        return calculateActualBalance(
            checkInBalance = currentCheckInBalance,
            totalCashedOut = totalCashedOut + additionalDeduction,
            totalFreezeSpending = totalFreezeSpending
        )
    }
}

