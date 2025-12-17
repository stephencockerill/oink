package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.UserPreferences
import com.oink.app.notifications.NotificationHelper
import com.oink.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Why AndroidViewModel instead of ViewModel?
 * Because we need the Application context to:
 * 1. Access DataStore through PreferencesRepository
 * 2. Schedule/cancel reminders with WorkManager
 * 3. Check notification permissions
 *
 * We use Application context (not Activity context) to avoid memory leaks.
 */
class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)

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
        val current = userPreferences.value.availableFreezes
        if (current >= PreferencesRepository.MAX_FREEZES) {
            return
        }

        viewModelScope.launch {
            preferencesRepository.purchaseFreeze()
        }
    }

    /**
     * Set the exercise reward amount.
     * This affects how much you earn per workout.
     */
    fun setExerciseReward(amount: Double) {
        viewModelScope.launch {
            preferencesRepository.setExerciseReward(amount)
        }
    }
}

