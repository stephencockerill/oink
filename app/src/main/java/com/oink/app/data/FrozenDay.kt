package com.oink.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A single day a habit's streak was frozen (protected from the miss penalty).
 *
 * A frozen day belongs to exactly one habit. Deleting a habit deletes its
 * frozen days ([ForeignKey.CASCADE]) - the child record has no meaning without
 * its parent habit.
 *
 * At most one frozen day per (habit, date), enforced by the unique index. That
 * index is also keyed on `habitId` first, which satisfies Room's requirement
 * that every foreign-key column be indexed.
 *
 * Dates are stored as epoch day (Long) via [Converters].
 */
@Entity(
    tableName = "frozen_days",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true)
    ]
)
data class FrozenDay(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The habit whose streak this day protects.
     */
    val habitId: Long,

    /**
     * The frozen date.
     */
    val date: LocalDate
)
