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
    /** Total amount spent on freezes, in cents. */
    suspend fun getTotalFreezeSpending(): Long
    /** Add to the total freeze spending, in cents. */
    suspend fun addFreezeSpending(amount: Long)
}

