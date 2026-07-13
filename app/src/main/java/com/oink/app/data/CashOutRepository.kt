package com.oink.app.data

import com.oink.app.utils.BalanceCalculator
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing cash-out operations.
 *
 * This is where the REWARD magic happens! When a user has earned
 * enough through consistent exercise, they can treat themselves.
 *
 * Key psychological framing:
 * - Cash-outs are CELEBRATIONS, not losses
 * - "You EARNED this!" energy
 * - Show how many workouts it took to earn this reward
 *
 * IMPORTANT: Cash-outs are tracked separately from check-in balances.
 * We do NOT modify check-in records when cashing out. Instead, we
 * just record the cash-out, and the actual balance is calculated as:
 *   (Check-in balance) - (Total cashed out) - (Total freeze spending)
 *
 * This prevents the nasty bug where toggling a check-in between
 * "exercised" and "didn't" would lose track of cash-outs.
 *
 * A cash-out is pot-level, but every one carries a [CashOutAllocation]
 * attributing its amount to a habit ([habitId], defaulting to
 * [HabitRepository.DEFAULT_HABIT_ID]). The allocation is what the per-habit
 * halving reads (see [DefaultDeductionProvider]), so this repository keeps it in
 * step with the cash-out on create, edit, and delete. Splitting one cash-out
 * across multiple habits is deferred to the pot-aggregation work.
 */
class CashOutRepository(
    private val cashOutDao: CashOutDao,
    private val cashOutAllocationDao: CashOutAllocationDao,
    private val checkInRepository: CheckInRepository,
    private val preferencesProvider: CashOutPreferencesProvider,
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID
) {

    /**
     * Flow of all cash-outs (most recent first).
     */
    val allCashOuts: Flow<List<CashOut>> = cashOutDao.getAllCashOutsFlow()

    /**
     * Flow of total amount cashed out all-time.
     */
    val totalCashedOut: Flow<Long> = cashOutDao.getTotalCashedOutFlow()

    /**
     * Cash out from the piggy bank! 🎉
     *
     * This is the reward for all your hard work. The balance goes down,
     * but it's a GOOD thing - you're treating yourself to something
     * you earned through discipline and sweat.
     *
     * NOTE: We don't modify check-in records here! We just record the
     * cash-out, and the balance reduction happens automatically because
     * actual balance = check-in balance - total cashed out - freeze spending.
     *
     * @param name What you're treating yourself to
     * @param amount How much to cash out
     * @param emoji Emoji to represent your reward
     * @return The CashOut record, or null if insufficient balance
     */
    suspend fun cashOut(
        name: String,
        amount: Long,
        emoji: String = "🎁"
    ): CashOut? {
        // Get ACTUAL current balance (after existing deductions)
        val currentBalance = getCurrentBalance()

        // Can't cash out more than you have
        if (amount > currentBalance || amount <= 0) {
            return null
        }

        val balanceAfter = BalanceCalculator.calculateBalanceAfterDeduction(
            currentCheckInBalance = checkInRepository.getCurrentBalanceOnce(habitId),
            totalCashedOut = cashOutDao.getTotalCashedOut(),
            totalFreezeSpending = preferencesProvider.getTotalFreezeSpending(),
            additionalDeduction = amount
        )

        // Just record the cash-out - DON'T deduct from check-in!
        // The balance reduction happens automatically via the calculation.
        val cashOut = CashOut(
            name = name,
            amount = amount,
            emoji = emoji,
            balanceBefore = currentBalance,
            balanceAfter = balanceAfter
        )

        val id = cashOutDao.insert(cashOut)
        // Attribute the full amount to the habit so the per-habit halving sees
        // this spending, capturing the reward rate in force for "workouts earned".
        cashOutAllocationDao.insert(
            CashOutAllocation(
                cashOutId = id,
                habitId = habitId,
                amount = amount,
                exerciseRewardAtTime = checkInRepository.getExerciseReward(habitId)
            )
        )
        return cashOut.copy(id = id)
    }

    /**
     * Get the ACTUAL current balance after all deductions.
     * Uses BalanceCalculator for the actual calculation.
     */
    private suspend fun getCurrentBalance(): Long {
        return BalanceCalculator.calculateActualBalance(
            checkInBalance = checkInRepository.getCurrentBalanceOnce(habitId),
            totalCashedOut = cashOutDao.getTotalCashedOut(),
            totalFreezeSpending = preferencesProvider.getTotalFreezeSpending()
        )
    }

    /**
     * Get total amount cashed out all-time.
     */
    suspend fun getTotalCashedOut(): Long {
        return cashOutDao.getTotalCashedOut()
    }

    /**
     * Get all cash-outs.
     */
    suspend fun getAllCashOuts(): List<CashOut> {
        return cashOutDao.getAllCashOuts()
    }

    /**
     * Get count of rewards earned.
     */
    suspend fun getRewardCount(): Int {
        return cashOutDao.getCashOutCount()
    }

    /**
     * Calculate total workouts represented by all cash-outs.
     * Sums up workoutsToEarn from each individual cash-out to account
     * for potentially different reward amounts over time.
     */
    suspend fun getTotalWorkoutsRewarded(): Int {
        return getAllCashOuts().sumOf { it.workoutsToEarn }
    }

    /**
     * Get a specific cash-out by ID.
     */
    suspend fun getCashOutById(id: Long): CashOut? {
        return cashOutDao.getById(id)
    }

    /**
     * Update an existing cash-out.
     *
     * When editing a reward amount, the balance updates automatically because
     * we calculate: actual balance = check-in balance - total cashed out - freeze spending.
     *
     * If the user increases the reward amount, balance goes down.
     * If the user decreases the reward amount, balance goes up.
     *
     * @param cashOut The updated cash-out record
     * @return true if update was successful
     */
    suspend fun updateCashOut(cashOut: CashOut): Boolean {
        val existing = cashOutDao.getById(cashOut.id)
        if (existing == null) return false

        // Update the record - balance calculation happens automatically
        // because totalCashedOut flow will emit the new sum
        cashOutDao.update(cashOut)
        // Keep the habit's allocation share in step so the per-habit halving
        // reads the edited amount. Single-habit: the whole amount is one share.
        cashOutAllocationDao.getForCashOut(cashOut.id)
            .firstOrNull { it.habitId == habitId }
            ?.let { allocation ->
                if (allocation.amount != cashOut.amount) {
                    cashOutAllocationDao.update(allocation.copy(amount = cashOut.amount))
                }
            }
        return true
    }

    /**
     * Delete a cash-out record.
     *
     * When deleting, the balance goes UP because we're removing a deduction.
     * The user gets that money "back" in their piggy bank.
     *
     * @param cashOut The cash-out to delete
     * @return true if deletion was successful
     */
    suspend fun deleteCashOut(cashOut: CashOut): Boolean {
        val existing = cashOutDao.getById(cashOut.id)
        if (existing == null) return false

        deleteAllocationsFor(cashOut.id)
        cashOutDao.delete(cashOut)
        return true
    }

    /**
     * Delete a cash-out by ID.
     */
    suspend fun deleteCashOutById(id: Long): Boolean {
        val existing = cashOutDao.getById(id) ?: return false
        deleteAllocationsFor(id)
        cashOutDao.delete(existing)
        return true
    }

    /**
     * Drop a cash-out's allocations so its spending leaves the per-habit halving.
     *
     * Room cascades these on the foreign key, but deleting them explicitly first
     * keeps the in-memory fakes (which have no cascade) correct too, and makes
     * the intent obvious at the call site.
     */
    private suspend fun deleteAllocationsFor(cashOutId: Long) {
        cashOutAllocationDao.getForCashOut(cashOutId).forEach { cashOutAllocationDao.delete(it) }
    }
}

