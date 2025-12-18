package com.oink.app.data

/**
 * Interface for providing the exercise reward amount.
 *
 * This exists to make CheckInRepository testable without needing
 * to mock the entire PreferencesRepository. Following Android guidelines:
 * "Reduce dependencies on Android classes" and "Prefer fakes to mocks"
 *
 * In production: PreferencesRepository implements this
 * In tests: A simple fake implements this
 */
fun interface ExerciseRewardProvider {
    suspend fun getExerciseReward(): Double
}

