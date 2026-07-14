package com.oink.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oink.app.AppContainer
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import com.oink.app.data.UserPreferences
import com.oink.app.notifications.NotificationHelper
import com.oink.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val freezeRepository: FreezeRepository,
    private val privateGate: PrivateGate,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    /**
     * The habit this screen operates on, taken from the `habit/{habitId}/settings`
     * route argument via [SavedStateHandle]. Reward and freeze reads/writes target
     * this habit, since both are per-habit. Falls back to the seeded default habit
     * if the argument is absent.
     */
    private val habitId: Long =
        savedStateHandle.get<Long>(MainViewModel.HABIT_ID_KEY) ?: HabitRepository.DEFAULT_HABIT_ID

    /**
     * True when this habit is private and the private gate is locked.
     *
     * Mirrors [MainViewModel.privateLocked]: this settings subtree hangs off the
     * private-habit detail screen, so it must also drop back to the PIN gate on a
     * background re-lock rather than keep showing a private habit's settings.
     * Public habits are never gated.
     */
    val privateLocked: StateFlow<Boolean> = combine(
        habitRepository.habit(habitId),
        privateGate.isUnlocked
    ) { habit, unlocked ->
        habit?.isPrivate == true && !unlocked
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

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
    val dailyReward: StateFlow<Long> = habitRepository.habit(habitId)
        .map { it?.rewardValue ?: PreferencesRepository.DEFAULT_DAILY_REWARD }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesRepository.DEFAULT_DAILY_REWARD
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
     * [dailyReward] flow re-emits so the UI reflects the change.
     */
    fun setDailyReward(amount: Long) {
        viewModelScope.launch {
            val habit = habitRepository.getHabit(habitId) ?: return@launch
            habitRepository.updateHabit(habit.copy(rewardValue = amount.coerceAtLeast(1L)))
        }
    }

    companion object {
        /**
         * Factory that builds the ViewModel from [CreationExtras], mirroring
         * [MainViewModel.provideFactory]: the [AppContainer] is closed over
         * explicitly, the [Application] comes from [APPLICATION_KEY], and the
         * per-habit [SavedStateHandle] (carrying the route's `{habitId}`) comes
         * from [createSavedStateHandle].
         */
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[APPLICATION_KEY] as Application
                    SettingsViewModel(
                        application = application,
                        preferencesRepository = container.preferencesRepository,
                        habitRepository = container.habitRepository,
                        freezeRepository = container.freezeRepository,
                        privateGate = container.privateGate,
                        savedStateHandle = createSavedStateHandle()
                    )
                }
            }
    }
}

