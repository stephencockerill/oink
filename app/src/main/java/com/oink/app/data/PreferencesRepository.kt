package com.oink.app.data

import kotlinx.coroutines.flow.Flow

/**
 * User preferences data class.
 *
 * Covers app-wide settings only. Per-habit state (the reward, streak freezes,
 * freeze spending) lives on the [Habit] row - the reward via [Habit.rewardValue],
 * freezes via [FreezeRepository].
 */
data class UserPreferences(
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20, // Default: 8 PM
    val reminderMinute: Int = 0
)

/**
 * Repository for managing app-wide user preferences.
 *
 * This is an interface so that ViewModels and other repositories depend on
 * an abstraction rather than the DataStore-backed implementation. That keeps
 * them testable: production wires up [DataStorePreferencesRepository], while
 * tests inject FakePreferencesRepository with no Android Context or on-disk
 * DataStore.
 *
 * The per-day reward is not here; it lives on the [Habit] row
 * ([Habit.rewardValue]) and is read via [HabitRewardProvider].
 */
interface PreferencesRepository {

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
     * Whether a private-area PIN has been configured. Emits a new value when a
     * PIN is first set. Deliberately exposes only existence, never the hash, so
     * it is safe to observe from the UI.
     */
    val hasPin: Flow<Boolean>

    /**
     * The stored PIN hash (with its salt and iteration count), or null if none
     * is set. Read only for verification; the plaintext PIN is never persisted.
     */
    suspend fun getHashedPin(): PinHasher.HashedPin?

    /**
     * Store the private-area PIN as its hash, salt, and iteration count.
     */
    suspend fun setPin(hashed: PinHasher.HashedPin)
}
