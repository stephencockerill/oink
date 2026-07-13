package com.oink.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [PreferencesRepository].
 *
 * Uses DataStore (not SharedPreferences) because:
 * 1. DataStore is async and doesn't block the UI thread
 * 2. It's type-safe with Kotlin coroutines/Flow
 * 3. It handles errors gracefully
 * 4. SharedPreferences is legacy garbage
 *
 * The single DataStore instance and its keys live in [OinkPreferences] so they
 * are shared with any other component that touches the same preferences.
 *
 * Streak-freeze state is per-habit and lives on the [Habit] row; see
 * [FreezeRepository]. It is not stored here.
 */
class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            remindersEnabled = prefs[OinkPreferenceKeys.REMINDERS_ENABLED] ?: false,
            reminderHour = prefs[OinkPreferenceKeys.REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[OinkPreferenceKeys.REMINDER_MINUTE] ?: 0,
            exerciseReward = prefs[OinkPreferenceKeys.EXERCISE_REWARD] ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD
        )
    }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.REMINDERS_ENABLED] = enabled
        }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.REMINDER_HOUR] = hour
            prefs[OinkPreferenceKeys.REMINDER_MINUTE] = minute
        }
    }

    override suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.REMINDERS_ENABLED] = enabled
            prefs[OinkPreferenceKeys.REMINDER_HOUR] = hour
            prefs[OinkPreferenceKeys.REMINDER_MINUTE] = minute
        }
    }

    /**
     * Get the current exercise reward amount.
     * Implements ExerciseRewardProvider interface.
     */
    override suspend fun getExerciseReward(): Long {
        return context.dataStore.data.first()[OinkPreferenceKeys.EXERCISE_REWARD] ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD
    }

    override suspend fun setExerciseReward(amount: Long) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.EXERCISE_REWARD] = amount.coerceAtLeast(1L) // Minimum 1 cent
        }
    }
}
