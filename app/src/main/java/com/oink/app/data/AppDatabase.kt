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
 * - v2: Money columns changed from REAL (Double dollars) to INTEGER (Long cents):
 *       CheckIn.balanceAfter, CashOut.amount/balanceBefore/balanceAfter/exerciseRewardAtTime.
 *       [MIGRATION_1_2] converts existing rows by rounding dollars to whole cents.
 *
 * IMPORTANT: When bumping the version, add a migration!
 * Never use fallbackToDestructiveMigration() in production - it wipes user data.
 */
@Database(
    entities = [CheckIn::class, CashOut::class],
    version = 2,
    exportSchema = true
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v1 -> v2: money moves from REAL (dollars) to INTEGER (cents).
         *
         * SQLite can't change a column's type in place, so each table is
         * recreated with the new INTEGER columns and its rows copied over,
         * rounding dollars to the nearest whole cent (CAST(ROUND(x * 100))).
         * The unique index on check_ins.date is recreated to match the schema
         * Room expects.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // check_ins
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `check_ins_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`didExercise` INTEGER NOT NULL, " +
                        "`balanceAfter` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `check_ins_new` (`id`, `date`, `didExercise`, `balanceAfter`) " +
                        "SELECT `id`, `date`, `didExercise`, CAST(ROUND(`balanceAfter` * 100) AS INTEGER) " +
                        "FROM `check_ins`"
                )
                db.execSQL("DROP TABLE `check_ins`")
                db.execSQL("ALTER TABLE `check_ins_new` RENAME TO `check_ins`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_check_ins_date` ON `check_ins` (`date`)")

                // cash_outs
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_outs_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`cashedOutAt` INTEGER NOT NULL, " +
                        "`balanceBefore` INTEGER NOT NULL, " +
                        "`balanceAfter` INTEGER NOT NULL, " +
                        "`exerciseRewardAtTime` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `cash_outs_new` " +
                        "(`id`, `name`, `amount`, `emoji`, `cashedOutAt`, `balanceBefore`, `balanceAfter`, `exerciseRewardAtTime`) " +
                        "SELECT `id`, `name`, CAST(ROUND(`amount` * 100) AS INTEGER), `emoji`, `cashedOutAt`, " +
                        "CAST(ROUND(`balanceBefore` * 100) AS INTEGER), " +
                        "CAST(ROUND(`balanceAfter` * 100) AS INTEGER), " +
                        "CAST(ROUND(`exerciseRewardAtTime` * 100) AS INTEGER) " +
                        "FROM `cash_outs`"
                )
                db.execSQL("DROP TABLE `cash_outs`")
                db.execSQL("ALTER TABLE `cash_outs_new` RENAME TO `cash_outs`")
            }
        }
    }
}
