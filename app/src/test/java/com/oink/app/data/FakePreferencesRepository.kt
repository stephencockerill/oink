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
 * Per-habit freeze state is not here; see [FreezeRepository] and its fake DAOs.
 */
class FakePreferencesRepository(
    initialExerciseReward: Long = PreferencesRepository.DEFAULT_EXERCISE_REWARD
) : PreferencesRepository {

    // Internal state
    private val _exerciseReward = MutableStateFlow(initialExerciseReward)
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
        _exerciseReward,
        _remindersEnabled,
        _reminderHour,
        _reminderMinute
    ) { reward, remindersEnabled, hour, minute ->
        UserPreferences(
            remindersEnabled = remindersEnabled,
            reminderHour = hour,
            reminderMinute = minute,
            exerciseReward = reward
        )
    }

    // ============================================================
    // Exercise Reward
    // ============================================================

    override suspend fun getExerciseReward(): Long {
        return _exerciseReward.value
    }

    override suspend fun setExerciseReward(amount: Long) {
        _exerciseReward.value = amount.coerceAtLeast(1L)
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
    fun reset(
        exerciseReward: Long = PreferencesRepository.DEFAULT_EXERCISE_REWARD
    ) {
        _exerciseReward.value = exerciseReward
        _remindersEnabled.value = false
        _reminderHour.value = 20
        _reminderMinute.value = 0
    }
}
