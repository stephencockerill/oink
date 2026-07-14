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
import com.oink.app.data.HabitType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [ReminderDecider], the aggregate that drives the single global
 * daily notification.
 *
 * The contract under test: a pending build habit fires the actionable
 * [ReminderDecision.BuildNudge] and takes priority; a quit habit never fires the
 * nudge but fires a [ReminderDecision.QuitCelebration] of its clean streak when
 * nothing needs nudging; private habits never enter the decision. Uses the DAO
 * fakes and a fixed clock so "today" and streaks are deterministic.
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
        decider = ReminderDecider(habitRepository, checkInRepository, freezeRepository)
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

    /** A clean check-in for a quit habit on [date], id derived to stay unique. */
    private fun cleanDay(habitId: Long, date: LocalDate) = CheckIn(
        id = habitId * 1000 + date.toEpochDay(),
        date = date,
        didSucceed = true,
        balanceAfter = 500L,
        habitId = habitId
    )

    // =====================================================================
    // Build nudge
    // =====================================================================

    @Test
    fun `nudges when a build habit has no check-in today`() = runTest {
        fakeHabitDao.seed(Habit(id = 1L, name = "Workout"))

        assertEquals(ReminderDecision.BuildNudge, decider.decide())
    }

    @Test
    fun `nudges when a build habit is logged incomplete today`() = runTest {
        fakeHabitDao.seed(Habit(id = 1L, name = "Workout"))
        fakeCheckInDao.seed(missedToday(1L))

        assertEquals(ReminderDecision.BuildNudge, decider.decide())
    }

    @Test
    fun `silent when all build habits are completed today`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout"),
            Habit(id = 2L, name = "Read")
        )
        fakeCheckInDao.seed(completedToday(1L), completedToday(2L))

        assertEquals(ReminderDecision.None, decider.decide())
    }

    @Test
    fun `nudges when one of several build habits is still incomplete`() = runTest {
        // The core multi-habit case: habit 1 done, habit 2 not - a hardcoded
        // habit-1 read would wrongly suppress the nudge.
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout"),
            Habit(id = 2L, name = "Read")
        )
        fakeCheckInDao.seed(completedToday(1L))

        assertEquals(ReminderDecision.BuildNudge, decider.decide())
    }

    @Test
    fun `ignores a private build habit that is incomplete today`() = runTest {
        // Only habit is private and pending: the public nudge must stay silent.
        fakeHabitDao.seed(Habit(id = 1L, name = "Journal", isPrivate = true))

        assertEquals(ReminderDecision.None, decider.decide())
    }

    @Test
    fun `silent when build habit is done and only a private habit is pending`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", isPrivate = false),
            Habit(id = 2L, name = "Journal", isPrivate = true)
        )
        fakeCheckInDao.seed(completedToday(1L))

        assertEquals(ReminderDecision.None, decider.decide())
    }

    @Test
    fun `silent when there are no non-private habits`() = runTest {
        assertEquals(ReminderDecision.None, decider.decide())
    }

    // =====================================================================
    // Quit celebration
    // =====================================================================

    @Test
    fun `a pending quit habit never fires the build nudge`() = runTest {
        // A quit habit with no check-in today is the expected clean-so-far state,
        // not outstanding work. With no streak yet, nothing fires.
        fakeHabitDao.seed(Habit(id = 1L, name = "No doomscrolling", habitType = HabitType.QUIT))

        assertEquals(ReminderDecision.None, decider.decide())
    }

    @Test
    fun `celebrates a quit habit's clean streak when nothing needs nudging`() = runTest {
        fakeHabitDao.seed(Habit(id = 1L, name = "No doomscrolling", habitType = HabitType.QUIT))
        // Clean yesterday and the day before; today is intentionally unresolved.
        fakeCheckInDao.seed(
            cleanDay(1L, today.minusDays(1)),
            cleanDay(1L, today.minusDays(2))
        )

        assertEquals(ReminderDecision.QuitCelebration(2), decider.decide())
    }

    @Test
    fun `build nudge takes priority over a quit celebration`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "Workout", habitType = HabitType.BUILD),
            Habit(id = 2L, name = "No doomscrolling", habitType = HabitType.QUIT)
        )
        // Build habit pending; quit habit on a streak. The actionable nudge wins.
        fakeCheckInDao.seed(
            cleanDay(2L, today.minusDays(1)),
            cleanDay(2L, today.minusDays(2))
        )

        assertEquals(ReminderDecision.BuildNudge, decider.decide())
    }

    @Test
    fun `celebrates the best clean streak across quit habits`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "No doomscrolling", habitType = HabitType.QUIT),
            Habit(id = 2L, name = "No smoking", habitType = HabitType.QUIT)
        )
        // habit 1: 1 clean day; habit 2: 3 clean days. Celebrate the best.
        fakeCheckInDao.seed(
            cleanDay(1L, today.minusDays(1)),
            cleanDay(2L, today.minusDays(1)),
            cleanDay(2L, today.minusDays(2)),
            cleanDay(2L, today.minusDays(3))
        )

        assertEquals(ReminderDecision.QuitCelebration(3), decider.decide())
    }

    @Test
    fun `ignores a private quit habit's streak`() = runTest {
        fakeHabitDao.seed(
            Habit(id = 1L, name = "No doomscrolling", habitType = HabitType.QUIT, isPrivate = true)
        )
        fakeCheckInDao.seed(
            cleanDay(1L, today.minusDays(1)),
            cleanDay(1L, today.minusDays(2))
        )

        assertEquals(ReminderDecision.None, decider.decide())
    }
}
