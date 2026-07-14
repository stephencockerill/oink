package com.oink.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SecurityQuestionLimiter].
 *
 * The behaviour mirrors [PrivateGate]'s escalating backoff, but the distinguishing
 * property is persistence: the counters live in the [PreferencesRepository], so a
 * fresh limiter instance over the same store still sees accumulated failures. That
 * is what a force-kill-and-retry attacker cannot reset.
 */
class SecurityQuestionLimiterTest {

    private lateinit var repository: FakePreferencesRepository
    private var now = 0L

    private fun limiter() = SecurityQuestionLimiter(repository, nowMillis = { now })

    @Before
    fun setup() {
        repository = FakePreferencesRepository()
    }

    @Test
    fun `locks out after the max attempts`() = runTest {
        val limiter = limiter()
        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS) { limiter.recordFailure() }

        assertTrue(limiter.isLockedOut())
        assertEquals(SecurityQuestionLimiter.BASE_LOCKOUT_MILLIS, limiter.remainingLockoutMillis())
    }

    @Test
    fun `lockout expires once the clock advances past the window`() = runTest {
        val limiter = limiter()
        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS) { limiter.recordFailure() }
        assertTrue(limiter.isLockedOut())

        now += SecurityQuestionLimiter.BASE_LOCKOUT_MILLIS
        assertFalse(limiter.isLockedOut())
    }

    @Test
    fun `backoff grows across successive lockouts`() = runTest {
        val limiter = limiter()

        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS) { limiter.recordFailure() }
        val first = limiter.remainingLockoutMillis()

        now += first
        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS) { limiter.recordFailure() }
        val second = limiter.remainingLockoutMillis()

        assertTrue("Second lockout must be longer than the first", second > first)
    }

    @Test
    fun `failure count persists across limiter instances`() = runTest {
        // One instance accumulates failures, then goes away (process death).
        val first = limiter()
        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS - 1) { first.recordFailure() }
        assertFalse(first.isLockedOut())

        // A brand-new instance over the same store sees the accumulated count:
        // one more failure tips it into lockout rather than restarting from zero.
        val second = limiter()
        second.recordFailure()
        assertTrue("Persisted failures must survive a new limiter instance", second.isLockedOut())
    }

    @Test
    fun `success clears the persisted counters`() = runTest {
        val limiter = limiter()
        repeat(SecurityQuestionLimiter.MAX_ATTEMPTS) { limiter.recordFailure() }
        assertTrue(limiter.isLockedOut())

        now += limiter.remainingLockoutMillis()
        limiter.recordSuccess()

        val state = repository.getSecurityQuestionLimiterState()
        assertEquals(SecurityQuestionLimiter.State(), state)
    }
}
