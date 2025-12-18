package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Fake implementation of PreferencesRepository for testing.
 *
 * This replaces the real PreferencesRepository which depends on Android's DataStore.
 * Uses in-memory storage that behaves identically to the real implementation.
 *
 * Note: This doesn't extend PreferencesRepository because that class requires
 * Context. Instead, it implements the same interface (ExerciseRewardProvider)
 * and provides all the same methods.
 */
class FakePreferencesRepository(
    initialExerciseReward: Double = PreferencesRepository.DEFAULT_EXERCISE_REWARD,
    initialFreezes: Int = 0,
    initialFrozenDates: Set<LocalDate> = emptySet(),
    initialFreezeSpending: Double = 0.0
) : CashOutPreferencesProvider {

    // Internal state
    private val _exerciseReward = MutableStateFlow(initialExerciseReward)
    private val _availableFreezes = MutableStateFlow(initialFreezes)
    private val _frozenDates = MutableStateFlow(initialFrozenDates)
    private val _totalFreezeSpending = MutableStateFlow(initialFreezeSpending)
    private val _remindersEnabled = MutableStateFlow(false)
    private val _reminderHour = MutableStateFlow(20)
    private val _reminderMinute = MutableStateFlow(0)

    /**
     * Flow of total freeze spending.
     */
    val totalFreezeSpending: Flow<Double> = _totalFreezeSpending

    /**
     * Flow of user preferences (mirrors real implementation).
     */
    val userPreferences: Flow<UserPreferences> = _exerciseReward.map { reward ->
        UserPreferences(
            remindersEnabled = _remindersEnabled.value,
            reminderHour = _reminderHour.value,
            reminderMinute = _reminderMinute.value,
            availableFreezes = _availableFreezes.value,
            frozenDates = _frozenDates.value,
            exerciseReward = reward
        )
    }

    // ============================================================
    // Exercise Reward
    // ============================================================

    override suspend fun getExerciseReward(): Double {
        return _exerciseReward.value
    }

    suspend fun setExerciseReward(amount: Double) {
        _exerciseReward.value = amount.coerceAtLeast(0.01)
    }

    suspend fun getFreezeCost(): Double {
        return getExerciseReward() * 2
    }

    // ============================================================
    // Freezes
    // ============================================================

    suspend fun getAvailableFreezes(): Int {
        return _availableFreezes.value
    }

    suspend fun setAvailableFreezes(count: Int) {
        _availableFreezes.value = count.coerceIn(0, PreferencesRepository.MAX_FREEZES)
    }

    suspend fun purchaseFreeze(): Boolean {
        val current = _availableFreezes.value
        if (current >= PreferencesRepository.MAX_FREEZES) {
            return false
        }
        _availableFreezes.value = current + 1
        return true
    }

    suspend fun useFreeze(date: LocalDate): Boolean {
        if (_availableFreezes.value <= 0) {
            return false
        }
        _availableFreezes.update { it - 1 }
        _frozenDates.update { it + date }
        return true
    }

    suspend fun getFrozenDates(): Set<LocalDate> {
        return _frozenDates.value
    }

    suspend fun isDateFrozen(date: LocalDate): Boolean {
        return _frozenDates.value.contains(date)
    }

    // ============================================================
    // Freeze Spending Tracking
    // ============================================================

    override suspend fun getTotalFreezeSpending(): Double {
        return _totalFreezeSpending.value
    }

    override suspend fun addFreezeSpending(amount: Double) {
        _totalFreezeSpending.update { it + amount }
    }

    // ============================================================
    // Reminders
    // ============================================================

    suspend fun setRemindersEnabled(enabled: Boolean) {
        _remindersEnabled.value = enabled
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        _reminderHour.value = hour
        _reminderMinute.value = minute
    }

    suspend fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
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
        exerciseReward: Double = PreferencesRepository.DEFAULT_EXERCISE_REWARD,
        freezes: Int = 0,
        frozenDates: Set<LocalDate> = emptySet(),
        freezeSpending: Double = 0.0
    ) {
        _exerciseReward.value = exerciseReward
        _availableFreezes.value = freezes
        _frozenDates.value = frozenDates
        _totalFreezeSpending.value = freezeSpending
        _remindersEnabled.value = false
        _reminderHour.value = 20
        _reminderMinute.value = 0
    }
}
