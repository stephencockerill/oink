package com.oink.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Exercises Room migrations against the schema JSON exported to app/schemas.
 * Each schema bump adds a migration to [AppDatabase] plus a test here that runs
 * it and asserts the resulting schema is valid.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Creates the database at v1 from the exported schema. Fails if the schema
     * JSON is missing or malformed, which guards exportSchema staying enabled.
     */
    @Test
    @Throws(IOException::class)
    fun migrate_createsSchemaAtVersion1() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    /**
     * v1 -> v2: money columns go from REAL dollars to INTEGER cents.
     *
     * Seeds v1 rows with dollar values (including a half-cent case that must
     * round), runs [AppDatabase.MIGRATION_1_2], validates the v2 schema, and
     * asserts each amount was converted to whole cents.
     */
    @Test
    @Throws(IOException::class)
    fun migrate1To2_convertsDollarsToCents() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO check_ins (id, date, didExercise, balanceAfter) " +
                    "VALUES (1, 20000, 1, 12.34), (2, 20001, 0, 0.125)"
            )
            execSQL(
                "INSERT INTO cash_outs " +
                    "(id, name, amount, emoji, cashedOutAt, balanceBefore, balanceAfter, exerciseRewardAtTime) " +
                    "VALUES (1, 'Darts', 25.0, '🎯', 1700000000000, 50.0, 25.0, 5.0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT balanceAfter FROM check_ins ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1234L, cursor.getLong(0)) // $12.34 -> 1234 cents
            assertTrue(cursor.moveToNext())
            assertEquals(13L, cursor.getLong(0))   // $0.125 -> 12.5 cents rounds to 13
        }
        db.query(
            "SELECT amount, balanceBefore, balanceAfter, exerciseRewardAtTime FROM cash_outs WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2500L, cursor.getLong(0)) // amount
            assertEquals(5000L, cursor.getLong(1)) // balanceBefore
            assertEquals(2500L, cursor.getLong(2)) // balanceAfter
            assertEquals(500L, cursor.getLong(3))  // exerciseRewardAtTime
        }
    }

    /**
     * v2 -> v3: check_ins gains exerciseRewardAtTime.
     *
     * Seeds a v2 check-in, runs [AppDatabase.MIGRATION_2_3], validates the v3
     * schema, and asserts the existing row backfills to the 500 cent default.
     */
    @Test
    @Throws(IOException::class)
    fun migrate2To3_addsExerciseRewardAtTimeDefaulting500() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO check_ins (id, date, didExercise, balanceAfter) " +
                    "VALUES (1, 20000, 1, 1234)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT exerciseRewardAtTime FROM check_ins WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(500L, cursor.getLong(0))
        }
    }

    /**
     * v3 -> v4: multi-habit data model.
     *
     * Seeds v3 check-ins and cash-outs (the latter with differing per-cash-out
     * rewards), runs [AppDatabase.MIGRATION_3_4], validates the v4 schema, and
     * asserts:
     * - the default habit (id = 1, "Workout") is seeded;
     * - every check-in and cash-out is preserved, with check-ins attributed to
     *   the default habit and balances untouched;
     * - exactly one allocation per legacy cash-out, capturing its amount and the
     *   reward that applied before the cash_outs column was dropped;
     * - the check_ins unique index is (habitId, date) and the old date-only
     *   index is gone.
     */
    @Test
    @Throws(IOException::class)
    fun migrate3To4_seedsDefaultHabitAndBackfillsAllocations() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO check_ins (id, date, didExercise, balanceAfter, exerciseRewardAtTime) " +
                    "VALUES (1, 20000, 1, 1234, 500), (2, 20001, 0, 617, 500)"
            )
            execSQL(
                "INSERT INTO cash_outs " +
                    "(id, name, amount, emoji, cashedOutAt, balanceBefore, balanceAfter, exerciseRewardAtTime) " +
                    "VALUES (1, 'Darts', 2500, '🎯', 1700000000000, 5000, 2500, 500), " +
                    "(2, 'Coffee', 1500, '☕', 1700000100000, 2500, 1000, 700)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        // Default habit seeded.
        db.query("SELECT id, name FROM habits ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getCount())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Workout", cursor.getString(1))
        }

        // Check-ins preserved and attributed to the default habit.
        db.query("SELECT habitId, balanceAfter FROM check_ins ORDER BY id").use { cursor ->
            assertEquals(2, cursor.getCount())
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1234L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(617L, cursor.getLong(1))
        }

        // Cash-outs preserved (pot-level, no habitId, no exerciseRewardAtTime).
        db.query("SELECT amount, balanceAfter FROM cash_outs ORDER BY id").use { cursor ->
            assertEquals(2, cursor.getCount())
            assertTrue(cursor.moveToFirst())
            assertEquals(2500L, cursor.getLong(0))
            assertEquals(2500L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals(1500L, cursor.getLong(0))
            assertEquals(1000L, cursor.getLong(1))
        }

        // Exactly one allocation per legacy cash-out, capturing amount + reward.
        db.query(
            "SELECT cashOutId, habitId, amount, exerciseRewardAtTime " +
                "FROM cash_out_allocations ORDER BY cashOutId"
        ).use { cursor ->
            assertEquals(2, cursor.getCount())
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0)) // cashOutId
            assertEquals(1L, cursor.getLong(1)) // default habit
            assertEquals(2500L, cursor.getLong(2))
            assertEquals(500L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals(1500L, cursor.getLong(2))
            assertEquals(700L, cursor.getLong(3)) // captured pre-drop reward
        }

        // Unique index is (habitId, date); old date-only index is gone.
        val checkInIndexes = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'check_ins'")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { checkInIndexes.add(it) }
                }
            }
        assertTrue(checkInIndexes.contains("index_check_ins_habitId_date"))
        assertFalse(checkInIndexes.contains("index_check_ins_date"))

        db.query("PRAGMA index_info(`index_check_ins_habitId_date`)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(listOf("habitId", "date"), columns)
        }
    }

    /**
     * v4 -> v5: neutral column names.
     *
     * Seeds a v4 database (a habit, a cash-out, two check-ins with
     * completed=true/false, and one allocation) carrying the old column names
     * `didExercise` and `exerciseRewardAtTime`, runs [AppDatabase.MIGRATION_4_5],
     * validates the v5 schema, and asserts every row survives under the new
     * `completed` / `rewardAtTime` names with its money values intact.
     */
    @Test
    @Throws(IOException::class)
    fun migrate4To5_renamesColumnsPreservingData() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO habits " +
                    "(id, name, emoji, rewardValue, availableFreezes, " +
                    "totalFreezeSpending, isPrivate, sortOrder, createdAt) " +
                    "VALUES (1, 'Workout', '🏋️', 500, 0, 0, 0, 0, 1700000000000)"
            )
            execSQL(
                "INSERT INTO cash_outs " +
                    "(id, name, amount, emoji, cashedOutAt, balanceBefore, balanceAfter) " +
                    "VALUES (1, 'Darts', 2500, '🎯', 1700000000000, 5000, 2500)"
            )
            execSQL(
                "INSERT INTO check_ins (id, date, didExercise, balanceAfter, exerciseRewardAtTime, habitId) " +
                    "VALUES (1, 20000, 1, 1234, 500, 1), (2, 20001, 0, 617, 700, 1)"
            )
            execSQL(
                "INSERT INTO cash_out_allocations " +
                    "(id, cashOutId, habitId, amount, exerciseRewardAtTime) " +
                    "VALUES (1, 1, 1, 2500, 500)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5)

        // check_ins: didExercise -> completed, exerciseRewardAtTime ->
        // rewardAtTime; balances and rewards preserved.
        db.query("SELECT completed, balanceAfter, rewardAtTime, habitId FROM check_ins ORDER BY id")
            .use { cursor ->
                assertEquals(2, cursor.getCount())
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0)) // completed = true
                assertEquals(1234L, cursor.getLong(1))
                assertEquals(500L, cursor.getLong(2))
                assertEquals(1L, cursor.getLong(3))
                assertTrue(cursor.moveToNext())
                assertEquals(0L, cursor.getLong(0)) // completed = false
                assertEquals(617L, cursor.getLong(1))
                assertEquals(700L, cursor.getLong(2))
                assertEquals(1L, cursor.getLong(3))
            }

        // cash_out_allocations: exerciseRewardAtTime -> rewardAtTime; amount and
        // captured reward preserved.
        db.query("SELECT cashOutId, habitId, amount, rewardAtTime FROM cash_out_allocations")
            .use { cursor ->
                assertEquals(1, cursor.getCount())
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
                assertEquals(2500L, cursor.getLong(2))
                assertEquals(500L, cursor.getLong(3))
            }

        // check_ins unique index is still (habitId, date).
        db.query("PRAGMA index_info(`index_check_ins_habitId_date`)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(listOf("habitId", "date"), columns)
        }
    }

    /**
     * v5 -> v6: check_ins.completed -> didSucceed.
     *
     * Seeds a v5 database (a habit plus two check-ins carrying the old
     * `completed` column, one true and one false), runs
     * [AppDatabase.MIGRATION_5_6], validates the v6 schema, and asserts both rows
     * survive under the new `didSucceed` name with their boolean and money values
     * intact.
     */
    @Test
    @Throws(IOException::class)
    fun migrate5To6_renamesCompletedToDidSucceedPreservingData() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                "INSERT INTO habits " +
                    "(id, name, emoji, rewardValue, availableFreezes, " +
                    "totalFreezeSpending, isPrivate, sortOrder, createdAt) " +
                    "VALUES (1, 'Workout', '🏋️', 500, 0, 0, 0, 0, 1700000000000)"
            )
            execSQL(
                "INSERT INTO check_ins (id, date, completed, balanceAfter, rewardAtTime, habitId) " +
                    "VALUES (1, 20000, 1, 1234, 500, 1), (2, 20001, 0, 617, 700, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6)

        // check_ins: completed -> didSucceed; booleans, balances, and rewards
        // preserved.
        db.query("SELECT didSucceed, balanceAfter, rewardAtTime, habitId FROM check_ins ORDER BY id")
            .use { cursor ->
                assertEquals(2, cursor.getCount())
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0)) // didSucceed = true
                assertEquals(1234L, cursor.getLong(1))
                assertEquals(500L, cursor.getLong(2))
                assertEquals(1L, cursor.getLong(3))
                assertTrue(cursor.moveToNext())
                assertEquals(0L, cursor.getLong(0)) // didSucceed = false
                assertEquals(617L, cursor.getLong(1))
                assertEquals(700L, cursor.getLong(2))
                assertEquals(1L, cursor.getLong(3))
            }

        // check_ins unique index is still (habitId, date).
        db.query("PRAGMA index_info(`index_check_ins_habitId_date`)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(listOf("habitId", "date"), columns)
        }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
