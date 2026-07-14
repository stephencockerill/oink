package com.oink.app.widget

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [WidgetDataLoader], the widget's render-time re-validation +
 * per-habit data loader.
 *
 * The point of these tests is the safety guarantee from MH-10: a widget must
 * never render a habit that is missing, deleted, or private. That is exactly the
 * null-vs-data contract of [WidgetDataLoader.resolveWidgetData]:
 * - private habit -> null (fallback)
 * - missing/deleted habit -> null (fallback)
 * - valid public habit -> its own balance and streak
 *
 * Uses the repository fakes and a fixed clock, so "today" is deterministic.
 */
class WidgetDataLoaderTest {

    private lateinit var fakeHabitDao: FakeHabitDao
    private lateinit var fakeCheckInDao: FakeCheckInDao
    private lateinit var fakeCashOutDao: FakeCashOutDao
    private lateinit var fakeCashOutAllocationDao: FakeCashOutAllocationDao
    private lateinit var loader: WidgetDataLoader

    private val fixedClock: Clock = Clock.fixed(
        Instant.parse("2025-01-15T12:00:00Z"),
        ZoneId.of("UTC")
    )
    private val today: LocalDate = LocalDate.now(fixedClock)

    private val publicHabitId = 1L
    private val privateHabitId = 2L
    private val missingHabitId = 999L

    @Before
    fun setup() {
        fakeHabitDao = FakeHabitDao().apply {
            seed(
                Habit(id = publicHabitId, name = "Workout", emoji = "🏋️", isPrivate = false),
                Habit(id = privateHabitId, name = "Journal", emoji = "📓", isPrivate = true)
            )
        }
        fakeCheckInDao = FakeCheckInDao()
        fakeCashOutDao = FakeCashOutDao()
        fakeCashOutAllocationDao = FakeCashOutAllocationDao()

        val habitRepository = HabitRepository(fakeHabitDao)
        val freezeRepository = FreezeRepository(fakeHabitDao, FakeFrozenDayDao())
        val checkInRepository = CheckInRepository(
            fakeCheckInDao,
            HabitRewardProvider(fakeHabitDao),
            DefaultDeductionProvider(fakeCashOutDao, fakeCashOutAllocationDao, freezeRepository),
            fixedClock
        )
        val cashOutRepository = CashOutRepository(
            fakeCashOutDao,
            fakeCashOutAllocationDao,
            checkInRepository,
            habitRepository,
            freezeRepository,
            FakeTransactionRunner()
        )
        loader = WidgetDataLoader(
            habitRepository,
            checkInRepository,
            cashOutRepository,
            fixedClock
        )
    }

    @Test
    fun `private habit resolves to null so the widget falls back`() = runTest {
        assertNull(loader.resolveWidgetData(privateHabitId))
    }

    @Test
    fun `habit toggled private after selection resolves to null`() = runTest {
        // The habit was public when the widget was configured; toggling it private
        // must blank the widget on the next render.
        fakeHabitDao.update(
            Habit(id = publicHabitId, name = "Workout", emoji = "🏋️", isPrivate = true)
        )

        assertNull(loader.resolveWidgetData(publicHabitId))
    }

    @Test
    fun `missing or deleted habit resolves to null so the widget falls back`() = runTest {
        assertNull(loader.resolveWidgetData(missingHabitId))
    }

    @Test
    fun `valid public habit resolves to its own balance and streak`() = runTest {
        // Two consecutive completed days ending today: streak 2, balance = latest
        // check-in's balanceAfter (no cash-outs or freeze spending).
        fakeCheckInDao.setCheckIns(
            listOf(
                CheckIn(
                    id = 1L,
                    date = today.minusDays(1),
                    completed = true,
                    balanceAfter = 1000L,
                    habitId = publicHabitId
                ),
                CheckIn(
                    id = 2L,
                    date = today,
                    completed = true,
                    balanceAfter = 1500L,
                    habitId = publicHabitId
                )
            )
        )

        val data = loader.resolveWidgetData(publicHabitId)

        assertNotNull(data)
        requireNotNull(data)
        assertEquals("Workout", data.habitName)
        assertEquals("🏋️", data.habitEmoji)
        assertEquals(1500L, data.balance)
        assertEquals(2, data.streak)
        assertTrue(data.checkedInToday)
        assertEquals(true, data.completedToday)
    }

    @Test
    fun `valid public habit with no check-ins resolves to a neutral zero state`() = runTest {
        val data = loader.resolveWidgetData(publicHabitId)

        assertNotNull(data)
        requireNotNull(data)
        assertEquals(0L, data.balance)
        assertEquals(0, data.streak)
        assertFalse(data.checkedInToday)
        assertNull(data.completedToday)
    }

    @Test
    fun `balance is the habit's own spendable, not another habit's`() = runTest {
        // A different public habit has a large balance; the widget for
        // publicHabitId must show only its own (zero here), never the pot.
        fakeHabitDao.insert(Habit(id = 3L, name = "Read", emoji = "📚", isPrivate = false))
        fakeCheckInDao.setCheckIns(
            listOf(
                CheckIn(
                    id = 1L,
                    date = today,
                    completed = true,
                    balanceAfter = 9000L,
                    habitId = 3L
                )
            )
        )

        val data = loader.resolveWidgetData(publicHabitId)

        assertNotNull(data)
        requireNotNull(data)
        assertEquals(0L, data.balance)
    }
}
