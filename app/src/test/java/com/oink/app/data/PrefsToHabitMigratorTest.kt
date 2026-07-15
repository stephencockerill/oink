package com.oink.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Runs [PrefsToHabitMigrator] against a real (in-memory) Room database and
 * a real file-backed DataStore, proving the one-time copy is applied once and is
 * idempotent under re-runs and partial-failure retries.
 *
 * All money values are in cents (Long), e.g. $5.00 is 500.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PrefsToHabitMigratorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var frozenDayDao: FrozenDayDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope

    private val dayA = LocalDate.of(2025, 1, 10)
    private val dayB = LocalDate.of(2025, 1, 11)

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        habitDao = db.habitDao()
        frozenDayDao = db.frozenDayDao()

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            tmpFolder.newFile("migrator_test.preferences_pb")
        }
    }

    @After
    fun teardown() {
        db.close()
        dataStoreScope.cancel()
    }

    /**
     * Simulate an upgraded install: MIGRATION_3_4 seeded the default habit
     * (id = 1) before this startup migration runs.
     */
    private suspend fun seedDefaultHabit() {
        habitDao.insert(Habit(id = HabitRepository.DEFAULT_HABIT_ID, name = "Workout"))
    }

    private suspend fun seedLegacyPreferences() {
        dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.DAILY_REWARD] = 1000L
            prefs[PrefsToHabitMigrator.LEGACY_AVAILABLE_FREEZES] = 2
            // Legacy freeze spending is a Double of dollars; $15.00 -> 1500 cents.
            prefs[PrefsToHabitMigrator.LEGACY_TOTAL_FREEZE_SPENDING] = 15.0
            prefs[PrefsToHabitMigrator.LEGACY_FROZEN_DATES] = setOf(
                dayA.toEpochDay().toString(),
                dayB.toEpochDay().toString()
            )
        }
    }

    @Test
    fun firstRun_copiesPreferencesOntoHabitAndSetsFlag() = runTest {
        seedDefaultHabit()
        seedLegacyPreferences()

        PrefsToHabitMigrator(dataStore, habitDao, frozenDayDao).migrateIfNeeded()

        val habit = habitDao.getById(HabitRepository.DEFAULT_HABIT_ID)!!
        assertEquals(1000L, habit.rewardValue)
        assertEquals(2, habit.availableFreezes)
        assertEquals(1500L, habit.totalFreezeSpending)

        val frozen = frozenDayDao.getFrozenDays(HabitRepository.DEFAULT_HABIT_ID)
        assertEquals(setOf(dayA, dayB), frozen.map { it.date }.toSet())

        assertTrue(dataStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true)
    }

    /**
     * Regression for the v1-upgrade data-loss bug: the shipped v1 app wrote
     * `exercise_reward` and `total_freeze_spending` as Doubles of dollars. Reading
     * them through Long keys returned the boxed Double and threw
     * [ClassCastException] on unboxing, aborting the copy and silently dropping
     * freeze state, the custom reward, and every frozen day.
     *
     * This seeds the preferences file the way the v1 app actually wrote it - as
     * Doubles, before [DailyRewardKeyMigration] has run - using the real decoded
     * device values (freeze spending $10.00, one frozen day 2026-04-29), then
     * reopens the file with the production key migration wired in, exactly as the
     * app does at startup. It asserts the migrator does not throw and that dollars
     * convert to whole cents with the frozen day preserved.
     */
    @Test
    fun firstRun_realLegacyDoublePrefs_convertsDollarsToCentsAndPreservesFrozenDays() = runTest {
        seedDefaultHabit()

        // A file already holding the legacy prefs as the v1 app wrote them:
        // Doubles of dollars, before any key migration has touched them.
        val legacyFile = tmpFolder.newFile("legacy_double.preferences_pb")
        val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        PreferenceDataStoreFactory.create(scope = seedScope) { legacyFile }.edit { prefs ->
            prefs[doublePreferencesKey("exercise_reward")] = 5.0
            prefs[doublePreferencesKey("total_freeze_spending")] = 10.0
            prefs[intPreferencesKey("available_freezes")] = 0
            prefs[stringSetPreferencesKey("frozen_dates")] = setOf("20572")
        }
        // Release the single-instance-per-file lock before reopening the file.
        seedScope.coroutineContext.job.cancelAndJoin()

        // Reopen the same file the way OinkApplication does: with the key
        // migration that converts exercise_reward (dollars) -> daily_reward (cents).
        val prodScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prodStore = PreferenceDataStoreFactory.create(
            scope = prodScope,
            migrations = listOf(DailyRewardKeyMigration)
        ) { legacyFile }

        try {
            PrefsToHabitMigrator(prodStore, habitDao, frozenDayDao).migrateIfNeeded()

            val habit = habitDao.getById(HabitRepository.DEFAULT_HABIT_ID)!!
            assertEquals(500L, habit.rewardValue)          // $5.00 -> 500 cents
            assertEquals(0, habit.availableFreezes)
            assertEquals(1000L, habit.totalFreezeSpending) // $10.00 -> 1000 cents

            val frozen = frozenDayDao.getFrozenDays(HabitRepository.DEFAULT_HABIT_ID)
            assertEquals(
                setOf(LocalDate.ofEpochDay(20572)), // 2026-04-29
                frozen.map { it.date }.toSet()
            )

            assertTrue(prodStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true)
            // The legacy dollar key was drained by DailyRewardKeyMigration.
            assertNull(prodStore.data.first()[doublePreferencesKey("exercise_reward")])
        } finally {
            prodScope.coroutineContext.job.cancelAndJoin()
        }
    }

    /**
     * A user who never customized their reward has no `exercise_reward` key. The
     * copy must fall back to the app default rather than crash, while still
     * draining the freeze state that IS present.
     */
    @Test
    fun firstRun_absentRewardPref_fallsBackToDefaultWithoutCrashing() = runTest {
        seedDefaultHabit()
        dataStore.edit { prefs ->
            // No reward key of any kind.
            prefs[PrefsToHabitMigrator.LEGACY_AVAILABLE_FREEZES] = 3
            prefs[PrefsToHabitMigrator.LEGACY_TOTAL_FREEZE_SPENDING] = 10.0
            prefs[PrefsToHabitMigrator.LEGACY_FROZEN_DATES] = emptySet()
        }

        PrefsToHabitMigrator(dataStore, habitDao, frozenDayDao).migrateIfNeeded()

        val habit = habitDao.getById(HabitRepository.DEFAULT_HABIT_ID)!!
        assertEquals(PreferencesRepository.DEFAULT_DAILY_REWARD, habit.rewardValue)
        assertEquals(3, habit.availableFreezes)
        assertEquals(1000L, habit.totalFreezeSpending) // $10.00 -> 1000 cents
        assertTrue(dataStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true)
    }

    @Test
    fun secondRun_isNoOp_doesNotReapplyOrDuplicate() = runTest {
        seedDefaultHabit()
        seedLegacyPreferences()

        val migrator = PrefsToHabitMigrator(dataStore, habitDao, frozenDayDao)
        migrator.migrateIfNeeded()

        // Change the DataStore values after the first run. Because the run-once
        // flag is set, a second run must bail and NOT re-copy these onto the
        // habit - proving the guard, not merely the absolute-write idempotency.
        dataStore.edit { prefs ->
            prefs[OinkPreferenceKeys.DAILY_REWARD] = 9999L
            prefs[PrefsToHabitMigrator.LEGACY_AVAILABLE_FREEZES] = 0
            prefs[PrefsToHabitMigrator.LEGACY_TOTAL_FREEZE_SPENDING] = 88.88
            prefs[PrefsToHabitMigrator.LEGACY_FROZEN_DATES] = setOf(
                LocalDate.of(2025, 2, 2).toEpochDay().toString()
            )
        }

        migrator.migrateIfNeeded()

        val habit = habitDao.getById(HabitRepository.DEFAULT_HABIT_ID)!!
        assertEquals(1000L, habit.rewardValue)
        assertEquals(2, habit.availableFreezes)
        assertEquals(1500L, habit.totalFreezeSpending) // not doubled, not overwritten

        val frozen = frozenDayDao.getFrozenDays(HabitRepository.DEFAULT_HABIT_ID)
        assertEquals(2, frozen.size) // no duplicates, no new date added
        assertEquals(setOf(dayA, dayB), frozen.map { it.date }.toSet())
    }

    @Test
    fun freshInstall_noSeededHabit_skipsCopyButSetsFlag() = runTest {
        // No habit seeded - a fresh install at v4 has no habit id = 1.
        seedLegacyPreferences()

        PrefsToHabitMigrator(dataStore, habitDao, frozenDayDao).migrateIfNeeded()

        assertNull(habitDao.getById(HabitRepository.DEFAULT_HABIT_ID))
        assertTrue(frozenDayDao.getFrozenDays(HabitRepository.DEFAULT_HABIT_ID).isEmpty())
        // Flag is still set so this does not retry on every launch.
        assertTrue(dataStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true)
    }

    @Test
    fun partialFailure_doesNotSetFlag_andRetryConverges() = runTest {
        seedDefaultHabit()
        seedLegacyPreferences()

        // A DAO that throws on the first insert to simulate a crash mid-copy.
        val flakyDao = FailOnFirstInsertFrozenDayDao(frozenDayDao)
        val failingMigrator = PrefsToHabitMigrator(dataStore, habitDao, flakyDao)

        var thrown: Throwable? = null
        try {
            failingMigrator.migrateIfNeeded()
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is IllegalStateException)

        // The DataStore edit aborted, so the flag was never committed.
        assertNull(dataStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1])

        // Retry with a healthy DAO converges: absolute habit write and IGNORE
        // inserts mean no doubling and no duplicate frozen days.
        PrefsToHabitMigrator(dataStore, habitDao, frozenDayDao).migrateIfNeeded()

        val habit = habitDao.getById(HabitRepository.DEFAULT_HABIT_ID)!!
        assertEquals(1000L, habit.rewardValue)
        assertEquals(1500L, habit.totalFreezeSpending)

        val frozen = frozenDayDao.getFrozenDays(HabitRepository.DEFAULT_HABIT_ID)
        assertEquals(setOf(dayA, dayB), frozen.map { it.date }.toSet())
        assertTrue(dataStore.data.first()[OinkPreferenceKeys.PREFS_MIGRATED_TO_HABIT_V1] == true)
    }

    /**
     * Delegates to a real [FrozenDayDao] but throws on the first [insert],
     * simulating a crash partway through the copy.
     */
    private class FailOnFirstInsertFrozenDayDao(
        private val delegate: FrozenDayDao
    ) : FrozenDayDao {
        private var firstInsert = true

        override suspend fun insert(frozenDay: FrozenDay): Long {
            if (firstInsert) {
                firstInsert = false
                throw IllegalStateException("Simulated crash during frozen-day insert")
            }
            return delegate.insert(frozenDay)
        }

        override suspend fun delete(frozenDay: FrozenDay) = delegate.delete(frozenDay)

        override fun getFrozenDaysFlow(habitId: Long): Flow<List<FrozenDay>> =
            delegate.getFrozenDaysFlow(habitId)

        override suspend fun getFrozenDays(habitId: Long): List<FrozenDay> =
            delegate.getFrozenDays(habitId)

        override suspend fun getFrozenDay(habitId: Long, epochDay: Long): FrozenDay? =
            delegate.getFrozenDay(habitId, epochDay)

        override suspend fun deleteByDate(habitId: Long, epochDay: Long) =
            delegate.deleteByDate(habitId, epochDay)
    }
}
