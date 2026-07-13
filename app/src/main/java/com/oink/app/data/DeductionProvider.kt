package com.oink.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Supplies the total money withdrawn from the bank (cash-outs + freeze costs)
 * as of a given date.
 *
 * A miss halves the SPENDABLE balance ("lose half your balance"), not the raw
 * earnings ledger. Spendable = raw check-in balance - deductions, so halving
 * spendable requires knowing the deductions in force at the moment of the miss.
 * See [CheckInRepository] for how this feeds the miss calculation.
 */
fun interface DeductionProvider {
    /**
     * Total deductions (cash-outs + freeze spending) in force as of the end of [date].
     */
    suspend fun getDeductionsAsOf(date: LocalDate): Double
}

/**
 * Production [DeductionProvider].
 *
 * Cash-outs are counted by their own date, so editing a past miss halves the
 * spendable balance as it stood on that day rather than today's. Freeze spending
 * is stored only as a running total (not per-use timestamps), so it is treated
 * as fully in force regardless of [date]. This is exact for the common case of
 * missing today; it can slightly over-count freeze spending only when a past
 * miss is edited after a freeze was later used. Dating freeze spending is
 * deferred to the multi-habit ledger refactor.
 */
class DefaultDeductionProvider(
    private val cashOutDao: CashOutDao,
    private val preferences: CashOutPreferencesProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DeductionProvider {

    override suspend fun getDeductionsAsOf(date: LocalDate): Double {
        val cashedOut = cashOutDao.getAllCashOuts()
            .filter { cashOutDateOf(it) <= date }
            .sumOf { it.amount }
        return cashedOut + preferences.getTotalFreezeSpending()
    }

    private fun cashOutDateOf(cashOut: CashOut): LocalDate =
        Instant.ofEpochMilli(cashOut.cashedOutAt).atZone(zoneId).toLocalDate()
}
