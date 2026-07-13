package com.oink.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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
 * We do NOT modify check-in records when cashing out. Instead we record the
 * cash-out and its per-habit allocations, and each habit's spendable balance is
 * derived as:
 *   (raw check-in balance) - (that habit's cash-out allocations) - (that habit's freeze spending)
 *
 * This prevents the nasty bug where toggling a check-in between "exercised" and
 * "didn't" would lose track of cash-outs.
 *
 * A cash-out is drawn from a shared POT: the sum of every in-scope habit's
 * spendable balance. A claim drains the highest-balance habits first (the
 * waterfall) and records one [CashOutAllocation] per contributing habit, so the
 * per-habit halving (see [DefaultDeductionProvider]) sees exactly what each
 * habit funded. All money is Long cents; allocations sum to the claim exactly,
 * with no rounding.
 */
class CashOutRepository(
    private val cashOutDao: CashOutDao,
    private val cashOutAllocationDao: CashOutAllocationDao,
    private val checkInRepository: CheckInRepository,
    private val habitRepository: HabitRepository,
    private val freezeRepository: FreezeRepository,
    private val transactionRunner: TransactionRunner
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
     * Observe the shared pot: the sum of every public habit's spendable balance.
     *
     * Rebuilds whenever the set of habits changes ([HabitRepository.allHabits]),
     * then combines each public habit's spendable flow. Combining over an empty
     * iterable never emits, so an empty public-habit set is short-circuited to a
     * flow of 0.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pot: Flow<Long> = habitRepository.allHabits.flatMapLatest { habits ->
        val public = habits.filter { !it.isPrivate }
        if (public.isEmpty()) {
            flowOf(0L)
        } else {
            combine(public.map { habit -> spendableFlow(habit.id) }) { shares -> shares.sum() }
        }
    }

    /**
     * Observe the total cents a habit has funded across all cash-outs. This is
     * the per-habit analogue of [totalCashedOut] and drives per-habit deduction
     * displays.
     */
    fun allocatedForHabit(habitId: Long): Flow<Long> =
        cashOutAllocationDao.getTotalForHabitFlow(habitId)

    /**
     * Observe a single habit's spendable balance:
     * raw check-in balance - its cash-out allocations - its freeze spending,
     * floored at zero.
     */
    private fun spendableFlow(habitId: Long): Flow<Long> = combine(
        checkInRepository.currentBalance(habitId),
        allocatedForHabit(habitId),
        freezeRepository.totalFreezeSpending(habitId)
    ) { raw, allocated, freeze ->
        (raw - allocated - freeze).coerceAtLeast(0L)
    }

    /**
     * Cash out from the piggy bank! 🎉
     *
     * Draws [amount] from the shared pot of in-scope habits, draining the
     * highest-balance habits first, and records one allocation per contributing
     * habit so the per-habit halving sees the spending. The cash-out and its
     * allocations are written in one transaction.
     *
     * NOTE: We don't modify check-in records here! The balance reduction happens
     * automatically because a habit's spendable balance subtracts its allocations.
     *
     * @param name What you're treating yourself to
     * @param amount How much to cash out, in cents
     * @param emoji Emoji to represent your reward
     * @param scope The habits the pot is drawn from. Empty means all public
     *   habits (the default); a non-empty set means exactly those habit ids that
     *   exist.
     * @return The CashOut record, or null if the amount is not in `0 < amount <= pot`
     */
    suspend fun cashOut(
        name: String,
        amount: Long,
        emoji: String = "🎁",
        scope: Set<Long> = emptySet()
    ): CashOut? {
        val spendables = spendablesFor(resolveScope(scope))
        val pot = spendables.sumOf { it.spendable }

        // Can't cash out nothing, and can't cash out more than the pot holds.
        if (amount <= 0 || amount > pot) {
            return null
        }

        val plan = planWaterfall(spendables, amount)

        return transactionRunner {
            val cashOut = CashOut(
                name = name,
                amount = amount,
                emoji = emoji,
                balanceBefore = pot,
                balanceAfter = pot - amount
            )
            val id = cashOutDao.insert(cashOut)
            insertAllocations(id, plan)
            cashOut.copy(id = id)
        }
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
     *
     * Sums per allocation: each allocation's cents divided by the habit's reward
     * rate captured when the allocation was written ([CashOutAllocation.exerciseRewardAtTime]),
     * so habits with different reward rates each count correctly. Allocations
     * whose captured reward is non-positive contribute zero rather than dividing
     * by zero.
     */
    suspend fun getTotalWorkoutsRewarded(): Int {
        return cashOutAllocationDao.getAll().sumOf { allocation ->
            if (allocation.exerciseRewardAtTime <= 0L) {
                0
            } else {
                (allocation.amount / allocation.exerciseRewardAtTime).toInt()
            }
        }
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
     * A name/emoji-only edit leaves the amount and its allocations untouched. An
     * amount edit re-runs the waterfall at CURRENT balances over all public
     * habits: the cash-out's own allocations are added back to availability
     * first (they are about to be replaced), the new split is validated against
     * that pot, and only then is anything mutated. Validating before mutating
     * means the operation is safe even against the in-memory fakes, which do not
     * roll back.
     *
     * @param cashOut The updated cash-out record
     * @return true if the update was applied; false for a non-existent id or an
     *   amount edit that does not fit the pot (`newAmount` outside `0 < x <= pot`)
     */
    suspend fun updateCashOut(cashOut: CashOut): Boolean {
        val existing = cashOutDao.getById(cashOut.id) ?: return false

        if (existing.amount == cashOut.amount) {
            // Name/emoji only: no allocation churn.
            cashOutDao.update(cashOut)
            return true
        }

        // Amount changed: re-split at current balances over all public habits,
        // treating this cash-out's own allocations as available again.
        val spendables = spendablesFor(
            resolveScope(emptySet()),
            excludeCashOutId = cashOut.id
        )
        val potForEdit = spendables.sumOf { it.spendable }
        if (cashOut.amount <= 0 || cashOut.amount > potForEdit) {
            return false
        }

        val plan = planWaterfall(spendables, cashOut.amount)

        transactionRunner {
            deleteAllocationsFor(cashOut.id)
            insertAllocations(cashOut.id, plan)
            cashOutDao.update(
                cashOut.copy(
                    balanceBefore = potForEdit,
                    balanceAfter = potForEdit - cashOut.amount
                )
            )
        }
        return true
    }

    /**
     * Delete a cash-out record.
     *
     * When deleting, each contributing habit's balance goes UP because we're
     * removing its allocation. The user gets that money "back" in their piggy bank.
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
     * A habit paired with its spendable balance, used to plan a waterfall.
     */
    private data class HabitSpendable(val habit: Habit, val spendable: Long)

    /**
     * A slice of a cash-out attributed to one habit.
     */
    private data class WaterfallShare(val habit: Habit, val amount: Long)

    /**
     * Resolve the habits a claim is drawn from.
     *
     * An empty scope means all public habits ([Habit.isPrivate] false). A
     * non-empty scope means exactly those habit ids that exist; unknown ids are
     * dropped.
     */
    private suspend fun resolveScope(scope: Set<Long>): List<Habit> {
        return if (scope.isEmpty()) {
            habitRepository.getAllHabits().filter { !it.isPrivate }
        } else {
            scope.mapNotNull { habitRepository.getHabit(it) }
        }
    }

    /**
     * Pair each habit with its current spendable balance.
     *
     * Spendable = raw check-in balance - allocations - freeze spending, floored
     * at zero. When [excludeCashOutId] is given, that cash-out's own allocations
     * are excluded from the total, so an amount edit re-splits over the pot as it
     * would stand without the cash-out being edited.
     */
    private suspend fun spendablesFor(
        habits: List<Habit>,
        excludeCashOutId: Long? = null
    ): List<HabitSpendable> = habits.map { habit ->
        val raw = checkInRepository.getCurrentBalanceOnce(habit.id)
        val allocated = cashOutAllocationDao.getForHabit(habit.id)
            .filter { excludeCashOutId == null || it.cashOutId != excludeCashOutId }
            .sumOf { it.amount }
        val freeze = freezeRepository.getTotalFreezeSpending(habit.id)
        HabitSpendable(habit, (raw - allocated - freeze).coerceAtLeast(0L))
    }

    /**
     * Plan how [amount] drains across [spendables], highest-first.
     *
     * Orders by spendable DESC, then sortOrder ASC, then id ASC, and takes
     * `min(spendable, remaining)` from each habit, skipping any that contribute
     * nothing. Callers guarantee `amount <= pot`, so the remainder reaches
     * exactly zero and the shares sum to [amount] exactly.
     */
    private fun planWaterfall(
        spendables: List<HabitSpendable>,
        amount: Long
    ): List<WaterfallShare> {
        val ordered = spendables.sortedWith(
            compareByDescending<HabitSpendable> { it.spendable }
                .thenBy { it.habit.sortOrder }
                .thenBy { it.habit.id }
        )
        var remaining = amount
        val shares = mutableListOf<WaterfallShare>()
        for (entry in ordered) {
            if (remaining <= 0L) break
            val take = minOf(entry.spendable, remaining)
            if (take > 0L) {
                shares += WaterfallShare(entry.habit, take)
                remaining -= take
            }
        }
        return shares
    }

    /**
     * Write one allocation per share of a cash-out, capturing each habit's
     * current reward rate.
     */
    private suspend fun insertAllocations(cashOutId: Long, shares: List<WaterfallShare>) {
        shares.forEach { share ->
            cashOutAllocationDao.insert(
                CashOutAllocation(
                    cashOutId = cashOutId,
                    habitId = share.habit.id,
                    amount = share.amount,
                    exerciseRewardAtTime = share.habit.rewardValue
                )
            )
        }
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
