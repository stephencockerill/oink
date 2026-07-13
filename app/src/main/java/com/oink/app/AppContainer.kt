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
import com.oink.app.data.RoomTransactionRunner

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

    private val database: AppDatabase = AppDatabase.getDatabase(context)

    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(context)

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
}
