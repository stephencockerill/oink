package com.oink.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.oink.app.AppContainer
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable form state for creating a habit.
 *
 * All `val`s, so it is stable for Compose skipping. [canSave] is derived from
 * the fields: a habit needs a non-blank name and a positive reward, and cannot
 * be submitted while a save is already in flight.
 */
data class AddHabitUiState(
    val name: String = "",
    val emoji: String = DEFAULT_EMOJI,
    val rewardValue: Long = PreferencesRepository.DEFAULT_DAILY_REWARD,
    val freezesEnabled: Boolean = false,
    val isPrivate: Boolean = false,
    val isSaving: Boolean = false
) {
    /**
     * Whether the current form is valid and idle enough to persist.
     */
    val canSave: Boolean
        get() = name.trim().isNotEmpty() && rewardValue > 0 && !isSaving

    companion object {
        /**
         * Emoji a new habit starts with until the user picks their own from the
         * full picker.
         */
        const val DEFAULT_EMOJI = "⭐"
    }
}

/**
 * ViewModel for the add-habit form.
 *
 * Owns the form as a single immutable [AddHabitUiState] and mutates it through
 * intent methods (state down, events up). [save] computes the next sort order
 * off the current habits, inserts the row, and signals completion once - it is
 * guarded against double submission via [AddHabitUiState.isSaving].
 *
 * A plain [ViewModel] (not [androidx.lifecycle.AndroidViewModel]): it needs no
 * Application context.
 */
class AddHabitViewModel(
    private val habitRepository: HabitRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    /**
     * Whether the form opens with the private toggle already on. Set from the
     * `private` route argument, so opening add-habit from the private area
     * defaults the new habit to private while the home FAB defaults it off.
     */
    private val initialPrivate: Boolean =
        savedStateHandle.get<Boolean>(PRIVATE_ARG) ?: false

    private val _uiState = MutableStateFlow(AddHabitUiState(isPrivate = initialPrivate))
    val uiState: StateFlow<AddHabitUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onEmojiSelect(emoji: String) {
        _uiState.update { it.copy(emoji = emoji) }
    }

    fun onRewardSelect(rewardValue: Long) {
        _uiState.update { it.copy(rewardValue = rewardValue) }
    }

    fun onFreezesToggle(enabled: Boolean) {
        _uiState.update { it.copy(freezesEnabled = enabled) }
    }

    fun onPrivateToggle(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivate = isPrivate) }
    }

    /**
     * Validate, insert the habit, and invoke [onSaved] on success.
     *
     * A no-op when the form is invalid or a save is already running, so a rapid
     * double tap inserts at most one habit. The new row sorts after every
     * existing habit; enabling freezes banks the maximum allowed.
     */
    fun save(onSaved: () -> Unit) {
        if (!_uiState.value.canSave) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val current = _uiState.value
            val nextSortOrder = habitRepository.getAllHabits()
                .maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0
            val habit = Habit(
                name = current.name.trim(),
                emoji = current.emoji,
                rewardValue = current.rewardValue,
                availableFreezes = if (current.freezesEnabled) PreferencesRepository.MAX_FREEZES else 0,
                isPrivate = current.isPrivate,
                sortOrder = nextSortOrder
            )
            habitRepository.addHabit(habit)
            onSaved()
        }
    }

    companion object {
        /**
         * Navigation argument name for the initial private state, kept in sync
         * with the `add_habit?private=...` route.
         */
        const val PRIVATE_ARG = "private"

        /**
         * Factory that builds the ViewModel from the [AppContainer]. The
         * per-entry [SavedStateHandle] - populated with the route's `private`
         * argument - comes from [createSavedStateHandle], so the form opens with
         * the right privacy default.
         */
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AddHabitViewModel(
                        habitRepository = container.habitRepository,
                        savedStateHandle = createSavedStateHandle()
                    )
                }
            }
    }
}
