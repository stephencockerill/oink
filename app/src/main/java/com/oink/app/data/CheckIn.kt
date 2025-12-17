package com.oink.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

/**
 * Entity representing a daily check-in record.
 *
 * Each check-in tracks whether the user exercised on a given date
 * and what their balance was after the check-in was recorded.
 *
 * The date column is indexed for fast lookups since we frequently
 * query by date (today's check-in, check-ins before/after a date, etc.).
 * It's also unique to prevent duplicate check-ins for the same day.
 */
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["date"], unique = true)]
)
@TypeConverters(Converters::class)
data class CheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The date of this check-in.
     * Only one check-in is allowed per date.
     */
    val date: LocalDate,

    /**
     * Whether the user exercised on this date.
     * true = exercised (+$5.00)
     * false = missed (balance / 2)
     */
    val didExercise: Boolean,

    /**
     * The user's balance AFTER this check-in was recorded.
     * This allows us to reconstruct the balance history without
     * having to recalculate from the beginning every time.
     */
    val balanceAfter: Double
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

