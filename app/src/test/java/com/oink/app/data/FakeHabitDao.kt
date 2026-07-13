package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake implementation of [HabitDao] for testing.
 *
 * In-memory storage that behaves like the real Room DAO. The backing
 * [MutableStateFlow] means [getHabitFlow]/[getAllHabitsFlow] re-emit on every
 * write, matching Room's observable queries.
 */
class FakeHabitDao : HabitDao {

    private val habits = MutableStateFlow<List<Habit>>(emptyList())
    private val idCounter = AtomicLong(0L)

    override suspend fun insert(habit: Habit): Long {
        val id = habit.id.takeIf { it != 0L } ?: idCounter.incrementAndGet()
        idCounter.updateAndGetMax(id)
        habits.update { current -> current + habit.copy(id = id) }
        return id
    }

    override suspend fun update(habit: Habit) {
        habits.update { current ->
            current.map { if (it.id == habit.id) habit else it }
        }
    }

    override suspend fun delete(habit: Habit) {
        habits.update { current -> current.filter { it.id != habit.id } }
    }

    override suspend fun getById(id: Long): Habit? =
        habits.value.find { it.id == id }

    override suspend fun applyMigratedPreferences(
        id: Long,
        rewardValue: Long,
        availableFreezes: Int,
        totalFreezeSpending: Long
    ): Int {
        var updated = 0
        habits.update { current ->
            current.map { habit ->
                if (habit.id == id) {
                    updated = 1
                    habit.copy(
                        rewardValue = rewardValue,
                        availableFreezes = availableFreezes,
                        totalFreezeSpending = totalFreezeSpending
                    )
                } else {
                    habit
                }
            }
        }
        return updated
    }

    override fun getAllHabitsFlow(): Flow<List<Habit>> =
        habits.map { list -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

    override suspend fun getAllHabits(): List<Habit> =
        habits.value.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun getHabitFlow(id: Long): Flow<Habit?> =
        habits.map { list -> list.find { it.id == id } }

    // ============================================================
    // Test Helpers
    // ============================================================

    /**
     * Seed the fake with initial habits for testing.
     */
    fun seed(vararg items: Habit) {
        habits.value = items.toList()
        items.forEach { idCounter.updateAndGetMax(it.id) }
    }

    private fun AtomicLong.updateAndGetMax(candidate: Long) {
        updateAndGet { existing -> maxOf(existing, candidate) }
    }
}
