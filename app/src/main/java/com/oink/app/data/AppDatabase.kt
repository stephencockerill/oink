package com.oink.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Oink.
 *
 * Version history:
 * - v1: Initial release with CheckIn and CashOut entities
 *
 * IMPORTANT: When bumping the version, add a migration!
 * Never use fallbackToDestructiveMigration() in production—it wipes user data.
 */
@Database(
    entities = [CheckIn::class, CashOut::class],
    version = 1,
    exportSchema = false // Enable and configure room.schemaLocation when you need migration testing
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checkInDao(): CheckInDao
    abstract fun cashOutDao(): CashOutDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * Why synchronized? Because we don't want multiple threads
         * creating multiple database instances. That would be a
         * fucking disaster for data consistency.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oink_database"
                )
                    // TODO: REMOVE THIS before first production release!
                    // This is only here for development convenience. Once you have
                    // real users with real data, you MUST add proper migrations
                    // and remove this line, or you'll wipe their data on updates.
                    .fallbackToDestructiveMigration()
                    // Add migrations here as the schema evolves:
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, etc.)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Example migration template for future schema changes.
         * Copy this pattern when you need to add a migration.
         *
         * MIGRATION_1_2 would look like:
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         database.execSQL("ALTER TABLE cash_outs ADD COLUMN newColumn TEXT DEFAULT ''")
         *     }
         * }
         */
    }
}

