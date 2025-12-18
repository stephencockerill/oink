package com.oink.app.data

/**
 * Interface for preferences needed by CashOutRepository.
 *
 * This exists to make CashOutRepository testable without needing
 * a real PreferencesRepository (which requires Android Context).
 *
 * In production: PreferencesRepository implements this
 * In tests: FakePreferencesRepository implements this
 */
interface CashOutPreferencesProvider : ExerciseRewardProvider {
    suspend fun getTotalFreezeSpending(): Double
    suspend fun addFreezeSpending(amount: Double)
}

