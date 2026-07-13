package com.oink.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single DataStore instance backing app preferences.
 *
 * DataStore permits only one instance per file name ("oink_preferences"), so
 * this extension is declared exactly once for the whole process and shared by
 * every consumer - [DataStorePreferencesRepository] and [PrefsToHabitMigrator] -
 * rather than each declaring its own.
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oink_preferences"
)

/**
 * Preference keys for [Context.dataStore], kept in one place so each key name is
 * defined once and shared by every reader and writer.
 */
internal object OinkPreferenceKeys {
    val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val AVAILABLE_FREEZES = intPreferencesKey("available_freezes")

    /** Frozen dates stored as epoch-day strings. */
    val FROZEN_DATES = stringSetPreferencesKey("frozen_dates")
    val EXERCISE_REWARD = longPreferencesKey("exercise_reward")
    val TOTAL_FREEZE_SPENDING = longPreferencesKey("total_freeze_spending")

    /**
     * Run-once guard for the one-time copy of per-habit preferences onto the
     * seeded habit row. Set only after [PrefsToHabitMigrator] finishes, so a
     * crashed run retries on the next cold start.
     */
    val PREFS_MIGRATED_TO_HABIT_V1 = booleanPreferencesKey("prefs_migrated_to_habit_v1")
}
