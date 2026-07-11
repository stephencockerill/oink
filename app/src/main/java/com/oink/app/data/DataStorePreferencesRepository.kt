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
 * DataStore-backed implementation of [PreferencesRepository].
 *
 * Uses DataStore (not SharedPreferences) because:
 * 1. DataStore is async and doesn't block the UI thread
 * 2. It's type-safe with Kotlin coroutines/Flow
 * 3. It handles errors gracefully
 * 4. SharedPreferences is legacy garbage
 */
class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    private object Keys {
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val AVAILABLE_FREEZES = intPreferencesKey("available_freezes")
        val FROZEN_DATES = stringSetPreferencesKey("frozen_dates") // Store as epoch day strings
        val EXERCISE_REWARD = doublePreferencesKey("exercise_reward")
        val TOTAL_FREEZE_SPENDING = doublePreferencesKey("total_freeze_spending")
    }

    override val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
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
            exerciseReward = prefs[Keys.EXERCISE_REWARD] ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD
        )
    }

    override suspend fun getAvailableFreezes(): Int {
        return context.dataStore.data.first()[Keys.AVAILABLE_FREEZES] ?: 0
    }

    override suspend fun getFrozenDates(): Set<LocalDate> {
        val strings = context.dataStore.data.first()[Keys.FROZEN_DATES] ?: emptySet()
        return strings.mapNotNull { epochStr ->
            try {
                LocalDate.ofEpochDay(epochStr.toLong())
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }

    override suspend fun isDateFrozen(date: LocalDate): Boolean {
        return getFrozenDates().contains(date)
    }

    override suspend fun purchaseFreeze(): Boolean {
        val current = getAvailableFreezes()
        if (current >= PreferencesRepository.MAX_FREEZES) {
            return false
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.AVAILABLE_FREEZES] = current + 1
        }
        return true
    }

    override suspend fun useFreeze(date: LocalDate): Boolean {
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

    override suspend fun setAvailableFreezes(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AVAILABLE_FREEZES] = count.coerceIn(0, PreferencesRepository.MAX_FREEZES)
        }
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDERS_ENABLED] = enabled
        }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
        }
    }

    override suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
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
        return context.dataStore.data.first()[Keys.EXERCISE_REWARD] ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD
    }

    override suspend fun setExerciseReward(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EXERCISE_REWARD] = amount.coerceAtLeast(0.01) // Minimum 1 cent
        }
    }

    override suspend fun getFreezeCost(): Double {
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

    override val totalFreezeSpending: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOTAL_FREEZE_SPENDING] ?: 0.0
    }

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
