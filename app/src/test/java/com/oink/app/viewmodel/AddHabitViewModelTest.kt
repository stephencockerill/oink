package com.oink.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.oink.app.data.FakeHabitDao
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AddHabitViewModel] using Robolectric + fakes.
 *
 * Focus:
 * - A valid form saves a habit carrying exactly the chosen fields, sorted after
 *   every existing habit.
 * - Validation gates the save: a blank name or non-positive reward is not
 *   savable, and save is a no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AddHabitViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var habitRepository: HabitRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeHabitDao = FakeHabitDao()
        habitRepository = HabitRepository(fakeHabitDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AddHabitViewModel =
        AddHabitViewModel(habitRepository = habitRepository)

    @Test
    fun `save inserts a habit with the chosen fields and next sortOrder`() = runTest {
        // An existing habit at sortOrder 3; the new one must sort after it.
        fakeHabitDao.seed(Habit(id = 1L, name = "Workout", sortOrder = 3))

        val viewModel = createViewModel()
        viewModel.onNameChange("  Meditate  ")
        viewModel.onEmojiSelect("🧘")
        viewModel.onRewardSelect(1000L)
        viewModel.onFreezesToggle(true)
        viewModel.onPrivateToggle(true)

        var saved = false
        viewModel.save { saved = true }
        advanceUntilIdle()

        assertTrue("onSaved should fire once the insert completes", saved)

        val created = habitRepository.getAllHabits().first { it.name == "Meditate" }
        assertEquals("🧘", created.emoji)
        assertEquals(1000L, created.rewardValue)
        // Freezes on -> banks the maximum allowed.
        assertEquals(PreferencesRepository.MAX_FREEZES, created.availableFreezes)
        assertTrue(created.isPrivate)
        // Sorts immediately after the seeded sortOrder 3.
        assertEquals(4, created.sortOrder)
        // Name is trimmed before persisting.
        assertEquals("Meditate", created.name)
    }

    @Test
    fun `first habit in an empty table gets sortOrder zero and no freezes when off`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNameChange("Run")

        viewModel.save {}
        advanceUntilIdle()

        val created = habitRepository.getAllHabits().single()
        assertEquals(0, created.sortOrder)
        assertEquals(0, created.availableFreezes)
        assertFalse(created.isPrivate)
        // Default reward is applied and remains positive.
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, created.rewardValue)
    }

    @Test
    fun `blank name is not savable and save is a no-op`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNameChange("   ")

        assertFalse(viewModel.uiState.value.canSave)

        var saved = false
        viewModel.save { saved = true }
        advanceUntilIdle()

        assertFalse("onSaved must not fire for an invalid form", saved)
        assertNull(habitRepository.getAllHabits().firstOrNull())
    }

    @Test
    fun `canSave flips to true once a non-blank name is present`() = runTest {
        val viewModel = createViewModel()
        // Default reward is positive, so only the name gates validity.
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onNameChange("Read")
        assertTrue(viewModel.uiState.value.canSave)
    }
}
