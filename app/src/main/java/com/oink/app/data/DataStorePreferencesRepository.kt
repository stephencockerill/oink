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
            reminderMinute = prefs[OinkPreferenceKeys.REMINDER_MINUTE] ?: 0
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

    override val hasPin: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[OinkPreferenceKeys.PIN_HASH] != null
    }

    override suspend fun getHashedPin(): PinHasher.HashedPin? {
        val prefs = context.dataStore.data.first()
        val hash = prefs[OinkPreferenceKeys.PIN_HASH] ?: return null
        val salt = prefs[OinkPreferenceKeys.PIN_SALT] ?: return null
        val iterations = prefs[OinkPreferenceKeys.PIN_ITERATIONS] ?: return null
        return PinHasher.HashedPin(hashBase64 = hash, saltBase64 = salt, iterations = iterations)
    }

    override suspend fun setPin(hashed: PinHasher.HashedPin) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.PIN_HASH] = hashed.hashBase64
            prefs[OinkPreferenceKeys.PIN_SALT] = hashed.saltBase64
            prefs[OinkPreferenceKeys.PIN_ITERATIONS] = hashed.iterations
        }
    }
}
