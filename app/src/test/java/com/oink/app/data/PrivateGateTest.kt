package com.oink.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PrivateGate] on plain JVM: the time source is injected, so no
 * real waiting and no Android clock.
 *
 * Focus:
 * - unlock/lock toggle the flag.
 * - The rate limiter locks out after [PrivateGate.MAX_ATTEMPTS] failures, blocks
 *   until the clock passes the window, resets on success, and is NOT reset by
 *   backgrounding ([PrivateGate.lock]).
 */
class PrivateGateTest {

    private var now = 0L
    private val gate = PrivateGate(elapsedRealtime = { now })

    @Test
    fun `unlock and lock toggle the unlocked flag`() {
        assertFalse(gate.isUnlocked.value)

        gate.unlock()
        assertTrue(gate.isUnlocked.value)

        gate.lock()
        assertFalse(gate.isUnlocked.value)
    }

    @Test
    fun `max consecutive failures triggers a lockout`() {
        repeat(PrivateGate.MAX_ATTEMPTS - 1) { gate.recordFailure() }
        assertFalse("Not locked out before the threshold", gate.isLockedOut())
        assertEquals(1, gate.remainingAttempts())

        gate.recordFailure() // the threshold-th failure
        assertTrue("Locked out on the threshold failure", gate.isLockedOut())
        assertEquals(0, gate.remainingAttempts())
    }

    @Test
    fun `lockout blocks until the clock passes the window then clears`() {
        repeat(PrivateGate.MAX_ATTEMPTS) { gate.recordFailure() }
        assertTrue(gate.isLockedOut())

        // Just before the window ends, still locked out.
        now += PrivateGate.BASE_LOCKOUT_MILLIS - 1
        assertTrue(gate.isLockedOut())

        // Past the window, the lockout clears and attempts are available again.
        now += 1
        assertFalse(gate.isLockedOut())
        assertEquals(PrivateGate.MAX_ATTEMPTS, gate.remainingAttempts())
    }

    @Test
    fun `backoff lengthens each successive lockout`() {
        // First lockout: base window.
        repeat(PrivateGate.MAX_ATTEMPTS) { gate.recordFailure() }
        now += PrivateGate.BASE_LOCKOUT_MILLIS
        assertFalse(gate.isLockedOut())

        // Second lockout must be longer than the base window (backoff).
        repeat(PrivateGate.MAX_ATTEMPTS) { gate.recordFailure() }
        now += PrivateGate.BASE_LOCKOUT_MILLIS
        assertTrue("Second lockout is longer than the base window", gate.isLockedOut())
    }

    @Test
    fun `recording a failure while locked out is ignored`() {
        repeat(PrivateGate.MAX_ATTEMPTS) { gate.recordFailure() }
        val lockedUntil = gate.attemptState.value.lockedOutUntilElapsedMs

        // Extra failures during the lockout must not extend or reset it.
        gate.recordFailure()
        gate.recordFailure()
        assertEquals(lockedUntil, gate.attemptState.value.lockedOutUntilElapsedMs)
    }

    @Test
    fun `success resets the counter and any lockout`() {
        repeat(PrivateGate.MAX_ATTEMPTS - 1) { gate.recordFailure() }
        assertEquals(1, gate.remainingAttempts())

        gate.recordSuccess()
        assertEquals(PrivateGate.MAX_ATTEMPTS, gate.remainingAttempts())
        assertFalse(gate.isLockedOut())
        assertEquals(0, gate.attemptState.value.consecutiveFailures)
        assertEquals(0, gate.attemptState.value.lockoutCount)
    }

    @Test
    fun `lock does not reset the rate-limit counter`() {
        repeat(PrivateGate.MAX_ATTEMPTS - 1) { gate.recordFailure() }
        val failuresBefore = gate.attemptState.value.consecutiveFailures

        // Backgrounding re-locks but must not shed accumulated failures.
        gate.lock()

        assertEquals(failuresBefore, gate.attemptState.value.consecutiveFailures)
        assertEquals(1, gate.remainingAttempts())

        // One more failure still trips the lockout, proving the count carried over.
        gate.recordFailure()
        assertTrue(gate.isLockedOut())
    }
}
