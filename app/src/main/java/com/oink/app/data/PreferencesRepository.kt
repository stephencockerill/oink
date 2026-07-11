package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * User preferences data class.
 */
data class UserPreferences(
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20, // Default: 8 PM
    val reminderMinute: Int = 0,
    val availableFreezes: Int = 0, // Streak freezes the user has
    val frozenDates: Set<LocalDate> = emptySet(), // Days that were frozen
    val exerciseReward: Long = 500L // How much you earn per workout, in cents (default $5.00)
) {
    /**
     * Freeze cost is always 2x the exercise reward.
     * If you earn $5 per workout, a freeze costs $10 (2 workouts worth).
     */
    val freezeCost: Long get() = exerciseReward * 2
}

/**
 * Repository for managing user preferences.
 *
 * This is an interface so that ViewModels and other repositories depend on
 * an abstraction rather than the DataStore-backed implementation. That keeps
 * them testable: production wires up [DataStorePreferencesRepository], while
 * tests inject FakePreferencesRepository with no Android Context or on-disk
 * DataStore.
 *
 * Extends [CashOutPreferencesProvider] (and therefore [ExerciseRewardProvider])
 * so a single implementation also satisfies the narrower contracts consumed by
 * CheckInRepository and CashOutRepository.
 */
interface PreferencesRepository : CashOutPreferencesProvider {

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
     * Flow of total freeze spending, in cents.
     * Use this for reactive UI updates.
     */
    val totalFreezeSpending: Flow<Long>

    /**
     * Get current available freezes count.
     */
    suspend fun getAvailableFreezes(): Int

    /**
     * Get frozen dates.
     */
    suspend fun getFrozenDates(): Set<LocalDate>

    /**
     * Check if a specific date is frozen.
     */
    suspend fun isDateFrozen(date: LocalDate): Boolean

    /**
     * Purchase a streak freeze (adds to available freezes).
     * Returns true if successful, false if already at max.
     */
    suspend fun purchaseFreeze(): Boolean

    /**
     * Use a freeze for a specific date.
     * Returns true if successful, false if no freezes available.
     */
    suspend fun useFreeze(date: LocalDate): Boolean

    /**
     * Set the number of available freezes directly.
     */
    suspend fun setAvailableFreezes(count: Int)

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

    /**
     * Get the freeze cost (2x exercise reward), in cents.
     */
    suspend fun getFreezeCost(): Long
}
