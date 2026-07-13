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
 * - v3: CheckIn gains exerciseRewardAtTime so historical balances replay with the
 *       reward in force on each day, not today's setting. [MIGRATION_2_3] adds the
 *       column defaulting existing rows to the $5.00 default.
 * - v4: Multi-habit data model. Adds the Habit parent table plus the FrozenDay
 *       and CashOutAllocation child tables. check_ins gains a habitId foreign key
 *       (unique index becomes (habitId, date)); cash_outs stays pot-level and
 *       drops its per-cash-out exerciseRewardAtTime, which is captured into one
 *       CashOutAllocation per legacy cash-out. [MIGRATION_3_4] seeds the single
 *       default habit (id = 1, "Workout") and migrates all existing rows under it.
 *
 * IMPORTANT: When bumping the version, add a migration!
 * Never use fallbackToDestructiveMigration() in production - it wipes user data.
 */
@Database(
    entities = [
        CheckIn::class,
        CashOut::class,
        Habit::class,
        FrozenDay::class,
        CashOutAllocation::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun checkInDao(): CheckInDao
    abstract fun cashOutDao(): CashOutDao
    abstract fun habitDao(): HabitDao
    abstract fun frozenDayDao(): FrozenDayDao
    abstract fun cashOutAllocationDao(): CashOutAllocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * Why synchronized? Because we don't want multiple threads
         * creating multiple database instances. That would be a
         * disaster for data consistency.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oink_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        /**
         * v2 -> v3: add check_ins.exerciseRewardAtTime.
         *
         * A plain additive column, so a single ALTER TABLE suffices. Existing
         * rows predate per-day reward tracking; they default to the $5.00
         * (500 cent) default, which is the only reward the app ever used before
         * this column existed.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `check_ins` ADD COLUMN `exerciseRewardAtTime` INTEGER NOT NULL DEFAULT 500"
                )
            }
        }

        /**
         * v3 -> v4: multi-habit data model.
         *
         * Introduces the `habits` parent table with a single seeded default
         * habit (id = 1, "Workout") that owns every pre-existing row, plus the
         * `frozen_days` and `cash_out_allocations` child tables.
         *
         * `check_ins` and `cash_outs` are rebuilt because SQLite cannot add a
         * foreign key or drop a column in place - each is recreated with the new
         * shape and its rows copied over (the create-`_new` / copy / drop /
         * rename idiom used by [MIGRATION_1_2]):
         * - `check_ins` gains `habitId` (defaulting to the seeded habit) and
         *   swaps its unique index from `(date)` to `(habitId, date)`.
         * - `cash_outs` drops `exerciseRewardAtTime` and stays pot-level. That
         *   per-cash-out reward is not lost: it is snapshotted into a scratch
         *   table before the column disappears, then written into one
         *   `cash_out_allocations` row per legacy cash-out (attributed to the
         *   default habit) after `cash_outs` is rebuilt.
         *
         * Ordering matters. `cash_outs` is rebuilt (dropping the old table)
         * BEFORE `cash_out_allocations` is created, so the drop's implicit
         * delete cannot cascade into freshly backfilled allocations. Foreign
         * keys stay satisfied throughout: the parent `habits` row exists before
         * any child references it, and rebuilt rows preserve their original ids.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the habits parent table and seed the default habit.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`rewardValue` INTEGER NOT NULL, " +
                        "`availableFreezes` INTEGER NOT NULL, " +
                        "`totalFreezeSpending` INTEGER NOT NULL, " +
                        "`isPrivate` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                // Seed defaults only. Backfilling the habit's reward, freezes,
                // and freeze spending from DataStore preferences is a later
                // migration phase; nothing reads these fields yet.
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `habits` " +
                        "(`id`, `name`, `emoji`, `rewardValue`, `availableFreezes`, " +
                        "`totalFreezeSpending`, `isPrivate`, `sortOrder`, `createdAt`) " +
                        "VALUES (1, 'Workout', '🏋️', 500, 0, 0, 0, 0, $now)"
                )

                // 2. Rebuild check_ins with a habitId foreign key and the new
                //    (habitId, date) unique index. Nothing references check_ins,
                //    so dropping the old table cannot cascade.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `check_ins_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`didExercise` INTEGER NOT NULL, " +
                        "`balanceAfter` INTEGER NOT NULL, " +
                        "`exerciseRewardAtTime` INTEGER NOT NULL, " +
                        "`habitId` INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO `check_ins_new` " +
                        "(`id`, `date`, `didExercise`, `balanceAfter`, `exerciseRewardAtTime`, `habitId`) " +
                        "SELECT `id`, `date`, `didExercise`, `balanceAfter`, `exerciseRewardAtTime`, 1 " +
                        "FROM `check_ins`"
                )
                db.execSQL("DROP TABLE `check_ins`")
                db.execSQL("ALTER TABLE `check_ins_new` RENAME TO `check_ins`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_check_ins_habitId_date` " +
                        "ON `check_ins` (`habitId`, `date`)"
                )

                // 3. Snapshot each cash-out's reward before dropping the column,
                //    so it survives the cash_outs rebuild for step 5.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_out_reward_backup` (" +
                        "`cashOutId` INTEGER NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`reward` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `cash_out_reward_backup` (`cashOutId`, `amount`, `reward`) " +
                        "SELECT `id`, `amount`, `exerciseRewardAtTime` FROM `cash_outs`"
                )

                // 4. Rebuild cash_outs without exerciseRewardAtTime, keeping it
                //    pot-level. No allocations exist yet, so the drop is safe.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_outs_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`cashedOutAt` INTEGER NOT NULL, " +
                        "`balanceBefore` INTEGER NOT NULL, " +
                        "`balanceAfter` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `cash_outs_new` " +
                        "(`id`, `name`, `amount`, `emoji`, `cashedOutAt`, `balanceBefore`, `balanceAfter`) " +
                        "SELECT `id`, `name`, `amount`, `emoji`, `cashedOutAt`, `balanceBefore`, `balanceAfter` " +
                        "FROM `cash_outs`"
                )
                db.execSQL("DROP TABLE `cash_outs`")
                db.execSQL("ALTER TABLE `cash_outs_new` RENAME TO `cash_outs`")

                // 5. Create cash_out_allocations and backfill one allocation per
                //    legacy cash-out, attributed to the default habit, capturing
                //    the snapshotted amount and reward.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cash_out_allocations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`cashOutId` INTEGER NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`exerciseRewardAtTime` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`cashOutId`) REFERENCES `cash_outs`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_cash_out_allocations_cashOutId_habitId` " +
                        "ON `cash_out_allocations` (`cashOutId`, `habitId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cash_out_allocations_habitId` " +
                        "ON `cash_out_allocations` (`habitId`)"
                )
                db.execSQL(
                    "INSERT INTO `cash_out_allocations` " +
                        "(`cashOutId`, `habitId`, `amount`, `exerciseRewardAtTime`) " +
                        "SELECT `cashOutId`, 1, `amount`, `reward` FROM `cash_out_reward_backup`"
                )
                db.execSQL("DROP TABLE `cash_out_reward_backup`")

                // 6. Create the frozen_days child table. New table, no backfill.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `frozen_days` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_frozen_days_habitId_date` " +
                        "ON `frozen_days` (`habitId`, `date`)"
                )
            }
        }
    }
}
