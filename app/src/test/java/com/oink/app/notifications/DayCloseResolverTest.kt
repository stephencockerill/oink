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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [DayCloseResolver], the day-close job that materializes quit
 * habits' passive clean days.
 *
 * A fixed clock pins "today" to 2025-06-15 (so yesterday is 2025-06-14), and the
 * DAO fakes stand in for Room. The tests exercise the contract from the design:
 * multi-day catch-up, a logged slip is left untouched, build habits are ignored,
 * today is never resolved, and re-running resolves nothing new.
 */
class DayCloseResolverTest {

    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var resolver: DayCloseResolver

    private val zone: ZoneId = ZoneId.of("UTC")
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), zone)
    private val today: LocalDate = LocalDate.now(fixedClock)
    private val yesterday: LocalDate = today.minusDays(1)

    private val quitHabitId = 1L
    private val buildHabitId = 2L

    @Before
    fun setup() {
        fakeHabitDao = FakeHabitDao()
        fakeCheckInDao = FakeCheckInDao()

        val habitRepository = HabitRepository(fakeHabitDao)
        val freezeRepository = FreezeRepository(fakeHabitDao, FakeFrozenDayDao())
        checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(
                FakeCashOutDao(),
                FakeCashOutAllocationDao(),
                freezeRepository
            ),
            fixedClock
        )
        resolver = DayCloseResolver(habitRepository, checkInRepository, fixedClock)
    }

    /** Epoch millis for the start of [date] in the test zone. */
    private fun createdAtMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun quitHabit(createdAt: LocalDate) = Habit(
        id = quitHabitId,
        name = "No doomscrolling",
        rewardValue = 500L,
        habitType = HabitType.QUIT,
        createdAt = createdAtMillis(createdAt)
    )

    private suspend fun checkInsFor(habitId: Long): List<CheckIn> =
        fakeCheckInDao.getAllCheckInsAsc(habitId)

    @Test
    fun `resolves every clean day from creation through yesterday when there are no check-ins`() = runTest {
        // Created 5 days before today; device effectively off since. Everything
        // from creation through yesterday should resolve clean.
        fakeHabitDao.seed(quitHabit(createdAt = today.minusDays(5)))

        resolver.resolveElapsedDays()

        val checkIns = checkInsFor(quitHabitId)
        assertEquals(
            listOf(
                today.minusDays(5),
                today.minusDays(4),
                today.minusDays(3),
                today.minusDays(2),
                yesterday
            ),
            checkIns.map { it.date }
        )
        assertTrue("every resolved day is clean", checkIns.all { it.didSucceed })
        // Reward accrues per clean day: 5 x 500c = 2500c on the last one.
        assertEquals(2500L, checkIns.last().balanceAfter)
        // Today is never resolved.
        assertNull(checkInRepository.getCheckInForDate(today, quitHabitId))
    }

    @Test
    fun `resolves only the gap after the last check-in`() = runTest {
        fakeHabitDao.seed(quitHabit(createdAt = today.minusDays(10)))
        // A clean day already recorded four days ago; nothing since.
        fakeCheckInDao.seed(
            CheckIn(
                id = 1L,
                date = today.minusDays(4),
                didSucceed = true,
                balanceAfter = 500L,
                rewardAtTime = 500L,
                habitId = quitHabitId
            )
        )

        resolver.resolveElapsedDays()

        val dates = checkInsFor(quitHabitId).map { it.date }
        // Existing day preserved; the gap after it (3 days) resolves; today does not.
        assertEquals(
            listOf(
                today.minusDays(4),
                today.minusDays(3),
                today.minusDays(2),
                yesterday
            ),
            dates
        )
    }

    @Test
    fun `leaves a logged slip untouched and never overwrites it to clean`() = runTest {
        fakeHabitDao.seed(quitHabit(createdAt = today.minusDays(3)))
        // The user logged a slip yesterday; it is already resolved.
        fakeCheckInDao.seed(
            CheckIn(
                id = 1L,
                date = yesterday,
                didSucceed = false,
                balanceAfter = 0L,
                rewardAtTime = 500L,
                habitId = quitHabitId
            )
        )

        resolver.resolveElapsedDays()

        val checkIns = checkInsFor(quitHabitId)
        // Nothing new: the only elapsed day is yesterday, which already has a slip.
        assertEquals(1, checkIns.size)
        assertEquals(yesterday, checkIns.first().date)
        assertFalse("the slip stays a slip", checkIns.first().didSucceed)
    }

    @Test
    fun `ignores build habits entirely`() = runTest {
        fakeHabitDao.seed(
            Habit(
                id = buildHabitId,
                name = "Workout",
                rewardValue = 500L,
                habitType = HabitType.BUILD,
                createdAt = createdAtMillis(today.minusDays(5))
            )
        )

        resolver.resolveElapsedDays()

        // A build habit's unlogged days stay misses - nothing is materialized.
        assertTrue(checkInsFor(buildHabitId).isEmpty())
    }

    @Test
    fun `never resolves today while resolving elapsed days`() = runTest {
        fakeHabitDao.seed(quitHabit(createdAt = today.minusDays(2)))

        resolver.resolveElapsedDays()

        val dates = checkInsFor(quitHabitId).map { it.date }
        assertEquals(listOf(today.minusDays(2), yesterday), dates)
        assertNull(checkInRepository.getCheckInForDate(today, quitHabitId))
    }

    @Test
    fun `is idempotent - a second run resolves nothing new`() = runTest {
        fakeHabitDao.seed(quitHabit(createdAt = today.minusDays(5)))

        resolver.resolveElapsedDays()
        val afterFirst = checkInsFor(quitHabitId)

        resolver.resolveElapsedDays()
        val afterSecond = checkInsFor(quitHabitId)

        assertEquals(afterFirst.map { it.date }, afterSecond.map { it.date })
        assertEquals(afterFirst.map { it.balanceAfter }, afterSecond.map { it.balanceAfter })
    }
}
