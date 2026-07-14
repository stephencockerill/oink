package com.oink.app.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Repository for managing cash-out operations.
 *
 * This is where the REWARD magic happens! When a user has earned
 * enough through consistent check-ins, they can treat themselves.
 *
 * Key psychological framing:
 * - Cash-outs are CELEBRATIONS, not losses
 * - "You EARNED this!" energy
 * - Show how many completed days it took to earn this reward
 *
 * IMPORTANT: Cash-outs are tracked separately from check-in balances.
 * We do NOT modify check-in records when cashing out. Instead we record the
 * cash-out and its per-habit allocations, and each habit's spendable balance is
 * derived as:
 *   (raw check-in balance) - (that habit's cash-out allocations) - (that habit's freeze spending)
 *
 * This prevents the nasty bug where toggling a check-in between "completed" and
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
     * Flow of all cash-outs (most recent first), with no visibility gating.
     *
     * This is the unlocked view: it includes every cash-out regardless of which
     * habits funded it. Locked surfaces read [visibleCashOuts] instead.
     */
    val allCashOuts: Flow<List<CashOut>> = cashOutDao.getAllCashOutsFlow()

    /**
     * Flow of total amount cashed out all-time (ungated, matches [allCashOuts]).
     */
    val totalCashedOut: Flow<Long> = cashOutDao.getTotalCashedOutFlow()

    /**
     * The cash-outs safe to show on a locked (public) surface.
     *
     * A claim can draw from private habits while the private area is unlocked, so
     * a cash-out with any allocation to a *currently* private habit is hidden -
     * showing it would betray that private habits exist and how much they funded.
     * The taint predicate is on the habit's live [Habit.isPrivate], so flipping a
     * habit to private hides its past cash-outs immediately and flipping it back
     * reveals them again.
     *
     * Per-habit balances are never gated: this only filters history. Combining
     * over [HabitRepository.allHabits] means the visible set re-derives whenever
     * a habit's privacy, or the set of allocations, changes.
     */
    val visibleCashOuts: Flow<List<CashOut>> = combine(
        cashOutDao.getAllCashOutsFlow(),
        cashOutAllocationDao.getAllFlow(),
        habitRepository.allHabits
    ) { cashOuts, allocations, habits ->
        val privateHabitIds = habits.filter { it.isPrivate }.map { it.id }.toSet()
        val taintedCashOutIds = allocations
            .filter { it.habitId in privateHabitIds }
            .map { it.cashOutId }
            .toSet()
        cashOuts.filter { it.id !in taintedCashOutIds }
    }

    /**
     * Total cents cashed out across the [visibleCashOuts] only - the locked-view
     * analogue of [totalCashedOut].
     */
    val visibleTotalCashedOut: Flow<Long> = visibleCashOuts.map { visible ->
        visible.sumOf { it.amount }
    }

    /**
     * Observe the shared pot: the sum of every in-scope habit's spendable
     * balance.
     *
     * Scope tracks unlock state: when the private area is locked
     * ([includePrivate] false) the pot spans public habits only; when unlocked
     * ([includePrivate] true) it spans public and private habits, so a claim can
     * draw from private banks. This is the same waterfall, just over a larger
     * set.
     *
     * Rebuilds whenever the set of habits changes ([HabitRepository.allHabits]),
     * then combines each in-scope habit's spendable flow. Combining over an empty
     * iterable never emits, so an empty in-scope set is short-circuited to a flow
     * of 0.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun pot(includePrivate: Boolean): Flow<Long> = habitRepository.allHabits.flatMapLatest { habits ->
        val inScope = if (includePrivate) habits else habits.filter { !it.isPrivate }
        if (inScope.isEmpty()) {
            flowOf(0L)
        } else {
            combine(inScope.map { habit -> spendable(habit.id) }) { shares -> shares.sum() }
        }
    }

    /**
     * One-shot read of the shared pot for non-Flow callers (the widget).
     *
     * The widget runs in the app process with no access to the unlock holder, so
     * it always reads the locked public pot ([includePrivate] false): a private
     * or mixed claim never changes the public spendable it displays.
     */
    suspend fun potOnce(includePrivate: Boolean): Long =
        spendablesFor(resolveScope(emptySet(), includePrivate)).sumOf { it.spendable }

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
     *
     * This is the per-habit share that [pot] sums over public habits. The habit
     * detail screen shows this for its own habit, while the home list shows the
     * pot as the shared total.
     */
    fun spendable(habitId: Long): Flow<Long> = combine(
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
     * @param scope The habits the pot is drawn from. Empty defers to
     *   [includePrivate]; a non-empty set means exactly those habit ids that
     *   exist (and ignores [includePrivate]).
     * @param includePrivate When [scope] is empty, whether private habits join
     *   the pot. False (locked) draws from public habits only; true (unlocked)
     *   draws from public and private habits.
     * @return The CashOut record, or null if the amount is not in `0 < amount <= pot`
     */
    suspend fun cashOut(
        name: String,
        amount: Long,
        emoji: String = "🎁",
        scope: Set<Long> = emptySet(),
        includePrivate: Boolean = false
    ): CashOut? {
        val spendables = spendablesFor(resolveScope(scope, includePrivate))
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
     * Calculate total completed days represented by all cash-outs.
     *
     * Sums per allocation: each allocation's cents divided by the habit's reward
     * rate captured when the allocation was written ([CashOutAllocation.rewardAtTime]),
     * so habits with different reward rates each count correctly. Allocations
     * whose captured reward is non-positive contribute zero rather than dividing
     * by zero.
     */
    suspend fun getTotalDaysRewarded(): Int {
        return cashOutAllocationDao.getAll().sumOf { allocation ->
            if (allocation.rewardAtTime <= 0L) {
                0
            } else {
                (allocation.amount / allocation.rewardAtTime).toInt()
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
     * amount edit re-runs the waterfall at CURRENT balances over the in-scope
     * habits: the cash-out's own allocations are added back to availability
     * first (they are about to be replaced), the new split is validated against
     * that pot, and only then is anything mutated. Validating before mutating
     * means the operation is safe even against the in-memory fakes, which do not
     * roll back.
     *
     * Scope tracks the private-area unlock state, mirroring [cashOut]: while
     * locked ([includePrivate] false) the re-split spans public habits only;
     * while unlocked ([includePrivate] true) it also spans private habits, so
     * editing the amount of a private- or mixed-funded claim re-splits over the
     * banks that funded it rather than shifting a private debit onto public
     * habits.
     *
     * @param cashOut The updated cash-out record
     * @param includePrivate On an amount edit, whether private habits join the
     *   re-split pot. False (locked) re-splits over public habits only; true
     *   (unlocked) re-splits over public and private habits.
     * @return true if the update was applied; false for a non-existent id or an
     *   amount edit that does not fit the pot (`newAmount` outside `0 < x <= pot`)
     */
    suspend fun updateCashOut(cashOut: CashOut, includePrivate: Boolean = false): Boolean {
        val existing = cashOutDao.getById(cashOut.id) ?: return false

        if (existing.amount == cashOut.amount) {
            // Name/emoji only: no allocation churn.
            cashOutDao.update(cashOut)
            return true
        }

        // Amount changed: re-split at current balances over the in-scope habits,
        // treating this cash-out's own allocations as available again. Unlock
        // state decides whether private habits are in scope.
        val spendables = spendablesFor(
            resolveScope(emptySet(), includePrivate),
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
     * An empty scope defers to [includePrivate]: false yields public habits
     * ([Habit.isPrivate] false), true yields every habit (public and private). A
     * non-empty scope means exactly those habit ids that exist, ignoring
     * [includePrivate]; unknown ids are dropped.
     */
    private suspend fun resolveScope(scope: Set<Long>, includePrivate: Boolean): List<Habit> {
        return if (scope.isEmpty()) {
            habitRepository.getAllHabits().filter { includePrivate || !it.isPrivate }
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
                    rewardAtTime = share.habit.rewardValue
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
