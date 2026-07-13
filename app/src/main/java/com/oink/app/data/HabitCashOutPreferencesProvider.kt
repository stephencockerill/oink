package com.oink.app.data

/**
 * Production [CashOutPreferencesProvider] backed by per-habit state.
 *
 * Cash-out balance math (see [CashOutRepository] and [DefaultDeductionProvider])
 * is still single-habit, so this binds a [FreezeRepository] to one habit
 * ([habitId], defaulting to [HabitRepository.DEFAULT_HABIT_ID]) and delegates the
 * exercise reward to the app-wide [ExerciseRewardProvider]. When cash-out is made
 * habit-aware, callers pass the real habit instead of the default.
 */
class HabitCashOutPreferencesProvider(
    private val freezeRepository: FreezeRepository,
    private val exerciseRewardProvider: ExerciseRewardProvider,
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID
) : CashOutPreferencesProvider {

    override suspend fun getExerciseReward(): Long =
        exerciseRewardProvider.getExerciseReward()

    override suspend fun getTotalFreezeSpending(): Long =
        freezeRepository.getTotalFreezeSpending(habitId)

    override suspend fun addFreezeSpending(amount: Long) =
        freezeRepository.addFreezeSpending(habitId, amount)
}
