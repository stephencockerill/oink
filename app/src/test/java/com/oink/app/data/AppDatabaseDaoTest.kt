package com.oink.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Exercises the real Room DAOs, generated SQL, the unique index on
 * check_ins.date, and the [Converters] LocalDate<->epoch-day converter against
 * a real (in-memory) SQLite database via Robolectric.
 *
 * The [FakeCheckInDao]/[FakeCashOutDao] used elsewhere re-implement this logic
 * in Kotlin, so none of the real SQL, the unique constraint, or the type
 * converter is ever executed by those tests. This test closes that gap.
 *
 * All money values are in cents (Long), e.g. $5.00 is 500.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AppDatabaseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var checkInDao: CheckInDao
    private lateinit var cashOutDao: CashOutDao
    private lateinit var habitDao: HabitDao

    @Before
    fun createDb() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        checkInDao = db.checkInDao()
        cashOutDao = db.cashOutDao()
        habitDao = db.habitDao()

        // Check-ins carry a foreign key to habits, so the default habit
        // (id = 1, the parent every check-in defaults to) must exist first.
        habitDao.insert(Habit(id = 1L, name = "Workout"))
    }

    @After
    fun closeDb() {
        db.close()
    }

    // region CheckIn DAO + converter

    /**
     * A LocalDate written through the DAO round-trips back to the same
     * LocalDate, proving the [Converters] converter runs in both directions
     * against real SQLite.
     */
    @Test
    fun insertAndReadBack_roundTripsLocalDate() = runTest {
        val date = LocalDate.of(2024, 6, 15)
        val id = checkInDao.insert(
            CheckIn(date = date, didExercise = true, balanceAfter = 1234, exerciseRewardAtTime = 500)
        )

        val stored = checkInDao.getCheckInForDate(1L, date.toEpochDay())

        assertEquals(id, stored?.id)
        assertEquals(date, stored?.date)
        assertTrue(stored!!.didExercise)
        assertEquals(1234L, stored.balanceAfter)
        assertEquals(500L, stored.exerciseRewardAtTime)
    }

    /**
     * The converter stores dates as epoch day, so a lookup keyed by
     * [LocalDate.toEpochDay] finds the row the DAO persisted.
     */
    @Test
    fun getCheckInForDate_matchesOnEpochDay() = runTest {
        val date = LocalDate.of(2020, 1, 1)
        checkInDao.insert(CheckIn(date = date, didExercise = false, balanceAfter = 0))

        assertNull(checkInDao.getCheckInForDate(1L, date.toEpochDay() - 1))
        assertEquals(date, checkInDao.getCheckInForDate(1L, date.toEpochDay())?.date)
    }

    /**
     * The unique index on `date` plus [androidx.room.OnConflictStrategy.REPLACE]
     * means inserting a second check-in for the same day replaces the first
     * rather than creating a duplicate. Enforced by real SQL, not by the fake.
     */
    @Test
    fun insertSameDate_replacesViaUniqueIndex() = runTest {
        val date = LocalDate.of(2024, 3, 10)
        checkInDao.insert(CheckIn(date = date, didExercise = false, balanceAfter = 100))
        checkInDao.insert(CheckIn(date = date, didExercise = true, balanceAfter = 600))

        val all = checkInDao.getAllCheckInsAsc(1L)
        assertEquals(1, all.size)
        assertTrue(all.first().didExercise)
        assertEquals(600L, all.first().balanceAfter)
    }

    /**
     * Ordering queries (ASC/DESC/latest/before) run the real ORDER BY and
     * comparison against the epoch-day column.
     */
    @Test
    fun orderingQueries_useEpochDayColumn() = runTest {
        val jan = LocalDate.of(2024, 1, 1)
        val feb = LocalDate.of(2024, 2, 1)
        val mar = LocalDate.of(2024, 3, 1)
        checkInDao.insert(CheckIn(date = feb, didExercise = true, balanceAfter = 200))
        checkInDao.insert(CheckIn(date = jan, didExercise = true, balanceAfter = 100))
        checkInDao.insert(CheckIn(date = mar, didExercise = true, balanceAfter = 300))

        assertEquals(listOf(jan, feb, mar), checkInDao.getAllCheckInsAsc(1L).map { it.date })
        assertEquals(mar, checkInDao.getLatestCheckIn(1L)?.date)
        assertEquals(jan, checkInDao.getCheckInBefore(1L, feb.toEpochDay())?.date)
        assertNull(checkInDao.getCheckInBefore(1L, jan.toEpochDay()))
    }

    /**
     * COUNT(*) filtered on didExercise = 1 runs against the stored boolean.
     */
    @Test
    fun getTotalWorkoutCount_countsExerciseDays() = runTest {
        checkInDao.insert(CheckIn(date = LocalDate.of(2024, 1, 1), didExercise = true, balanceAfter = 100))
        checkInDao.insert(CheckIn(date = LocalDate.of(2024, 1, 2), didExercise = false, balanceAfter = 50))
        checkInDao.insert(CheckIn(date = LocalDate.of(2024, 1, 3), didExercise = true, balanceAfter = 550))

        assertEquals(2, checkInDao.getTotalWorkoutCount(1L))
    }

    /**
     * The Flow query emits the current rows, newest first, from real SQLite.
     */
    @Test
    fun getAllCheckInsFlow_emitsNewestFirst() = runTest {
        checkInDao.insert(CheckIn(date = LocalDate.of(2024, 1, 1), didExercise = true, balanceAfter = 100))
        checkInDao.insert(CheckIn(date = LocalDate.of(2024, 1, 5), didExercise = true, balanceAfter = 600))

        val dates = checkInDao.getAllCheckInsFlow(1L).first().map { it.date }
        assertEquals(listOf(LocalDate.of(2024, 1, 5), LocalDate.of(2024, 1, 1)), dates)
    }

    // endregion

    // region CashOut DAO

    /**
     * Cash-outs insert and read back through the real DAO, and the SUM
     * aggregation adds the cent amounts.
     */
    @Test
    fun cashOut_insertAndSum() = runTest {
        cashOutDao.insert(
            CashOut(
                name = "Darts",
                amount = 2500,
                emoji = "🎯",
                cashedOutAt = 1_700_000_000_000,
                balanceBefore = 5000,
                balanceAfter = 2500
            )
        )
        cashOutDao.insert(
            CashOut(
                name = "Coffee",
                amount = 750,
                emoji = "☕",
                cashedOutAt = 1_700_000_100_000,
                balanceBefore = 2500,
                balanceAfter = 1750
            )
        )

        assertEquals(2, cashOutDao.getCashOutCount())
        assertEquals(3250L, cashOutDao.getTotalCashedOut())
        assertEquals(3250L, cashOutDao.getTotalCashedOutFlow().first())
        assertEquals("Coffee", cashOutDao.getMostRecentCashOut()?.name)
    }

    /**
     * SUM over an empty table returns 0 via COALESCE rather than null.
     */
    @Test
    fun getTotalCashedOut_emptyReturnsZero() = runTest {
        assertEquals(0L, cashOutDao.getTotalCashedOut())
    }

    /**
     * Delete removes the targeted row and leaves the rest intact.
     */
    @Test
    fun cashOut_delete() = runTest {
        val id = cashOutDao.insert(
            CashOut(name = "Darts", amount = 2500, balanceBefore = 5000, balanceAfter = 2500)
        )
        val stored = cashOutDao.getById(id)!!

        cashOutDao.delete(stored)

        assertNull(cashOutDao.getById(id))
        assertEquals(0, cashOutDao.getCashOutCount())
    }

    // endregion
}
