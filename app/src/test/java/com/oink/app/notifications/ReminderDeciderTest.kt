package com.oink.app.notifications

import com.oink.app.data.CheckIn
import com.oink.app.data.CheckInRepository
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.FakeCashOutAllocationDao
import com.oink.app.data.FakeCashOutDao
import com.oink.app.data.FakeCheckInDao
import com.oink.app.data.FakeFrozenDayDao
import com.oink.app.data.FakeHabitDao
import com.oink.app.data.FreezeRepository
import com.oink.app.data.Habit
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [ReminderDecider], the aggregate that drives the single global
 * daily reminder.
 *
 * The point of these tests is the multi-habit contract: the nudge fires when ANY
 * non-private habit still has outstanding work today, and private habits never
 * enter the decision. Uses the DAO fakes and a fixed clock so "today" is
 * deterministic.
 */
class ReminderDeciderTest {

    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var decider: ReminderDecider

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2025-06-15T12:00:00Z"),
        ZoneId.of("UTC")
    )
    private val today: LocalDate = LocalDate.now(fixedClock)

    @Before
    fun setup() {
        fakeHabitDao = FakeHabitDao()
        fakeCheckInDao = FakeCheckInDao()

        val habitRepository = HabitRepository(fakeHabitDao)
        val freezeRepository = FreezeRepository(fakeHabitDao, FakeFrozenDayDao())
        val checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(
                FakeCashOutDao(),
                FakeCashOutAllocationDao(),
                freezeRepository
            ),
            fixedClock
        )
        decider = ReminderDecider(habitRepository, checkInRepository)
    }

    private fun completedToday(habitId: Long) = CheckIn(
        id = habitId,
        date = today,
        didSucceed = true,
        balanceAfter = 500L,
        habitId = habitId
    )

    private fun missedToday(habitId: Long) = CheckIn(
        id = habitId,
        date = today,
        didSucceed = false,
        balanceAfter = 0L,
        habitId = habitId
    )

    @Test
    fun `fires when a public habit has no check-in today`() = runTest {
        fakeHabitDao.seed(Habit(id = 1L, name = "Workout"))

        assertTrue(decider.shouldRemind())
    }

    @Test
    fun `fires when a public habit is logged incomplete today`() = runTest {
        fakeHabitDao.seed(Habit(id = 1L, name = "Workout"))
        fakeCheckInDao.seed(missedToday(1L))

        assertTrue(decider.shouldRemind())
    }

    @Test
    fun `suppresses when all public habits are completed today`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout"),
            Habit(id = 2L, name = "Read")
        )
        fakeCheckInDao.seed(completedToday(1L), completedToday(2L))

        assertFalse(decider.shouldRemind())
    }

    @Test
    fun `fires when one of several public habits is still incomplete`() = runTest {
        // The core multi-habit case: habit 1 done, habit 2 not - a hardcoded
        // habit-1 read would wrongly suppress the nudge.
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout"),
            Habit(id = 2L, name = "Read")
        )
        fakeCheckInDao.seed(completedToday(1L))

        assertTrue(decider.shouldRemind())
    }

    @Test
    fun `ignores a private habit that is incomplete today`() = runTest {
        // Only habit is private and pending: the public nudge must stay silent.
        fakeHabitDao.seed(Habit(id = 1L, name = "Journal", isPrivate = true))

        assertFalse(decider.shouldRemind())
    }

    @Test
    fun `suppresses when public habit is done and only a private habit is pending`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", isPrivate = false),
            Habit(id = 2L, name = "Journal", isPrivate = true)
        )
        fakeCheckInDao.seed(completedToday(1L))

        assertFalse(decider.shouldRemind())
    }

    @Test
    fun `suppresses when there are no non-private habits`() = runTest {
        // No habits at all - nothing to nudge about.
        assertFalse(decider.shouldRemind())
    }
}
