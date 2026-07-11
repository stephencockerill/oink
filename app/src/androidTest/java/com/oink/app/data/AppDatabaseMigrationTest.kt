package com.oink.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Scaffold for exercising Room migrations against the schema JSON exported to
 * app/schemas. Each schema bump adds a migration to [AppDatabase] plus a test
 * here that runs it and asserts the resulting schema is valid.
 *
 * The single test below only opens v1 to prove the harness and exported schema
 * are wired up. Per-version migration tests get added as the schema evolves.
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

    // Template for future migrations. Copy per version bump:
    //
    // @Test
    // @Throws(IOException::class)
    // fun migrate1To2() {
    //     helper.createDatabase(TEST_DB, 1).apply {
    //         // insert v1 rows with execSQL, then close
    //         close()
    //     }
    //     helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)
    // }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
