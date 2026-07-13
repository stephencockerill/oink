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
import com.oink.app.data.FakeTransactionRunner
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
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
        freezeRepository = freezeRepository,
        privateGate = privateGate
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

    private fun seedHabits(vararg habits: Habit) {
        fakeHabitDao.seed(*habits)
    }

    private fun seedRawBalances(vararg balances: Pair<Long, Long>) {
        val checkIns = balances.mapIndexed { index, (habitId, balance) ->
            CheckIn(
                id = (index + 1).toLong(),
                date = LocalDate.now().minusDays(1),
                didExercise = true,
                balanceAfter = balance,
                habitId = habitId
            )
        }
        fakeCheckInDao.setCheckIns(checkIns)
    }
}
