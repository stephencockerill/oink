package com.oink.app.data

/**
 * Interface for the freeze-spending total needed by [CashOutRepository] to
 * compute the spendable balance when cashing out.
 *
 * This exists to keep cash-out balance math testable without an Android Context
 * or a live database.
 *
 * In production: [HabitCashOutPreferencesProvider] sources freeze spending from
 * the default habit via [FreezeRepository].
 * In tests: a simple fake implements this.
 */
interface CashOutPreferencesProvider {
    /** Total amount spent on freezes, in cents. */
    suspend fun getTotalFreezeSpending(): Long
    /** Add to the total freeze spending, in cents. */
    suspend fun addFreezeSpending(amount: Long)
}

