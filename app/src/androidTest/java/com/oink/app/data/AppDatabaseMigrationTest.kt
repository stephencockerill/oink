package com.oink.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
