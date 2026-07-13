package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Fake implementation of [PreferencesRepository] for testing.
 *
 * This replaces [DataStorePreferencesRepository], which depends on Android's
 * DataStore. Uses in-memory storage that behaves identically to the real
 * implementation, so it can be injected into repositories and ViewModels
 * without any Android Context or on-disk state.
 *
 * Covers app-wide settings only. The per-day reward lives on the [Habit] row
 * ([Habit.rewardValue]); per-habit freeze state lives in [FreezeRepository].
 */
class FakePreferencesRepository : PreferencesRepository {

    // Internal state
    private val _remindersEnabled = MutableStateFlow(false)
    private val _reminderHour = MutableStateFlow(20)
    private val _reminderMinute = MutableStateFlow(0)

    /**
     * Flow of user preferences (mirrors real implementation).
     *
     * Combines every backing flow so it re-emits on any preference change,
     * matching the DataStore-backed implementation.
     */
    override val userPreferences: Flow<UserPreferences> = combine(
        _remindersEnabled,
        _reminderHour,
        _reminderMinute
    ) { remindersEnabled, hour, minute ->
        UserPreferences(
            remindersEnabled = remindersEnabled,
            reminderHour = hour,
            reminderMinute = minute
        )
    }

    // ============================================================
    // Reminders
    // ============================================================

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        _remindersEnabled.value = enabled
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        _reminderHour.value = hour
        _reminderMinute.value = minute
    }

    override suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        _remindersEnabled.value = enabled
        _reminderHour.value = hour
        _reminderMinute.value = minute
    }

    // ============================================================
    // Test Helpers
    // ============================================================

    /**
     * Reset all state to defaults. Useful between tests.
     */
    fun reset() {
        _remindersEnabled.value = false
        _reminderHour.value = 20
        _reminderMinute.value = 0
    }
}
