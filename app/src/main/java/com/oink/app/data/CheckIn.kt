package com.oink.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Entity representing a daily check-in record.
 *
 * Each check-in tracks whether the user exercised on a given date
 * and what their balance was after the check-in was recorded.
 *
 * A check-in belongs to one habit. At most one check-in per (habit, date),
 * enforced by the unique index on `(habitId, date)`. That index is keyed on
 * `habitId` first, which both powers per-habit date lookups and satisfies
 * Room's requirement that every foreign-key column be indexed. Deleting a
 * habit deletes its check-ins ([ForeignKey.CASCADE]).
 */
@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The date of this check-in.
     * Only one check-in is allowed per date within a habit.
     */
    val date: LocalDate,

    /**
     * Whether the user exercised on this date.
     * true = exercised (+$5.00)
     * false = missed (balance / 2)
     */
    val didExercise: Boolean,

    /**
     * The user's balance AFTER this check-in was recorded, in cents.
     * This allows us to reconstruct the balance history without
     * having to recalculate from the beginning every time.
     *
     * Money is stored as Long minor units (cents) so it never lives in
     * binary floating point, e.g. $5.00 is 500.
     */
    val balanceAfter: Long,

    /**
     * The exercise reward in force when this check-in was recorded, in cents.
     *
     * We store it per check-in so that recalculating historical balances uses
     * the reward that actually applied on each day, not whatever the user has
     * their reward set to today. Without this, changing the reward setting and
     * then editing a past day would silently rewrite history with the new rate.
     * This mirrors [CashOut.exerciseRewardAtTime].
     *
     * Money is stored as Long minor units (cents), e.g. $5.00 is 500.
     */
    val exerciseRewardAtTime: Long = 500L,

    /**
     * The habit this check-in belongs to.
     *
     * The column defaults to the single default habit (id = 1) at the SQL
     * level so historical rows created before habits existed all attribute to
     * it. Callers that do not yet distinguish habits inherit the same default.
     */
    @ColumnInfo(defaultValue = "1")
    val habitId: Long = 1L
)

/**
 * Type converters for Room to handle LocalDate serialization.
 * We store dates as epoch day (Long) in the database.
 */
class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? {
        return epochDay?.let { LocalDate.ofEpochDay(it) }
    }
}

