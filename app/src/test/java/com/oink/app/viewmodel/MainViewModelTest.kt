package com.oink.app.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.FakeCashOutDao
import com.oink.app.data.FakeCheckInDao
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
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var cashOutRepository: CashOutRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = ApplicationProvider.getApplicationContext()
        fakeCheckInDao = FakeCheckInDao()
        fakeCashOutDao = FakeCashOutDao()
        preferencesRepository = PreferencesRepository(application)
        checkInRepository = CheckInRepository(fakeCheckInDao, preferencesRepository)
        cashOutRepository = CashOutRepository(fakeCashOutDao, checkInRepository, preferencesRepository)

        viewModel = MainViewModel(
            application = application,
            repository = checkInRepository,
            preferencesRepository = preferencesRepository,
            cashOutRepository = cashOutRepository
        )
    }

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
        assertEquals(0.0, viewModel.currentBalance.value, 0.001)
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
    fun `initial exerciseReward should be default`() = runTest {
        advanceUntilIdle()
        assertEquals(PreferencesRepository.DEFAULT_EXERCISE_REWARD, viewModel.exerciseReward.value, 0.001)
    }

    @Test
    fun `initial freezeCost should be 2x default reward`() = runTest {
        advanceUntilIdle()
        assertEquals(PreferencesRepository.DEFAULT_EXERCISE_REWARD * 2, viewModel.freezeCost.value, 0.001)
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
    // Factory Tests
    // ============================================================

    @Test
    fun `factory creates MainViewModel successfully`() = runTest {
        val factory = MainViewModel.Factory(
            application = application,
            repository = checkInRepository,
            preferencesRepository = preferencesRepository,
            cashOutRepository = cashOutRepository
        )

        val vm = factory.create(MainViewModel::class.java)
        advanceUntilIdle()

        // Should not throw and should have default state
        assertEquals(0.0, vm.currentBalance.value, 0.001)
    }

}
