package com.oink.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How a single pot-level cash-out is split across habits.
 *
 * A [CashOut] stays pot-level (no habit of its own); each allocation records
 * the slice of that cash-out attributed to one habit, together with the reward
 * rate in force for that habit at the time. Summing a habit's allocations gives
 * how much of the user's spending it funded.
 *
 * At most one allocation per (cash-out, habit), enforced by the unique index.
 * Deleting either parent deletes the allocation ([ForeignKey.CASCADE]); the
 * `habitId` foreign key gets its own index since it is not the leftmost column
 * of the unique index.
 *
 * Money fields are Long minor units (cents), e.g. $5.00 is 500.
 */
@Entity(
    tableName = "cash_out_allocations",
    foreignKeys = [
        ForeignKey(
            entity = CashOut::class,
            parentColumns = ["id"],
            childColumns = ["cashOutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cashOutId", "habitId"], unique = true),
        Index(value = ["habitId"])
    ]
)
data class CashOutAllocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The pot-level cash-out this allocation belongs to.
     */
    val cashOutId: Long,

    /**
     * The habit this slice of the cash-out is attributed to.
     */
    val habitId: Long,

    /**
     * The slice of the cash-out attributed to the habit, in cents.
     */
    val amount: Long,

    /**
     * The habit's per-day reward in force when this allocation was recorded,
     * in cents. Captured so "workouts earned" stays accurate even after the
     * habit's reward setting changes.
     */
    val exerciseRewardAtTime: Long
)
