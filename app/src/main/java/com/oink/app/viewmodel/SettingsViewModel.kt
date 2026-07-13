package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.UserPreferences
import com.oink.app.notifications.NotificationHelper
import com.oink.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Why AndroidViewModel instead of ViewModel?
 * Because we need the Application context to:
 * 1. Schedule/cancel reminders with WorkManager
 * 2. Check notification permissions
 *
 * We use Application context (not Activity context) to avoid memory leaks.
 *
 * Preferences are accessed through the injected [PreferencesRepository]
 * interface (not a concrete DataStore-backed instance) so the ViewModel can
 * be tested with a fake.
 */
class SettingsViewModel(
    application: Application,
    private val preferencesRepository: PreferencesRepository,
    private val habitRepository: HabitRepository,
    private val freezeRepository: FreezeRepository
) : AndroidViewModel(application) {

    /**
     * The habit this screen operates on. The app is single-habit for now, so
     * the reward and every freeze read/write target the seeded default habit.
     */
    private val habitId: Long = HabitRepository.DEFAULT_HABIT_ID

    /**
     * User preferences as a StateFlow.
     */
    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    /**
     * This habit's per-day reward, in cents. Sourced from [Habit.rewardValue],
     * the single source of truth, so the chip selection reflects the stored
     * habit and edits round-trip through it.
     */
    val exerciseReward: StateFlow<Long> = habitRepository.habit(habitId)
        .map { it?.rewardValue ?: PreferencesRepository.DEFAULT_EXERCISE_REWARD }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_EXERCISE_REWARD
        )

    /**
     * Streak freezes banked for this habit.
     */
    val availableFreezes: StateFlow<Int> = freezeRepository.availableFreezes(habitId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * Check if we have notification permission.
     */
    fun hasNotificationPermission(): Boolean {
        return NotificationHelper.hasNotificationPermission(getApplication())
    }

    /**
     * Toggle reminders on/off.
     */
    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRemindersEnabled(enabled)

            if (enabled) {
                // Schedule reminder with current time settings
                val prefs = userPreferences.value
                ReminderScheduler.scheduleDailyReminder(
                    getApplication(),
                    prefs.reminderHour,
                    prefs.reminderMinute
                )
            } else {
                // Cancel any scheduled reminders
                ReminderScheduler.cancelReminder(getApplication())
            }
        }
    }

    /**
     * Update the reminder time.
     */
    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesRepository.setReminderTime(hour, minute)

            // If reminders are enabled, reschedule with new time
            if (userPreferences.value.remindersEnabled) {
                ReminderScheduler.scheduleDailyReminder(
                    getApplication(),
                    hour,
                    minute
                )
            }
        }
    }

    /**
     * Add a streak freeze to inventory.
     * Freezes are free to acquire, but cost 2x reward to USE.
     *
     * Results are observed through the [userPreferences] StateFlow.
     */
    fun acquireFreeze() {
        if (availableFreezes.value >= PreferencesRepository.MAX_FREEZES) {
            return
        }

        viewModelScope.launch {
            freezeRepository.purchaseFreeze(habitId)
        }
    }

    /**
     * Set this habit's per-day reward, in cents, on [Habit.rewardValue] - the
     * single source of truth. No-op when the habit does not exist. The
     * [exerciseReward] flow re-emits so the UI reflects the change.
     */
    fun setExerciseReward(amount: Long) {
        viewModelScope.launch {
            val habit = habitRepository.getHabit(habitId) ?: return@launch
            habitRepository.updateHabit(habit.copy(rewardValue = amount.coerceAtLeast(1L)))
        }
    }

    /**
     * Factory for creating SettingsViewModel with dependencies.
     *
     * Mirrors the pattern used by [MainViewModel] and [RewardsViewModel]:
     * the system needs to know how to recreate the ViewModel across
     * configuration changes, and this tells it how while allowing the
     * [PreferencesRepository] to be supplied (a fake in tests).
     */
    class Factory(
        private val application: Application,
        private val preferencesRepository: PreferencesRepository,
        private val habitRepository: HabitRepository,
        private val freezeRepository: FreezeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(application, preferencesRepository, habitRepository, freezeRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

