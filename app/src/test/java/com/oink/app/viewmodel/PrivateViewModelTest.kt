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
import com.oink.app.data.FakePreferencesRepository
import com.oink.app.data.FakeTransactionRunner
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
import com.oink.app.data.PinHasher
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PrivateViewModel] using Robolectric + fakes.
 *
 * Focus:
 * - No PIN configured yields NeedsPinSetup; creating one stores a hash and
 *   unlocks.
 * - A configured PIN starts Locked; a wrong PIN stays Locked with an error;
 *   enough wrong PINs become rate-limited; the correct PIN unlocks and shows
 *   only private habits.
 * - A background re-lock ([PrivateGate.lock]) drops the UI back to Locked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PrivateViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCashOutAllocationDao: FakeCashOutAllocationDao
    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeFrozenDayDao: FakeFrozenDayDao
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var habitRepository: HabitRepository
    private lateinit var freezeRepository: FreezeRepository
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var cashOutRepository: CashOutRepository

    private var elapsed = 0L
    private lateinit var privateGate: PrivateGate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        fakeCheckInDao = FakeCheckInDao()
        fakeCashOutDao = FakeCashOutDao()
        fakeCashOutAllocationDao = FakeCashOutAllocationDao()
        fakeHabitDao = FakeHabitDao()
        fakeFrozenDayDao = FakeFrozenDayDao()
        fakePreferencesRepository = FakePreferencesRepository()
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
        privateGate = PrivateGate(elapsedRealtime = { elapsed })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PrivateViewModel = PrivateViewModel(
        preferencesRepository = fakePreferencesRepository,
        privateGate = privateGate,
        habitRepository = habitRepository,
        checkInRepository = checkInRepository,
        freezeRepository = freezeRepository,
        cashOutRepository = cashOutRepository,
        defaultDispatcher = testDispatcher
    )

    @Test
    fun `no pin yields NeedsPinSetup`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(PrivateUiState.NeedsPinSetup, viewModel.uiState.value)
    }

    @Test
    fun `createPin stores a hash and unlocks`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.createPin("1234")
        advanceUntilIdle()

        val stored = fakePreferencesRepository.getHashedPin()
        assertNotNull("A PIN hash must be persisted", stored)
        assertTrue("The created PIN must verify against the stored hash", PinHasher.verify("1234", stored!!))
        assertTrue(privateGate.isUnlocked.value)
        assertTrue(viewModel.uiState.value is PrivateUiState.Unlocked)
    }

    @Test
    fun `configured pin starts locked`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrivateUiState.Locked)
        assertFalse((state as PrivateUiState.Locked).showError)
    }

    @Test
    fun `wrong pin stays locked with an error`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.submitPin("0000")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrivateUiState.Locked)
        assertTrue((state as PrivateUiState.Locked).showError)
        assertFalse(privateGate.isUnlocked.value)
    }

    @Test
    fun `repeated wrong pins become rate-limited`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        repeat(PrivateGate.MAX_ATTEMPTS) {
            viewModel.submitPin("0000")
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value
        assertTrue(state is PrivateUiState.Locked)
        assertTrue("The gate must be locked out after enough failures", (state as PrivateUiState.Locked).isLockedOut)
        assertTrue(state.remainingLockoutSeconds > 0)
    }

    @Test
    fun `correct pin unlocks and shows only private habits`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.submitPin("4321")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PrivateUiState.Unlocked)
        val ids = (state as PrivateUiState.Unlocked).habits.map { it.id }
        assertEquals("Only the private habit is shown", listOf(2L), ids)
    }

    @Test
    fun `background re-lock drops back to locked`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.submitPin("4321")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PrivateUiState.Unlocked)

        // Simulate the app going to the background.
        privateGate.lock()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PrivateUiState.Locked)
    }
}
