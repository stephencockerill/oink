package com.oink.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * DataStore instance for app preferences.
 * Created as an extension property on Context for easy access.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oink_preferences"
)

/**
 * User preferences data class.
 */
data class UserPreferences(
    val remindersEnabled: Boolean = false,
    val reminderHour: Int = 20, // Default: 8 PM
    val reminderMinute: Int = 0,
    val availableFreezes: Int = 0, // Streak freezes the user has
    val frozenDates: Set<LocalDate> = emptySet(), // Days that were frozen
    val exerciseReward: Double = 5.0 // How much you earn per workout (default $5)
) {
    /**
     * Freeze cost is always 2x the exercise reward.
     * If you earn $5 per workout, a freeze costs $10 (2 workouts worth).
     */
    val freezeCost: Double get() = exerciseReward * 2
}

/**
 * Repository for managing user preferences.
 *
 * Uses DataStore (not SharedPreferences) because:
 * 1. DataStore is async and doesn't block the UI thread
 * 2. It's type-safe with Kotlin coroutines/Flow
 * 3. It handles errors gracefully
 * 4. SharedPreferences is legacy garbage
 */
class PreferencesRepository(private val context: Context) : CashOutPreferencesProvider {

    companion object {
        const val MAX_FREEZES = 2
        const val DEFAULT_EXERCISE_REWARD = 5.0

        // Common reward amount options for the settings UI
        val REWARD_OPTIONS = listOf(1.0, 2.0, 5.0, 10.0, 20.0)
    }

    private object Keys {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val AVAILABLE_FREEZES = intPreferencesKey("available_freezes")
        val FROZEN_DATES = stringSetPreferencesKey("frozen_dates") // Store as epoch day strings
        val EXERCISE_REWARD = doublePreferencesKey("exercise_reward")
        val TOTAL_FREEZE_SPENDING = doublePreferencesKey("total_freeze_spending")
    }

    /**
     * Flow of user preferences.
     * Emits a new value whenever preferences change.
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val frozenDatesStrings = prefs[Keys.FROZEN_DATES] ?: emptySet()
        val frozenDates = frozenDatesStrings.mapNotNull { epochStr ->
            try {
                LocalDate.ofEpochDay(epochStr.toLong())
            } catch (e: Exception) {
                null
            }
        }.toSet()

        UserPreferences(
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
            availableFreezes = prefs[Keys.AVAILABLE_FREEZES] ?: 0,
            frozenDates = frozenDates,
            exerciseReward = prefs[Keys.EXERCISE_REWARD] ?: DEFAULT_EXERCISE_REWARD
        )
    }

    /**
     * Get current available freezes count.
     */
    suspend fun getAvailableFreezes(): Int {
        return context.dataStore.data.first()[Keys.AVAILABLE_FREEZES] ?: 0
    }

    /**
     * Get frozen dates.
     */
    suspend fun getFrozenDates(): Set<LocalDate> {
        val strings = context.dataStore.data.first()[Keys.FROZEN_DATES] ?: emptySet()
        return strings.mapNotNull { epochStr ->
            try {
                LocalDate.ofEpochDay(epochStr.toLong())
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    /**
     * Check if a specific date is frozen.
     */
    suspend fun isDateFrozen(date: LocalDate): Boolean {
        return getFrozenDates().contains(date)
    }

    /**
     * Purchase a streak freeze (adds to available freezes).
     * Returns true if successful, false if already at max.
     */
    suspend fun purchaseFreeze(): Boolean {
        val current = getAvailableFreezes()
        if (current >= MAX_FREEZES) {
            return false
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.AVAILABLE_FREEZES] = current + 1
        }
        return true
    }

    /**
     * Use a freeze for a specific date.
     * Returns true if successful, false if no freezes available.
     */
    suspend fun useFreeze(date: LocalDate): Boolean {
        val available = getAvailableFreezes()
        if (available <= 0) {
            return false
        }

        context.dataStore.edit { prefs ->
            // Decrement available freezes
            prefs[Keys.AVAILABLE_FREEZES] = available - 1

            // Add date to frozen dates
            val currentFrozen = prefs[Keys.FROZEN_DATES] ?: emptySet()
            prefs[Keys.FROZEN_DATES] = currentFrozen + date.toEpochDay().toString()
        }
        return true
    }

    /**
     * Set the number of available freezes directly.
     */
    suspend fun setAvailableFreezes(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AVAILABLE_FREEZES] = count.coerceIn(0, MAX_FREEZES)
        }
    }

    /**
     * Enable or disable reminders.
     */
    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDERS_ENABLED] = enabled
        }
    }

    /**
     * Set the reminder time.
     */
    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
        }
    }

    /**
     * Update all reminder settings at once.
     */
    suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDERS_ENABLED] = enabled
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
        }
    }

    /**
     * Get the current exercise reward amount.
     * Implements ExerciseRewardProvider interface.
     */
    override suspend fun getExerciseReward(): Double {
        return context.dataStore.data.first()[Keys.EXERCISE_REWARD] ?: DEFAULT_EXERCISE_REWARD
    }

    /**
     * Set the exercise reward amount.
     * This affects how much you earn per workout.
     */
    suspend fun setExerciseReward(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXERCISE_REWARD] = amount.coerceAtLeast(0.01) // Minimum 1 cent
        }
    }

    /**
     * Get the freeze cost (2x exercise reward).
     */
    suspend fun getFreezeCost(): Double {
        return getExerciseReward() * 2
    }

    // ============================================================
    // DEDUCTION TRACKING
    // ============================================================
    // We track deductions (like freeze costs) separately from check-in balances.
    // This ensures that when a check-in is toggled/recalculated, we don't
    // lose track of money that was spent on freezes.
    //
    // Cash-outs are already tracked in their own table (CashOut).
    // Freeze spending needs to be tracked here since it's not in a table.
    // ============================================================

    /**
     * Flow of total freeze spending.
     * Use this for reactive UI updates.
     */
    val totalFreezeSpending: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOTAL_FREEZE_SPENDING] ?: 0.0
    }

    /**
     * Get total amount spent on freezes (one-time query).
     */
    override suspend fun getTotalFreezeSpending(): Double {
        return context.dataStore.data.first()[Keys.TOTAL_FREEZE_SPENDING] ?: 0.0
    }

    /**
     * Add to the total freeze spending.
     * Called when a freeze is USED (not when it's acquired).
     */
    override suspend fun addFreezeSpending(amount: Double) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_FREEZE_SPENDING] ?: 0.0
            prefs[Keys.TOTAL_FREEZE_SPENDING] = current + amount
        }
    }
}

