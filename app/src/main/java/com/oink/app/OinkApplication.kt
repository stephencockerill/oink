package com.oink.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.oink.app.data.AppDatabase
import com.oink.app.data.PrefsToHabitMigrator
import com.oink.app.data.dataStore
import com.oink.app.notifications.DayCloseScheduler
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
        relockPrivateAreaWhenBackgrounded()
        scheduleDayCloseResolve()
    }

    /**
     * Arm the day-close auto-resolve worker for the next local midnight.
     *
     * Enqueued unconditionally on every launch (unlike the reminder, which the
     * user gates in settings): quit habits accrue money by materializing passive
     * clean days at day-close, so the chain must be live for every user with any
     * quit habit, whether or not reminders are on. It is unique work, so relaunch
     * never stacks duplicates, and [com.oink.app.notifications.DayCloseWorker]
     * re-arms it after each run. See [DayCloseScheduler].
     */
    private fun scheduleDayCloseResolve() {
        DayCloseScheduler.scheduleNextMidnight(this)
    }

    /**
     * Re-lock the private area whenever the whole app goes to the background.
     *
     * [ProcessLifecycleOwner] reports the lifecycle of the app as a whole, so
     * onStop fires once the last visible Activity stops - i.e. the app is
     * backgrounded, not merely a config-change or Activity hop. Clearing the
     * unlock there means returning to the app always re-presents the PIN gate.
     */
    private fun relockPrivateAreaWhenBackgrounded() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.privateGate.lock()
            }
        })
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
