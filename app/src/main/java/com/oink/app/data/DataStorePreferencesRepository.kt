package com.oink.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
 */
class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val frozenDatesStrings = prefs[OinkPreferenceKeys.FROZEN_DATES] ?: emptySet()
        val frozenDates = frozenDatesStrings.mapNotNull { epochStr ->
            try {
                LocalDate.ofEpochDay(epochStr.toLong())
            } catch (e: Exception) {
                null
            }
        }.toSet()

        UserPreferences(
            remindersEnabled = prefs[OinkPreferenceKeys.REMINDERS_ENABLED] ?: false,
            reminderHour = prefs[OinkPreferenceKeys.REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[OinkPreferenceKeys.REMINDER_MINUTE] ?: 0,
            availableFreezes = prefs[OinkPreferenceKeys.AVAILABLE_FREEZES] ?: 0,
            frozenDates = frozenDates,
            exerciseReward = prefs[OinkPreferenceKeys.EXERCISE_REWARD] ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD
        )
    }

    override suspend fun getAvailableFreezes(): Int {
        return context.dataStore.data.first()[OinkPreferenceKeys.AVAILABLE_FREEZES] ?: 0
    }

    override suspend fun getFrozenDates(): Set<LocalDate> {
        val strings = context.dataStore.data.first()[OinkPreferenceKeys.FROZEN_DATES] ?: emptySet()
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
            prefs[OinkPreferenceKeys.AVAILABLE_FREEZES] = current + 1
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
            prefs[OinkPreferenceKeys.AVAILABLE_FREEZES] = available - 1

            // Add date to frozen dates
            val currentFrozen = prefs[OinkPreferenceKeys.FROZEN_DATES] ?: emptySet()
            prefs[OinkPreferenceKeys.FROZEN_DATES] = currentFrozen + date.toEpochDay().toString()
        }
        return true
    }

    override suspend fun setAvailableFreezes(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.AVAILABLE_FREEZES] = count.coerceIn(0, PreferencesRepository.MAX_FREEZES)
        }
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

    override suspend fun getFreezeCost(): Long {
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

    override val totalFreezeSpending: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[OinkPreferenceKeys.TOTAL_FREEZE_SPENDING] ?: 0L
    }

    override suspend fun getTotalFreezeSpending(): Long {
        return context.dataStore.data.first()[OinkPreferenceKeys.TOTAL_FREEZE_SPENDING] ?: 0L
    }

    /**
     * Add to the total freeze spending, in cents.
     * Called when a freeze is USED (not when it's acquired).
     */
    override suspend fun addFreezeSpending(amount: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[OinkPreferenceKeys.TOTAL_FREEZE_SPENDING] ?: 0L
            prefs[OinkPreferenceKeys.TOTAL_FREEZE_SPENDING] = current + amount
        }
    }
}
