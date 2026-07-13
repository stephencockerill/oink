package com.oink.app.data

/**
 * Production [CashOutPreferencesProvider] backed by per-habit state.
 *
 * Cash-out balance math (see [CashOutRepository]) is single-habit, so this binds
 * a [FreezeRepository] to one habit ([habitId], defaulting to
 * [HabitRepository.DEFAULT_HABIT_ID]). When cash-out is made habit-aware, callers
 * pass the real habit instead of the default.
 */
class HabitCashOutPreferencesProvider(
    private val freezeRepository: FreezeRepository,
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID
) : CashOutPreferencesProvider {

    override suspend fun getTotalFreezeSpending(): Long =
        freezeRepository.getTotalFreezeSpending(habitId)

    override suspend fun addFreezeSpending(amount: Long) =
        freezeRepository.addFreezeSpending(habitId, amount)
}
