package com.oink.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Supplies the total money a single habit has spent (its share of cash-outs +
 * its freeze costs) as of a given date.
 *
 * A miss halves the SPENDABLE balance ("lose half your balance"), not the raw
 * earnings ledger. Spendable = raw check-in balance - deductions, so halving
 * spendable requires knowing the deductions in force at the moment of the miss.
 * See [CheckInRepository] for how this feeds the miss calculation.
 */
fun interface DeductionProvider {
    /**
     * Total deductions for a habit (its cash-out allocation shares + its freeze
     * spending) in force as of the end of [date], in cents.
     */
    suspend fun getDeductionsAsOf(habitId: Long, date: LocalDate): Long
}

/**
 * Production [DeductionProvider], scoped per habit.
 *
 * A cash-out is pot-level; each [CashOutAllocation] records the slice of it
 * attributed to one habit. This sums the habit's allocation shares whose parent
 * cash-out is dated on or before [date], so editing a past miss halves the
 * spendable balance as it stood on that day rather than today's. Freeze spending
 * is stored only as a running total on the habit row (not per-use timestamps),
 * so it is treated as fully in force regardless of [date]. This is exact for the
 * common case of missing today; it can slightly over-count freeze spending only
 * when a past miss is edited after a freeze was later used.
 */
class DefaultDeductionProvider(
    private val cashOutDao: CashOutDao,
    private val cashOutAllocationDao: CashOutAllocationDao,
    private val freezeRepository: FreezeRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DeductionProvider {

    override suspend fun getDeductionsAsOf(habitId: Long, date: LocalDate): Long {
        val cashOutDatesById = cashOutDao.getAllCashOuts().associate { it.id to cashOutDateOf(it) }
        val allocated = cashOutAllocationDao.getForHabit(habitId)
            .filter { allocation ->
                val cashOutDate = cashOutDatesById[allocation.cashOutId] ?: return@filter false
                cashOutDate <= date
            }
            .sumOf { it.amount }
        return allocated + freezeRepository.getTotalFreezeSpending(habitId)
    }

    private fun cashOutDateOf(cashOut: CashOut): LocalDate =
        Instant.ofEpochMilli(cashOut.cashedOutAt).atZone(zoneId).toLocalDate()
}
