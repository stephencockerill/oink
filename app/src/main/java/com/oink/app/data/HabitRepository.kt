package com.oink.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository owning habit records - the parent of check-ins, frozen days, and
 * cash-out allocations.
 *
 * The single default habit (id = [DEFAULT_HABIT_ID], "Workout") owns all data
 * that predates multi-habit support.
 */
class HabitRepository(
    private val habitDao: HabitDao
) {

    /**
     * All habits in display order.
     */
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabitsFlow()

    /**
     * Observe a single habit by ID.
     */
    fun habit(id: Long): Flow<Habit?> = habitDao.getHabitFlow(id)

    /**
     * Get a habit by ID, or null if none exists.
     */
    suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)

    /**
     * Get all habits in display order.
     */
    suspend fun getAllHabits(): List<Habit> = habitDao.getAllHabits()

    /**
     * Add a habit. Returns the new habit's ID.
     */
    suspend fun addHabit(habit: Habit): Long = habitDao.insert(habit)

    /**
     * Update an existing habit.
     */
    suspend fun updateHabit(habit: Habit) = habitDao.update(habit)

    /**
     * Delete a habit and everything keyed to it.
     */
    suspend fun deleteHabit(habit: Habit) = habitDao.delete(habit)

    companion object {
        /**
         * The default habit seeded by the v3 -> v4 migration; owns all legacy
         * check-ins and cash-out allocations.
         */
        const val DEFAULT_HABIT_ID = 1L
    }
}
