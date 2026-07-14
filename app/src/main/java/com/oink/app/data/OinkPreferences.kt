package com.oink.app.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single DataStore instance backing app preferences.
 *
 * DataStore permits only one instance per file name ("oink_preferences"), so
 * this extension is declared exactly once for the whole process and shared by
 * every consumer - [DataStorePreferencesRepository] and [PrefsToHabitMigrator] -
 * rather than each declaring its own.
 *
 * [DailyRewardKeyMigration] runs before any read, so consumers only ever see
 * the current [OinkPreferenceKeys.DAILY_REWARD] key.
 */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "oink_preferences",
    produceMigrations = { listOf(DailyRewardKeyMigration) }
)

/**
 * Preference keys for [Context.dataStore], kept in one place so each key name is
 * defined once and shared by every reader and writer.
 */
internal object OinkPreferenceKeys {
    val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val DAILY_REWARD = longPreferencesKey("daily_reward")

    /**
     * Run-once guard for the one-time copy of per-habit preferences onto the
     * seeded habit row. Set only after [PrefsToHabitMigrator] finishes, so a
     * crashed run retries on the next cold start.
     */
    val PREFS_MIGRATED_TO_HABIT_V1 = booleanPreferencesKey("prefs_migrated_to_habit_v1")

    /**
     * Private-area PIN, stored as a PBKDF2 hash with its salt and iteration
     * count. The plaintext PIN is never written. See [PinHasher].
     */
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val PIN_ITERATIONS = intPreferencesKey("pin_iterations")

    /**
     * Security-question recovery. The prompt is the user's own question text,
     * stored in the clear so it can be shown back at recovery time; the answer is
     * a PBKDF2 hash of its normalized form (see [SecurityAnswer]). Present only
     * for users set up on a device with no biometric or lockscreen credential.
     */
    val SECURITY_QUESTION_PROMPT = stringPreferencesKey("security_question_prompt")
    val SECURITY_ANSWER_HASH = stringPreferencesKey("security_answer_hash")
    val SECURITY_ANSWER_SALT = stringPreferencesKey("security_answer_salt")
    val SECURITY_ANSWER_ITERATIONS = intPreferencesKey("security_answer_iterations")

    /**
     * Persisted counters for the [SecurityQuestionLimiter]. Survive process death
     * so the escalating lockout cannot be shed by force-killing the app.
     */
    val SECURITY_QUESTION_FAILURES = intPreferencesKey("security_question_failures")
    val SECURITY_QUESTION_LOCKOUT_COUNT = intPreferencesKey("security_question_lockout_count")
    val SECURITY_QUESTION_LOCKED_UNTIL = longPreferencesKey("security_question_locked_until")
}

/**
 * The daily-reward preference key string as it was originally written. Its value
 * is copied onto [OinkPreferenceKeys.DAILY_REWARD] and the old key dropped, so
 * an existing user's configured reward survives the rename with no data loss.
 */
private val LEGACY_DAILY_REWARD = longPreferencesKey("exercise_reward")

/**
 * Copies the legacy reward value onto [OinkPreferenceKeys.DAILY_REWARD] and
 * removes the old key.
 *
 * Idempotent: it runs whenever the legacy key is still present, only writes the
 * new key when it is not already set (so a value written under the new key is
 * never clobbered), and always drops the legacy key.
 */
internal val DailyRewardKeyMigration = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.contains(LEGACY_DAILY_REWARD)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutablePrefs = currentData.toMutablePreferences()
        val legacyValue = currentData[LEGACY_DAILY_REWARD]
        if (legacyValue != null && !currentData.contains(OinkPreferenceKeys.DAILY_REWARD)) {
            mutablePrefs[OinkPreferenceKeys.DAILY_REWARD] = legacyValue
        }
        mutablePrefs.remove(LEGACY_DAILY_REWARD)
        return mutablePrefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}
