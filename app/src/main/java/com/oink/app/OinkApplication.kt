package com.oink.app

import android.app.Application
import com.oink.app.data.AppDatabase
import com.oink.app.data.PrefsToHabitMigrator
import com.oink.app.data.dataStore
import com.oink.app.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for Oink.
 * Initializes the database and provides application-wide dependencies.
 */
class OinkApplication : Application() {

    /**
     * The application-wide dependency graph. Built lazily on first access and
     * shared by every ViewModel factory. See [AppContainer].
     */
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * Application-lifetime scope for startup work that must outlive any single
     * Activity. A SupervisorJob keeps one failed job from cancelling the others.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Create notification channel on app start
        // This is safe to call multiple times - Android ignores duplicates
        NotificationHelper.createNotificationChannel(this)

        migratePreferencesToHabit()
    }

    /**
     * Copy per-habit settings that predate multi-habit support from DataStore
     * onto the seeded habit row, once. Runs off the main thread and is safe to
     * attempt on every launch: [PrefsToHabitMigrator] no-ops once it has run.
     */
    private fun migratePreferencesToHabit() {
        val database = AppDatabase.getDatabase(this)
        val migrator = PrefsToHabitMigrator(
            dataStore = applicationContext.dataStore,
            habitDao = database.habitDao(),
            frozenDayDao = database.frozenDayDao()
        )
        applicationScope.launch {
            migrator.migrateIfNeeded()
        }
    }
}
