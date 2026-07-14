package com.oink.app.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckIn
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Unit tests for [RewardsViewModel] using Robolectric + fakes.
 *
 * Focus is the private-area unlock wiring (MH-9): the spendable pot and reward
 * history must both track [PrivateGate.isUnlocked]. Locked shows the public pot
 * and hides any cash-out that drew from a private habit; unlocked shows the
 * public + private pot and the full history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RewardsViewModelTest {

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
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var privateGate: PrivateGate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = ApplicationProvider.getApplicationContext()
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
        privateGate = PrivateGate(elapsedRealtime = { 0L })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RewardsViewModel = RewardsViewModel(
        application = application,
        cashOutRepository = cashOutRepository,
        checkInRepository = checkInRepository,
        habitRepository = habitRepository,
        freezeRepository = freezeRepository,
        privateGate = privateGate,
        preferencesRepository = fakePreferencesRepository,
        defaultDispatcher = testDispatcher
    )

    @Test
    fun `currentBalance is the public pot when locked and grows to public plus private when unlocked`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        seedRawBalances(1L to 2000L, 2L to 5000L)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.currentBalance.collect {} }
        advanceUntilIdle()

        // Locked: public habit only.
        assertEquals(2000L, viewModel.currentBalance.value)

        // Unlocked: public + private, live.
        privateGate.unlock()
        advanceUntilIdle()
        assertEquals(7000L, viewModel.currentBalance.value)
    }

    @Test
    fun `history hides a private-funded claim when locked and shows every claim when unlocked`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        seedRawBalances(1L to 3000L, 2L to 3000L)

        val publicClaim = cashOutRepository.cashOut("Public", 2000, includePrivate = false)!!
        val mixedClaim = cashOutRepository.cashOut("Mixed", 4000, includePrivate = true)!!

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.allCashOuts.collect {} }
        advanceUntilIdle()

        // Locked: the mixed claim (touched the private habit) is hidden.
        assertEquals(listOf(publicClaim.id), viewModel.allCashOuts.value.map { it.id })

        // Unlocked: the full history shows.
        privateGate.unlock()
        advanceUntilIdle()
        assertEquals(
            setOf(publicClaim.id, mixedClaim.id),
            viewModel.allCashOuts.value.map { it.id }.toSet()
        )
    }

    @Test
    fun `unlocked amount edit of a private-funded claim re-splits over public and private`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        seedRawBalances(1L to 2000L, 2L to 5000L)

        // Unlocked claim of 4000 drains the higher private habit fully.
        val claim = cashOutRepository.cashOut("Treat", 4000, includePrivate = true)!!

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.currentBalance.collect {} }
        privateGate.unlock()
        advanceUntilIdle()

        viewModel.selectCashOut(claim)
        // Editing up to 6000 while unlocked: re-split pot is 7000, so this is
        // accepted and split across the private (5000) and public (1000) banks.
        viewModel.updateSelectedCashOut(name = "Treat", amount = 6000, emoji = "🎁")
        advanceUntilIdle()

        assertNull(viewModel.error.value)
        assertEquals(5000L, fakeCashOutAllocationDao.getTotalForHabit(2L))
        assertEquals(1000L, fakeCashOutAllocationDao.getTotalForHabit(1L))
    }

    @Test
    fun `privateFundsAccess is NoPin with no pin, Locked once a pin is set, Unlocked after unlock`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.privateFundsAccess.collect {} }
        advanceUntilIdle()

        // No PIN configured: no unlock control.
        assertEquals(PrivateFundsAccess.NoPin, viewModel.privateFundsAccess.value)

        // A PIN exists and the area is locked: offer the discreet unlock.
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))
        advanceUntilIdle()
        assertEquals(PrivateFundsAccess.Locked, viewModel.privateFundsAccess.value)

        // Unlocked: private funds are folded in.
        privateGate.unlock()
        advanceUntilIdle()
        assertEquals(PrivateFundsAccess.Unlocked, viewModel.privateFundsAccess.value)
    }

    @Test
    fun `correct pin unlocks so the pot grows to include private funds`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        seedRawBalances(1L to 2000L, 2L to 5000L)
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.currentBalance.collect {} }
        backgroundScope.launch { viewModel.privateFundsAccess.collect {} }
        advanceUntilIdle()

        // Locked: public pot only.
        assertEquals(2000L, viewModel.currentBalance.value)

        viewModel.submitPin("4321")
        advanceUntilIdle()

        // Correct PIN unlocks: pot now spans public + private.
        assertTrue(privateGate.isUnlocked.value)
        assertEquals(PrivateFundsAccess.Unlocked, viewModel.privateFundsAccess.value)
        assertEquals(7000L, viewModel.currentBalance.value)
    }

    @Test
    fun `wrong pin records a failure and stays locked with an error`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.privateFundsAccess.collect {} }
        backgroundScope.launch { viewModel.pinPrompt.collect {} }
        advanceUntilIdle()

        viewModel.submitPin("0000")
        advanceUntilIdle()

        assertFalse(privateGate.isUnlocked.value)
        assertEquals(PrivateFundsAccess.Locked, viewModel.privateFundsAccess.value)
        val prompt = viewModel.pinPrompt.value
        assertTrue(prompt.showError)
        assertEquals(PrivateGate.MAX_ATTEMPTS - 1, prompt.remainingAttempts)
    }

    @Test
    fun `repeated wrong pins become rate-limited`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.pinPrompt.collect {} }
        advanceUntilIdle()

        repeat(PrivateGate.MAX_ATTEMPTS) {
            viewModel.submitPin("0000")
            advanceUntilIdle()
        }

        val prompt = viewModel.pinPrompt.value
        assertTrue("The gate must be locked out after enough failures", prompt.isLockedOut)
        assertTrue(prompt.remainingLockoutSeconds > 0)
        assertFalse(privateGate.isUnlocked.value)
    }

    @Test
    fun `clearPinError clears the failed-attempt flag`() = runTest {
        fakePreferencesRepository.setPin(PinHasher.hash("4321"))

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.pinPrompt.collect {} }
        advanceUntilIdle()

        viewModel.submitPin("0000")
        advanceUntilIdle()
        assertTrue(viewModel.pinPrompt.value.showError)

        viewModel.clearPinError()
        advanceUntilIdle()
        assertFalse(viewModel.pinPrompt.value.showError)
    }

    @Test
    fun `totalCompletedDays sums completed days across every public habit`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Read", sortOrder = 1, isPrivate = false)
        )
        seedCompletedDays(1L to 3, 2L to 2)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.totalCompletedDays.collect {} }
        advanceUntilIdle()

        assertEquals(5, viewModel.totalCompletedDays.value)
    }

    @Test
    fun `totalCompletedDays is zero with no habits and never touches the default habit`() = runTest {
        // No habits in scope, but the default habit has completed days on record.
        // The stat must span the (empty) habit set, not the missing default habit.
        seedCompletedDays(HabitRepository.DEFAULT_HABIT_ID to 4)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.totalCompletedDays.collect {} }
        advanceUntilIdle()

        assertEquals(0, viewModel.totalCompletedDays.value)
    }

    @Test
    fun `totalCompletedDays excludes private habits when locked and includes them when unlocked`() = runTest {
        seedHabits(
            Habit(id = 1L, name = "Workout", sortOrder = 0, isPrivate = false),
            Habit(id = 2L, name = "Therapy", sortOrder = 1, isPrivate = true)
        )
        seedCompletedDays(1L to 3, 2L to 2)

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.totalCompletedDays.collect {} }
        advanceUntilIdle()

        // Locked: public habit only.
        assertEquals(3, viewModel.totalCompletedDays.value)

        // Unlocked: public + private, live.
        privateGate.unlock()
        advanceUntilIdle()
        assertEquals(5, viewModel.totalCompletedDays.value)
    }

    private fun seedHabits(vararg habits: Habit) {
        fakeHabitDao.seed(*habits)
    }

    /**
     * Seed completed ([CheckIn.didSucceed] true) check-ins per habit on distinct
     * dates, so [RewardsViewModel.totalCompletedDays] has counts greater than one
     * to sum. Dates are unique across all seeded check-ins.
     */
    private fun seedCompletedDays(vararg habitToCount: Pair<Long, Int>) {
        var id = 1L
        var dayOffset = 1L
        val checkIns = habitToCount.flatMap { (habitId, count) ->
            (0 until count).map {
                CheckIn(
                    id = id++,
                    date = LocalDate.now().minusDays(dayOffset++),
                    didSucceed = true,
                    balanceAfter = 0L,
                    habitId = habitId
                )
            }
        }
        fakeCheckInDao.setCheckIns(checkIns)
    }

    private fun seedRawBalances(vararg balances: Pair<Long, Long>) {
        val checkIns = balances.mapIndexed { index, (habitId, balance) ->
            CheckIn(
                id = (index + 1).toLong(),
                date = LocalDate.now().minusDays(1),
                didSucceed = true,
                balanceAfter = balance,
                habitId = habitId
            )
        }
        fakeCheckInDao.setCheckIns(checkIns)
    }
}
