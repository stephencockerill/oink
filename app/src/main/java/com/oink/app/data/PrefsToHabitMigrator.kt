package com.oink.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDate

/**
 * One-time, idempotent startup migration copying per-habit settings that were
 * stored as DataStore singletons onto the seeded default habit row
 * (id = [HabitRepository.DEFAULT_HABIT_ID]).
 *
 * A Room migration cannot read DataStore, so this runs from startup code rather
 * than as an [androidx.room.migration.Migration]. It copies, exactly once:
 * - daily reward -> `habits.rewardValue`
 * - available freezes -> `habits.availableFreezes`
 * - total freeze spending -> `habits.totalFreezeSpending`
 * - each frozen date -> one `frozen_days` row
 *
 * Idempotency and atomicity come from doing everything inside a single DataStore
 * [edit] transform. DataStore serializes transforms and commits only if the
 * block completes without throwing:
 * - The run-once flag [OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] is checked
 *   first; a completed migration bails immediately.
 * - The Room writes happen inside the block. If any throws, the transform throws
 *   and DataStore never commits the flag, so the next cold start retries.
 * - The flag is set last, in the same transform, so it is committed together
 *   with a fully applied copy.
 *
 * The Room writes are individually idempotent, so a partial run that failed
 * before setting the flag converges on retry: the habit update writes absolute
 * values (re-running cannot double freeze spending) and frozen-day inserts use
 * [androidx.room.OnConflictStrategy.IGNORE] (re-running cannot duplicate rows).
 */
class PrefsToHabitMigrator(
    private val dataStore: DataStore<Preferences>,
    private val habitDao: HabitDao,
    private val frozenDayDao: FrozenDayDao
) {

    /**
     * Run the copy if it hasn't already succeeded. Safe to call on every launch.
     */
    suspend fun migrateIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true) {
                return@edit
            }

            val reward = prefs[OinkPreferenceKeys.DAILY_REWARD]
                ?: PreferencesRepository.DEFAULT_DAILY_REWARD
            val availableFreezes = prefs[LEGACY_AVAILABLE_FREEZES] ?: 0
            val totalFreezeSpending = prefs[LEGACY_TOTAL_FREEZE_SPENDING] ?: 0L
            val frozenEpochDays = prefs[LEGACY_FROZEN_DATES] ?: emptySet()

            // A fresh install at v4 has no seeded habit: MIGRATION_3_4 seeds
            // id = 1 only on upgrade. The update then touches zero rows and there
            // is nothing meaningful to copy (only default preferences exist), so
            // skip the child inserts but still set the flag below so this does
            // not retry on every launch.
            val habitRowsUpdated = habitDao.applyMigratedPreferences(
                id = HabitRepository.DEFAULT_HABIT_ID,
                rewardValue = reward,
                availableFreezes = availableFreezes,
                totalFreezeSpending = totalFreezeSpending
            )

            if (habitRowsUpdated > 0) {
                frozenEpochDays.forEach { epochDayString ->
                    val epochDay = epochDayString.toLongOrNull() ?: return@forEach
                    frozenDayDao.insert(
                        FrozenDay(
                            habitId = HabitRepository.DEFAULT_HABIT_ID,
                            date = LocalDate.ofEpochDay(epochDay)
                        )
                    )
                }
            }

            prefs[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] = true
        }
    }

    companion object {
        /**
         * DataStore keys for the freeze state that predates per-habit storage.
         * This migrator is their only remaining reader - it drains them onto the
         * habit row - so they are defined here rather than in [OinkPreferenceKeys].
         * The names must match the strings the legacy app wrote.
         *
         * Internal so the migrator's test can seed legacy values without
         * duplicating the key names.
         */
        internal val LEGACY_AVAILABLE_FREEZES = intPreferencesKey("available_freezes")
        internal val LEGACY_FROZEN_DATES = stringSetPreferencesKey("frozen_dates")
        internal val LEGACY_TOTAL_FREEZE_SPENDING = longPreferencesKey("total_freeze_spending")
    }
}
