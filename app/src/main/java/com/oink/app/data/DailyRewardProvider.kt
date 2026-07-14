package com.oink.app.data

/**
 * Interface for providing a habit's per-day reward amount.
 *
 * This exists to make CheckInRepository testable without depending on the
 * concrete Room-backed source. Following Android guidelines: "Reduce
 * dependencies on Android classes" and "Prefer fakes to mocks".
 *
 * In production: [HabitRewardProvider] reads [Habit.rewardValue], the single
 * source of truth for a habit's reward.
 * In tests: a simple fake implements this.
 */
fun interface DailyRewardProvider {
    /** The habit's per-day reward amount, in cents. */
    suspend fun getDailyReward(habitId: Long): Long
}

/**
 * Production [DailyRewardProvider] sourcing the reward from the habit row.
 *
 * [Habit.rewardValue] is the single source of truth for a habit's per-day
 * reward. Falls back to [PreferencesRepository.DEFAULT_DAILY_REWARD] when the
 * habit is absent so a missing row never zeroes out earnings.
 */
class HabitRewardProvider(
    private val habitDao: HabitDao
) : DailyRewardProvider {
    override suspend fun getDailyReward(habitId: Long): Long =
        habitDao.getById(habitId)?.rewardValue ?: PreferencesRepository.DEFAULT_DAILY_REWARD
}
