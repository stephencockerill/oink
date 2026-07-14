package com.oink.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A habit the user is building - the parent of check-ins, frozen days, and
 * cash-out allocations.
 *
 * Multi-habit support keys every child record to a habit. The single default
 * habit (id = 1, "Workout") owns all data created before habits existed, so
 * legacy rows migrate cleanly under it.
 *
 * Money fields are Long minor units (cents), never floating point, e.g. $5.00
 * is 500.
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Display name, e.g. "Workout", "Meditate", "Read".
     */
    val name: String,

    /**
     * Emoji shown alongside the habit.
     */
    val emoji: String = "⭐",

    /**
     * Reward earned per successful day for this habit, in cents.
     */
    val rewardValue: Long = 500L,

    /**
     * Streak freezes currently banked for this habit.
     */
    val availableFreezes: Int = 0,

    /**
     * Running total spent buying freezes for this habit, in cents.
     */
    val totalFreezeSpending: Long = 0L,

    /**
     * Whether this habit is hidden from shared or public surfaces.
     */
    val isPrivate: Boolean = false,

    /**
     * Whether this is a build habit (start a behavior) or a quit habit (stop
     * one). See [HabitType]. Stored as its name string; the column defaults to
     * "BUILD" at the SQL level so every habit created before quit habits existed
     * reads as a build habit.
     */
    @ColumnInfo(defaultValue = "BUILD")
    val habitType: HabitType = HabitType.BUILD,

    /**
     * Manual ordering position for display; lower sorts first.
     */
    val sortOrder: Int = 0,

    /**
     * When the habit was created (epoch millis).
     */
    val createdAt: Long = System.currentTimeMillis()
)
