package com.oink.app.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckIn
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.CheckInRepository
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
import com.oink.app.data.HabitType
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import com.oink.app.utils.MascotState
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
 * Unit tests for MainViewModel using Robolectric.
 *
 * IMPORTANT: Testing ViewModels with complex async Flows is tricky.
 * The core business logic (balance calculation, streak calculation) is
 * thoroughly tested in CheckInRepositoryTest and BalanceCalculatorTest.
 *
 * These tests focus on:
 * - Initial state values (synchronous)
 * - Error handling (synchronous state changes)
 * - Simple state management
 *
 * Tests that require waiting for async Flow emissions are kept minimal
 * because they're fragile and the underlying logic is already tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCashOutAllocationDao: FakeCashOutAllocationDao
    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeFrozenDayDao: FakeFrozenDayDao
    private lateinit var habitRepository: HabitRepository
    private lateinit var freezeRepository: FreezeRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var cashOutRepository: CashOutRepository
    private lateinit var privateGate: PrivateGate
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = ApplicationProvider.getApplicationContext()
        fakeCheckInDao = FakeCheckInDao()
        fakeCashOutDao = FakeCashOutDao()
        fakeCashOutAllocationDao = FakeCashOutAllocationDao()
        fakeHabitDao = FakeHabitDao().apply {
            seed(
                Habit(id = HABIT_A, name = "Workout"),
                Habit(id = HABIT_B, name = "Meditate"),
                Habit(id = HABIT_PRIVATE, name = "Therapy", isPrivate = true)
            )
        }
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
        // Fixed clock value keeps the gate's lockout math deterministic; these
        // tests only exercise the unlock flag.
        privateGate = PrivateGate(elapsedRealtime = { 0L })

        viewModel = viewModelFor(HABIT_A)
    }

    /**
     * Build a MainViewModel scoped to [habitId], mirroring how the nav host
     * populates the SavedStateHandle from the `habit/{habitId}` route argument.
     */
    private fun viewModelFor(habitId: Long): MainViewModel = MainViewModel(
        application = application,
        repository = checkInRepository,
        habitRepository = habitRepository,
        cashOutRepository = cashOutRepository,
        freezeRepository = freezeRepository,
        privateGate = privateGate,
        savedStateHandle = SavedStateHandle(mapOf(MainViewModel.HABIT_ID_KEY to habitId))
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================
    // Initial State Tests
    // These test that the ViewModel starts in the correct state.
    // ============================================================

    @Test
    fun `initial balance should be zero`() = runTest {
        advanceUntilIdle()
        assertEquals(0L, viewModel.currentBalance.value)
    }

    @Test
    fun `initial streak should be zero`() = runTest {
        advanceUntilIdle()
        assertEquals(0, viewModel.streak.value)
    }

    @Test
    fun `initial todayCheckIn should be null`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.todayCheckIn.value)
    }

    @Test
    fun `initial isLoading should be false after setup`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `initial error should be null`() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initial dailyReward should be default`() = runTest {
        advanceUntilIdle()
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, viewModel.dailyReward.value)
    }

    @Test
    fun `initial freezeCost should be 2x default reward`() = runTest {
        advanceUntilIdle()
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD * 2, viewModel.freezeCost.value)
    }

    @Test
    fun `initial availableFreezes should be zero`() = runTest {
        advanceUntilIdle()
        assertEquals(0, viewModel.availableFreezes.value)
    }

    // ============================================================
    // Error Handling Tests
    // These test synchronous error state management.
    // ============================================================

    @Test
    fun `clearError should clear error state`() = runTest {
        advanceUntilIdle()

        // Trigger an error (no freezes, no balance)
        viewModel.useFreeze(java.time.LocalDate.now().minusDays(1))
        advanceUntilIdle()

        // Clear it
        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `dismissFreezePrompt should clear missedDayForFreeze`() = runTest {
        advanceUntilIdle()
        viewModel.dismissFreezePrompt()
        assertNull(viewModel.missedDayForFreeze.value)
    }

    @Test
    fun `quit habit does not surface the freeze prompt for an unlogged gap`() = runTest {
        // Regression (E2E bug): a quit habit with clean days then an unlogged gap
        // yesterday - a pending-clean day the day-close resolver banks at midnight,
        // NOT a slip - must not show the freeze-forgiveness prompt.
        fakeHabitDao.seed(Habit(id = HABIT_QUIT, name = "No scrolling", habitType = HabitType.QUIT))
        val today = java.time.LocalDate.now()
        fakeCheckInDao.seed(
            CheckIn(id = 1L, date = today.minusDays(3), didSucceed = true, balanceAfter = 500L, habitId = HABIT_QUIT),
            CheckIn(id = 2L, date = today.minusDays(2), didSucceed = true, balanceAfter = 1000L, habitId = HABIT_QUIT)
            // yesterday: gap (pending clean)
        )
        val vm = viewModelFor(HABIT_QUIT)
        backgroundScope.launch { vm.missedDayForFreeze.collect {} }
        advanceUntilIdle()

        assertNull(vm.missedDayForFreeze.value)
    }

    @Test
    fun `quit habit surfaces the freeze prompt for a logged slip`() = runTest {
        // The forgiveness path still works: a genuine logged slip yesterday
        // surfaces so the user can protect their clean streak.
        fakeHabitDao.seed(Habit(id = HABIT_QUIT, name = "No scrolling", habitType = HabitType.QUIT))
        val today = java.time.LocalDate.now()
        fakeCheckInDao.seed(
            CheckIn(id = 1L, date = today.minusDays(2), didSucceed = true, balanceAfter = 500L, habitId = HABIT_QUIT),
            CheckIn(id = 2L, date = today.minusDays(1), didSucceed = false, balanceAfter = 250L, habitId = HABIT_QUIT)
        )
        val vm = viewModelFor(HABIT_QUIT)
        backgroundScope.launch { vm.missedDayForFreeze.collect {} }
        advanceUntilIdle()

        assertEquals(today.minusDays(1), vm.missedDayForFreeze.value)
    }

    @Test
    fun `useFreeze without sufficient balance should set error`() = runTest {
        advanceUntilIdle()

        // Purchase a freeze (free to acquire)
        viewModel.purchaseFreeze()
        advanceUntilIdle()

        // Try to use it without balance
        viewModel.useFreeze(java.time.LocalDate.now().minusDays(1))
        advanceUntilIdle()

        // Should have an error about balance
        val error = viewModel.error.value
        assert(error != null && error.contains("balance", ignoreCase = true)) {
            "Expected balance error, got: $error"
        }
    }

    // ============================================================
    // Reactivity Tests
    // These prove the derived StateFlows update off their source flows
    // with no imperative refresh call - the whole point of issue #9.
    // WhileSubscribed flows only run while collected, so each test keeps
    // an active collector alive in backgroundScope.
    // ============================================================

    @Test
    fun `streak updates reactively after check-in without any refresh`() = runTest {
        backgroundScope.launch { viewModel.streak.collect {} }
        advanceUntilIdle()
        assertEquals(0, viewModel.streak.value)

        viewModel.recordTodayCheckIn(didSucceed = true)
        advanceUntilIdle()

        assertEquals(1, viewModel.streak.value)
    }

    @Test
    fun `availableFreezes updates reactively after purchase without any refresh`() = runTest {
        backgroundScope.launch { viewModel.availableFreezes.collect {} }
        advanceUntilIdle()
        assertEquals(0, viewModel.availableFreezes.value)

        viewModel.purchaseFreeze()
        advanceUntilIdle()

        assertEquals(1, viewModel.availableFreezes.value)
    }

    @Test
    fun `completedPreview updates reactively after a check-in raises balance`() = runTest {
        backgroundScope.launch { viewModel.completedPreview.collect {} }
        advanceUntilIdle()
        // From a zero balance, completing today would reach one reward.
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, viewModel.completedPreview.value)

        viewModel.recordTodayCheckIn(didSucceed = true)
        advanceUntilIdle()

        // Balance is now one reward, so completing again previews two rewards.
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD * 2, viewModel.completedPreview.value)
    }

    @Test
    fun `heroState reflects this habit's balance, gain, streak, and milestone`() = runTest {
        backgroundScope.launch { viewModel.heroState.collect {} }
        advanceUntilIdle()

        viewModel.recordTodayCheckIn(didSucceed = true)
        advanceUntilIdle()

        val hero = viewModel.heroState.value
        val reward = PreferencesRepository.DEFAULT_DAILY_REWARD
        assertEquals(reward, hero.balanceCents)
        // Today's reward banked -> gain chip shows it.
        assertEquals(reward, hero.dailyGainCents)
        assertEquals(1, hero.streak)
        // First tier still ahead.
        assertEquals(2_500L, hero.milestone.nextThresholdCents)
    }

    @Test
    fun `heroState flips the mascot to comeback on a fresh miss`() = runTest {
        backgroundScope.launch { viewModel.heroState.collect {} }
        advanceUntilIdle()

        // Build a balance, then miss today: the latest action is a halving.
        viewModel.recordCheckIn(java.time.LocalDate.now().minusDays(1), didSucceed = true)
        advanceUntilIdle()
        viewModel.recordTodayCheckIn(didSucceed = false)
        advanceUntilIdle()

        val hero = viewModel.heroState.value
        assertEquals(0L, hero.dailyGainCents)
        assertEquals(0, hero.streak)
        assertEquals(MascotState.COMEBACK, hero.mascotState)
    }

    // ============================================================
    // Per-habit Scoping Tests
    // The detail screen is per-habit: the habit id comes from the
    // SavedStateHandle (the route arg), and one habit's actions must never
    // touch another's balance or streak.
    // ============================================================

    @Test
    fun `habitId comes from SavedStateHandle and scopes balance to that habit`() = runTest {
        val vmA = viewModelFor(HABIT_A)
        backgroundScope.launch { vmA.currentBalance.collect {} }
        advanceUntilIdle()

        vmA.recordTodayCheckIn(didSucceed = true)
        advanceUntilIdle()

        // Habit A earned one reward; its spendable balance reflects only itself.
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, vmA.currentBalance.value)
    }

    @Test
    fun `check-in on habit A does not change habit B balance or streak`() = runTest {
        val vmA = viewModelFor(HABIT_A)
        val vmB = viewModelFor(HABIT_B)
        backgroundScope.launch { vmA.currentBalance.collect {} }
        backgroundScope.launch { vmA.streak.collect {} }
        backgroundScope.launch { vmB.currentBalance.collect {} }
        backgroundScope.launch { vmB.streak.collect {} }
        advanceUntilIdle()

        vmA.recordTodayCheckIn(didSucceed = true)
        advanceUntilIdle()

        // Habit A moved...
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, vmA.currentBalance.value)
        assertEquals(1, vmA.streak.value)
        // ...habit B did not.
        assertEquals(0L, vmB.currentBalance.value)
        assertEquals(0, vmB.streak.value)
    }

    // ============================================================
    // Factory Tests
    // ============================================================

    @Test
    fun `viewModel builds from a SavedStateHandle with default state`() = runTest {
        val vm = viewModelFor(HABIT_A)
        advanceUntilIdle()

        // Should not throw and should have default state
        assertEquals(0L, vm.currentBalance.value)
    }

    // ============================================================
    // Private-gate Tests
    // A per-habit screen for a PRIVATE habit must not stay visible once the
    // shared gate re-locks (e.g. after backgrounding). privateLocked drives the
    // screen's pop-back-to-the-PIN-gate. Public habits are never gated.
    // ============================================================

    @Test
    fun `privateLocked is false for a public habit regardless of the gate`() = runTest {
        val vm = viewModelFor(HABIT_A)
        backgroundScope.launch { vm.privateLocked.collect {} }
        advanceUntilIdle()

        // Locked gate: a public habit is unaffected.
        assertFalse(vm.privateLocked.value)

        // Unlocking changes nothing for a public habit.
        privateGate.unlock()
        advanceUntilIdle()
        assertFalse(vm.privateLocked.value)
    }

    @Test
    fun `privateLocked is true for a private habit while the gate is locked and clears when unlocked`() = runTest {
        val vm = viewModelFor(HABIT_PRIVATE)
        backgroundScope.launch { vm.privateLocked.collect {} }
        advanceUntilIdle()

        // Gate starts locked: the private habit's screen must leave.
        assertTrue(vm.privateLocked.value)

        // Unlock (correct PIN entered elsewhere): the screen may render again.
        privateGate.unlock()
        advanceUntilIdle()
        assertFalse(vm.privateLocked.value)

        // Backgrounding re-locks the gate: the screen must leave once more.
        privateGate.lock()
        advanceUntilIdle()
        assertTrue(vm.privateLocked.value)
    }

    private companion object {
        const val HABIT_A = HabitRepository.DEFAULT_HABIT_ID
        const val HABIT_B = 2L
        const val HABIT_PRIVATE = 3L
        const val HABIT_QUIT = 4L
    }
}
