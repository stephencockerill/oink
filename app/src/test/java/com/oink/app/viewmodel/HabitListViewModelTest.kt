package com.oink.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.FakeCashOutAllocationDao
import com.oink.app.data.FakeCashOutDao
import com.oink.app.data.FakeCheckInDao
import com.oink.app.data.FakeFrozenDayDao
import com.oink.app.data.FakeHabitDao
import com.oink.app.data.FakeTransactionRunner
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
import com.oink.app.data.PreferencesRepository
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
 * Unit tests for [HabitListViewModel] using Robolectric + fakes.
 *
 * Focus:
 * - The list contains only public habits, each with its own spendable balance
 *   and streak.
 * - The overall bank is the sum of the public habits' spendables (private
 *   excluded).
 * - An empty public-habit set emits an empty list rather than hanging.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class HabitListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCashOutAllocationDao: FakeCashOutAllocationDao
    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeFrozenDayDao: FakeFrozenDayDao
    private lateinit var habitRepository: HabitRepository
    private lateinit var freezeRepository: FreezeRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var cashOutRepository: CashOutRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeCheckInDao = FakeCheckInDao()
        fakeCashOutDao = FakeCashOutDao()
        fakeCashOutAllocationDao = FakeCashOutAllocationDao()
        fakeHabitDao = FakeHabitDao()
        fakeFrozenDayDao = FakeFrozenDayDao()
        habitRepository = HabitRepository(fakeHabitDao)
        freezeRepository = FreezeRepository(fakeHabitDao, fakeFrozenDayDao)
        checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(fakeCashOutDao, fakeCashOutAllocationDao, freezeRepository)
        )
        cashOutRepository = CashOutRepository(
            fakeCashOutDao,
            fakeCashOutAllocationDao,
            checkInRepository,
            habitRepository,
            freezeRepository,
            FakeTransactionRunner()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HabitListViewModel = HabitListViewModel(
        habitRepository = habitRepository,
        checkInRepository = checkInRepository,
        freezeRepository = freezeRepository,
        cashOutRepository = cashOutRepository
    )

    @Test
    fun `list contains only public habits with correct balances and streaks`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", emoji = "🏋️", sortOrder = 0),
            Habit(id = 2L, name = "Meditate", emoji = "🧘", sortOrder = 1),
            Habit(id = 3L, name = "Secret", emoji = "🤫", isPrivate = true, sortOrder = 2)
        )
        // Habit 1: one completed day today -> spendable = one reward, streak 1.
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 1L)
        // Habit 2: two completed days (yesterday + today) -> spendable = two rewards, streak 2.
        checkInRepository.recordCheckIn(checkInRepository.today().minusDays(1), didSucceed = true, habitId = 2L)
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 2L)
        // Private habit has a balance too, but must be excluded from the list.
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 3L)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.habitCards.collect {} }
        advanceUntilIdle()

        val cards = viewModel.habitCards.value
        assertEquals(listOf(1L, 2L), cards.map { it.id })

        val firstHabit = cards.first { it.id == 1L }
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, firstHabit.spendable)
        assertEquals(1, firstHabit.streak)
        assertEquals("Workout", firstHabit.name)

        val meditate = cards.first { it.id == 2L }
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD * 2, meditate.spendable)
        assertEquals(2, meditate.streak)
    }

    @Test
    fun `overallBank sums public spendables and excludes private habits`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", sortOrder = 0),
            Habit(id = 2L, name = "Meditate", sortOrder = 1),
            Habit(id = 3L, name = "Secret", isPrivate = true, sortOrder = 2)
        )
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 1L)
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 2L)
        // Private habit funds nothing that should appear in the shared bank.
        checkInRepository.recordCheckIn(checkInRepository.today(), didSucceed = true, habitId = 3L)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.overallBank.collect {} }
        advanceUntilIdle()

        // Two public habits at one reward each; the private habit is excluded.
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD * 2, viewModel.overallBank.value)
    }

    @Test
    fun `empty public-habit set emits an empty list`() = runTest {
        // No habits at all.
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.habitCards.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<HabitCardState>(), viewModel.habitCards.value)
    }

    @Test
    fun `only-private habits still emit an empty list`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Secret", isPrivate = true, sortOrder = 0)
        )
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.habitCards.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<HabitCardState>(), viewModel.habitCards.value)
    }
}
