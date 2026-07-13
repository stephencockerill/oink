package com.oink.app.data

/**
 * Interface for the freeze-spending and reward totals needed by
 * CashOutRepository and DefaultDeductionProvider.
 *
 * This exists to keep cash-out balance math testable without an Android Context
 * or a live database.
 *
 * In production: [HabitCashOutPreferencesProvider] sources freeze spending from
 * the default habit via [FreezeRepository].
 * In tests: a simple fake implements this.
 */
interface CashOutPreferencesProvider : ExerciseRewardProvider {
    /** Total amount spent on freezes, in cents. */
    suspend fun getTotalFreezeSpending(): Long
    /** Add to the total freeze spending, in cents. */
    suspend fun addFreezeSpending(amount: Long)
}

