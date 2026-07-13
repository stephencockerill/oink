package com.oink.app.data

/**
 * Fake [TransactionRunner] for tests: runs the block directly, with no real
 * transaction. The in-memory fakes have no rollback, so repository logic that
 * validates before mutating stays correct here just as it does in production.
 */
class FakeTransactionRunner : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = block()
}
