package com.oink.app.data

import kotlinx.coroutines.flow.Flow

/**
 * User preferences data class.
 *
 * Covers app-wide settings only. Per-habit state (streak freezes, freeze
 * spending) lives on the [Habit] row and is served by [FreezeRepository].
 */
data class UserPreferences(
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20, // Default: 8 PM
    val reminderMinute: Int = 0,
    val exerciseReward: Long = 500L // How much you earn per workout, in cents (default $5.00)
)

/**
 * Repository for managing user preferences.
 *
 * This is an interface so that ViewModels and other repositories depend on
 * an abstraction rather than the DataStore-backed implementation. That keeps
 * them testable: production wires up [DataStorePreferencesRepository], while
 * tests inject FakePreferencesRepository with no Android Context or on-disk
 * DataStore.
 *
 * Extends [ExerciseRewardProvider] so a single implementation also satisfies the
 * narrower contract consumed by CheckInRepository.
 */
interface PreferencesRepository : ExerciseRewardProvider {

    companion object {
        const val MAX_FREEZES = 2
        const val DEFAULT_EXERCISE_REWARD = 500L // cents ($5.00)

        // Common reward amount options for the settings UI, in cents
        val REWARD_OPTIONS = listOf(100L, 200L, 500L, 1000L, 2000L)
    }

    /**
     * Flow of user preferences.
     * Emits a new value whenever preferences change.
     */
    val userPreferences: Flow<UserPreferences>

    /**
     * Enable or disable reminders.
     */
    suspend fun setRemindersEnabled(enabled: Boolean)

    /**
     * Set the reminder time.
     */
    suspend fun setReminderTime(hour: Int, minute: Int)

    /**
     * Update all reminder settings at once.
     */
    suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int)

    /**
     * Set the exercise reward amount, in cents.
     * This affects how much you earn per workout.
     */
    suspend fun setExerciseReward(amount: Long)
}
