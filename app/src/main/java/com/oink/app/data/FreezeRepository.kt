package com.oink.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Repository owning a habit's streak-freeze state.
 *
 * Freezes are per-habit: the banked count and running spend live on the
 * [Habit] row ([Habit.availableFreezes] / [Habit.totalFreezeSpending]) and the
 * days a habit's streak was frozen live in the `frozen_days` table. Every method
 * is keyed by `habitId`, so a freeze bought, used, or paid for on one habit never
 * touches another.
 *
 * This replaces the freeze state that used to be stored as DataStore singletons,
 * which could only ever describe a single habit.
 */
class FreezeRepository(
    private val habitDao: HabitDao,
    private val frozenDayDao: FrozenDayDao
) {

    /**
     * Observe the freezes banked for a habit. Emits 0 when the habit is absent.
     */
    fun availableFreezes(habitId: Long): Flow<Int> =
        habitDao.getHabitFlow(habitId).map { it?.availableFreezes ?: 0 }

    /**
     * Freezes currently banked for a habit (one-shot). 0 when the habit is absent.
     */
    suspend fun getAvailableFreezes(habitId: Long): Int =
        habitDao.getById(habitId)?.availableFreezes ?: 0

    /**
     * Observe the days a habit's streak is frozen.
     */
    fun frozenDates(habitId: Long): Flow<Set<LocalDate>> =
        frozenDayDao.getFrozenDaysFlow(habitId).map { days -> days.mapTo(mutableSetOf()) { it.date } }

    /**
     * Days a habit's streak is frozen (one-shot).
     */
    suspend fun getFrozenDates(habitId: Long): Set<LocalDate> =
        frozenDayDao.getFrozenDays(habitId).mapTo(mutableSetOf()) { it.date }

    /**
     * Whether [date] is frozen for a habit.
     */
    suspend fun isDateFrozen(habitId: Long, date: LocalDate): Boolean =
        frozenDayDao.getFrozenDay(habitId, date.toEpochDay()) != null

    /**
     * Bank a streak freeze for a habit. Returns true on success, false when the
     * habit is already at [PreferencesRepository.MAX_FREEZES] or does not exist.
     */
    suspend fun purchaseFreeze(habitId: Long): Boolean {
        val habit = habitDao.getById(habitId) ?: return false
        if (habit.availableFreezes >= PreferencesRepository.MAX_FREEZES) {
            return false
        }
        habitDao.update(habit.copy(availableFreezes = habit.availableFreezes + 1))
        return true
    }

    /**
     * Use a banked freeze to protect [date] for a habit: decrement the banked
     * count and record the frozen day. Returns true on success, false when the
     * habit has no freezes banked or does not exist.
     *
     * The frozen-day insert ignores an existing (habit, date) row, so freezing a
     * day that is already frozen adds no duplicate.
     */
    suspend fun useFreeze(habitId: Long, date: LocalDate): Boolean {
        val habit = habitDao.getById(habitId) ?: return false
        if (habit.availableFreezes <= 0) {
            return false
        }
        habitDao.update(habit.copy(availableFreezes = habit.availableFreezes - 1))
        frozenDayDao.insert(FrozenDay(habitId = habitId, date = date))
        return true
    }

    /**
     * Observe the running total spent using freezes for a habit, in cents.
     */
    fun totalFreezeSpending(habitId: Long): Flow<Long> =
        habitDao.getHabitFlow(habitId).map { it?.totalFreezeSpending ?: 0L }

    /**
     * Running total spent using freezes for a habit, in cents (one-shot).
     */
    suspend fun getTotalFreezeSpending(habitId: Long): Long =
        habitDao.getById(habitId)?.totalFreezeSpending ?: 0L

    /**
     * Add [amount] cents to a habit's running freeze spend. Called when a freeze
     * is used (not when it is banked). No-op when the habit does not exist.
     */
    suspend fun addFreezeSpending(habitId: Long, amount: Long) {
        val habit = habitDao.getById(habitId) ?: return
        habitDao.update(habit.copy(totalFreezeSpending = habit.totalFreezeSpending + amount))
    }
}
