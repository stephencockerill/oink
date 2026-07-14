package com.oink.app.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block of suspend work as one atomic unit.
 *
 * [CashOutRepository] writes a cash-out and its allocations together; wrapping
 * them in a single transaction keeps the two in step even if the process dies
 * mid-write. The abstraction lets tests supply an implementation that just runs
 * the block, so repository logic is run without a real database.
 *
 * This is a plain interface rather than a `fun interface`: Kotlin forbids a
 * functional interface whose abstract method carries its own type parameter
 * (the `<R>` here), and every consumer constructs an explicit implementation
 * rather than a SAM lambda, so nothing is lost.
 */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

/**
 * Production [TransactionRunner] backed by Room. Delegates to
 * [RoomDatabase.withTransaction], so the block runs on Room's transaction
 * executor and commits or rolls back as one unit.
 */
class RoomTransactionRunner(
    private val database: RoomDatabase
) : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R =
        database.withTransaction { block() }
}
