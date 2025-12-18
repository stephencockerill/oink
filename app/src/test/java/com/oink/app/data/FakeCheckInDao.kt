package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Fake implementation of CheckInDao for testing.
 *
 * Following Android guidelines: "Prefer fakes to mocks"
 *
 * This in-memory implementation behaves like the real Room DAO
 * but without needing a database. It's fast, deterministic,
 * and lets us test the Repository logic in isolation.
 */
class FakeCheckInDao : CheckInDao {

    // In-memory storage
    private val checkIns = MutableStateFlow<List<CheckIn>>(emptyList())
    private var nextId = 1L

    /**
     * Reset all data - call this in @Before or between tests
     */
    fun reset() {
        checkIns.value = emptyList()
        nextId = 1L
    }

    /**
     * Seed the fake with initial data for testing
     */
    fun seed(vararg items: CheckIn) {
        checkIns.value = items.toList()
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
    }

    /**
     * Set check-ins list (alternative to seed for List input)
     */
    fun setCheckIns(items: List<CheckIn>) {
        checkIns.value = items
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
    }

    override suspend fun insert(checkIn: CheckIn): Long {
        val id = if (checkIn.id == 0L) nextId++ else checkIn.id
        val newCheckIn = checkIn.copy(id = id)

        // REPLACE behavior - remove existing with same date
        val updated = checkIns.value
            .filter { it.date != checkIn.date }
            .plus(newCheckIn)
            .sortedByDescending { it.date }

        checkIns.value = updated
        return id
    }

    override suspend fun update(checkIn: CheckIn) {
        checkIns.value = checkIns.value.map {
            if (it.id == checkIn.id) checkIn else it
        }
    }

    override fun getAllCheckInsFlow(): Flow<List<CheckIn>> {
        return checkIns.map { list -> list.sortedByDescending { it.date } }
    }

    override suspend fun getAllCheckInsAsc(): List<CheckIn> {
        return checkIns.value.sortedBy { it.date }
    }

    override suspend fun getCheckInForDate(epochDay: Long): CheckIn? {
        return checkIns.value.find { it.date.toEpochDay() == epochDay }
    }

    override suspend fun getLatestCheckIn(): CheckIn? {
        return checkIns.value.maxByOrNull { it.date }
    }

    override fun getLatestCheckInFlow(): Flow<CheckIn?> {
        return checkIns.map { list -> list.maxByOrNull { it.date } }
    }

    override fun getTodayCheckInFlow(todayEpochDay: Long): Flow<CheckIn?> {
        return checkIns.map { list ->
            list.find { it.date.toEpochDay() == todayEpochDay }
        }
    }

    override suspend fun deleteAll() {
        checkIns.value = emptyList()
    }

    override suspend fun getTotalWorkoutCount(): Int {
        return checkIns.value.count { it.didExercise }
    }

    override suspend fun getCheckInBefore(epochDay: Long): CheckIn? {
        return checkIns.value
            .filter { it.date.toEpochDay() < epochDay }
            .maxByOrNull { it.date }
    }
}

