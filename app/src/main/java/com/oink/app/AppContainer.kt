package com.oink.app

import android.content.Context
import com.oink.app.data.AppDatabase
import com.oink.app.data.CashOutRepository
import com.oink.app.data.CheckInRepository
import com.oink.app.data.DataStorePreferencesRepository
import com.oink.app.data.DefaultDeductionProvider
import com.oink.app.data.FreezeRepository
import com.oink.app.data.HabitRepository
import com.oink.app.data.HabitRewardProvider
import com.oink.app.data.PreferencesRepository
import com.oink.app.data.PrivateGate
import com.oink.app.data.RoomTransactionRunner
import com.oink.app.widget.OinkWidget
import com.oink.app.widget.WidgetUpdater

/**
 * Application-wide dependency graph for Oink.
 *
 * This is the manual-DI container: it owns one instance of the database and each
 * repository, wired together exactly once, and hands them to the ViewModel
 * factories. Repositories are habit-agnostic singletons - each of their methods
 * takes a `habitId`, so a single instance serves every habit and every screen.
 *
 * Constructing the graph here (rather than in an Activity) keeps it independent
 * of any UI lifecycle and easy to reason about: everything is a plain `val`,
 * built eagerly when the container is created.
 */
class AppContainer(context: Context) {

    /**
     * Application context, held so the widget updater can refresh Glance widgets
     * long after the constructing Activity is gone. Application (not Activity)
     * context avoids leaking a UI lifecycle.
     */
    private val appContext: Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.getDatabase(context)

    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(context)

    /**
     * In-memory unlock state and rate limiter for the private area. One shared
     * instance so the ProcessLifecycle re-lock observer and the private
     * ViewModel act on the same unlocked flag; nothing here is persisted.
     */
    val privateGate: PrivateGate = PrivateGate()

    val habitRepository: HabitRepository = HabitRepository(database.habitDao())

    val freezeRepository: FreezeRepository = FreezeRepository(
        database.habitDao(),
        database.frozenDayDao()
    )

    val checkInRepository: CheckInRepository = CheckInRepository(
        database.checkInDao(),
        HabitRewardProvider(database.habitDao()),
        DefaultDeductionProvider(
            database.cashOutDao(),
            database.cashOutAllocationDao(),
            freezeRepository
        )
    )

    val cashOutRepository: CashOutRepository = CashOutRepository(
        database.cashOutDao(),
        database.cashOutAllocationDao(),
        checkInRepository,
        habitRepository,
        freezeRepository,
        RoomTransactionRunner(database)
    )

    /**
     * Refreshes the home-screen widget after a data change. Closes over the
     * Application context so plain ViewModels can trigger a refresh without
     * holding Android context themselves.
     */
    val widgetUpdater: WidgetUpdater = WidgetUpdater { OinkWidget.updateAllWidgets(appContext) }
}
