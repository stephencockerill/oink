package com.oink.app.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.oink.app.data.FakeFrozenDayDao
import com.oink.app.data.FakeHabitDao
import com.oink.app.data.FakePreferencesRepository
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SettingsViewModel] using Robolectric + fakes.
 *
 * Focus on the per-habit icon editing added to the config screen: [setEmoji]
 * writes through to [Habit.emoji] on the habit this screen is scoped to, and
 * multi-codepoint emoji survive the round trip verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val habitId = 7L

    private lateinit var application: Application
    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeFrozenDayDao: FakeFrozenDayDao
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var freezeRepository: FreezeRepository
    private lateinit var privateGate: PrivateGate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = ApplicationProvider.getApplicationContext()
        fakeHabitDao = FakeHabitDao().apply {
            seed(Habit(id = habitId, name = "Run", emoji = "⭐"))
        }
        fakeFrozenDayDao = FakeFrozenDayDao()
        preferencesRepository = FakePreferencesRepository()
        habitRepository = HabitRepository(fakeHabitDao)
        freezeRepository = FreezeRepository(fakeHabitDao, fakeFrozenDayDao)
        privateGate = PrivateGate(elapsedRealtime = { 0L })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        application = application,
        preferencesRepository = preferencesRepository,
        habitRepository = habitRepository,
        freezeRepository = freezeRepository,
        privateGate = privateGate,
        savedStateHandle = SavedStateHandle(mapOf(MainViewModel.HABIT_ID_KEY to habitId))
    )

    @Test
    fun `setEmoji persists the chosen emoji to the habit`() = runTest {
        val viewModel = createViewModel()

        viewModel.setEmoji("🏃")
        advanceUntilIdle()

        val updated = habitRepository.getHabit(habitId)
        assertEquals("🏃", updated?.emoji)
    }

    @Test
    fun `setEmoji stores a multi-codepoint emoji verbatim`() = runTest {
        val viewModel = createViewModel()

        // Skin-tone modifier sequence: multiple codepoints in one grapheme.
        val multiCodepoint = "🧘🏽‍♀️"
        viewModel.setEmoji(multiCodepoint)
        advanceUntilIdle()

        val updated = habitRepository.getHabit(habitId)
        assertEquals(multiCodepoint, updated?.emoji)
    }

    @Test
    fun `emoji flow reflects the stored habit and re-emits after an edit`() = runTest {
        val viewModel = createViewModel()

        // A collector is required for the WhileSubscribed flow to start.
        val job = backgroundScope.launch { viewModel.emoji.collect { } }
        advanceUntilIdle()
        assertEquals("⭐", viewModel.emoji.value)

        viewModel.setEmoji("📚")
        advanceUntilIdle()
        assertEquals("📚", viewModel.emoji.value)

        job.cancel()
    }

    @Test
    fun `setEmoji is a no-op when the habit does not exist`() = runTest {
        val viewModel = SettingsViewModel(
            application = application,
            preferencesRepository = preferencesRepository,
            habitRepository = habitRepository,
            freezeRepository = freezeRepository,
            privateGate = privateGate,
            savedStateHandle = SavedStateHandle(mapOf(MainViewModel.HABIT_ID_KEY to 999L))
        )

        viewModel.setEmoji("🏃")
        advanceUntilIdle()

        // The real habit is untouched; no phantom habit is created.
        assertEquals("⭐", habitRepository.getHabit(habitId)?.emoji)
        assertEquals(1, habitRepository.getAllHabits().size)
    }
}
