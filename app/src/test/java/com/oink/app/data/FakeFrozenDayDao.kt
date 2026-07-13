package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake implementation of [FrozenDayDao] for testing.
 *
 * In-memory storage that mirrors the real Room DAO, including the unique
 * (habitId, date) constraint: [insert] ignores a duplicate and returns -1, so
 * freezing the same day twice is a no-op just as [androidx.room.OnConflictStrategy.IGNORE]
 * makes it in production.
 */
class FakeFrozenDayDao : FrozenDayDao {

    private val frozenDays = MutableStateFlow<List<FrozenDay>>(emptyList())
    private val idCounter = AtomicLong(0L)

    override suspend fun insert(frozenDay: FrozenDay): Long {
        val exists = frozenDays.value.any {
            it.habitId == frozenDay.habitId && it.date == frozenDay.date
        }
        if (exists) return -1L

        val id = frozenDay.id.takeIf { it != 0L } ?: idCounter.incrementAndGet()
        frozenDays.update { current -> current + frozenDay.copy(id = id) }
        return id
    }

    override suspend fun delete(frozenDay: FrozenDay) {
        frozenDays.update { current -> current.filter { it.id != frozenDay.id } }
    }

    override fun getFrozenDaysFlow(habitId: Long): Flow<List<FrozenDay>> =
        frozenDays.map { list ->
            list.filter { it.habitId == habitId }.sortedByDescending { it.date }
        }

    override suspend fun getFrozenDays(habitId: Long): List<FrozenDay> =
        frozenDays.value.filter { it.habitId == habitId }.sortedByDescending { it.date }

    override suspend fun getFrozenDay(habitId: Long, epochDay: Long): FrozenDay? =
        frozenDays.value.firstOrNull {
            it.habitId == habitId && it.date.toEpochDay() == epochDay
        }

    override suspend fun deleteByDate(habitId: Long, epochDay: Long) {
        frozenDays.update { current ->
            current.filterNot { it.habitId == habitId && it.date.toEpochDay() == epochDay }
        }
    }
}
