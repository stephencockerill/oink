package com.oink.app.data

/**
 * Persisted brute-force limiter for the security-question recovery path.
 *
 * Unlike [PrivateGate] - whose counters live in memory and reset on every cold
 * start - this limiter is backed by DataStore. The security question is the
 * weakest recovery factor and, being purely in-app, has no OS-level throttle
 * behind it; an in-memory limiter would be trivially defeated by force-killing
 * the app between guesses. Persisting the failure count and lockout closes that
 * hole so the escalating backoff survives process death.
 *
 * The lockout deadline is stored as wall-clock epoch millis. A monotonic clock
 * ([android.os.SystemClock.elapsedRealtime]) cannot be used because it does not
 * survive a reboot, and the deadline must. The tradeoff is that moving the system
 * clock forward can end a lockout early; the real anti-brute-force property - the
 * persisted, escalating failure count - is unaffected by clock changes. Given
 * this is the rarely-hit last-resort factor, that tradeoff is acceptable.
 *
 * [nowMillis] is injected so tests drive time without waiting.
 */
class SecurityQuestionLimiter(
    private val preferencesRepository: PreferencesRepository,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * The persisted limiter counters.
     *
     * [consecutiveFailures] resets to zero on success and when a lockout opens
     * (the next window starts fresh). [lockoutCount] resets only on success, so
     * backoff keeps growing across repeated lockouts. [lockedOutUntilEpochMillis]
     * is the wall-clock instant the current lockout ends, or 0 when not locked out.
     */
    data class State(
        val consecutiveFailures: Int = 0,
        val lockoutCount: Int = 0,
        val lockedOutUntilEpochMillis: Long = 0L
    )

    /** Whether a lockout window is currently in effect. */
    suspend fun isLockedOut(): Boolean =
        nowMillis() < preferencesRepository.getSecurityQuestionLimiterState().lockedOutUntilEpochMillis

    /** Milliseconds left on the current lockout, or 0 when not locked out. */
    suspend fun remainingLockoutMillis(): Long {
        val until = preferencesRepository.getSecurityQuestionLimiterState().lockedOutUntilEpochMillis
        return (until - nowMillis()).coerceAtLeast(0L)
    }

    /**
     * Record one wrong answer. Ignored while already locked out. The
     * [MAX_ATTEMPTS]th consecutive failure opens a lockout window and resets the
     * failure count for the next window, growing the lockout with each round.
     */
    suspend fun recordFailure() {
        if (isLockedOut()) return
        val current = preferencesRepository.getSecurityQuestionLimiterState()
        val failures = current.consecutiveFailures + 1
        val next = if (failures >= MAX_ATTEMPTS) {
            val lockoutCount = current.lockoutCount + 1
            State(
                consecutiveFailures = 0,
                lockoutCount = lockoutCount,
                lockedOutUntilEpochMillis = nowMillis() + lockoutDurationMillis(lockoutCount)
            )
        } else {
            current.copy(consecutiveFailures = failures)
        }
        preferencesRepository.setSecurityQuestionLimiterState(next)
    }

    /** Record a correct answer: clear all failure history and any lockout. */
    suspend fun recordSuccess() {
        preferencesRepository.setSecurityQuestionLimiterState(State())
    }

    /**
     * Lockout length for the [lockoutCount]th lockout: [BASE_LOCKOUT_MILLIS]
     * doubling each time, capped at [MAX_LOCKOUT_MILLIS].
     */
    private fun lockoutDurationMillis(lockoutCount: Int): Long {
        val shifts = (lockoutCount - 1).coerceIn(0, MAX_BACKOFF_SHIFTS)
        return (BASE_LOCKOUT_MILLIS shl shifts).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }

    companion object {
        /** Consecutive wrong answers that trigger a lockout. */
        const val MAX_ATTEMPTS = 5

        /** First lockout length; doubles on each subsequent lockout. */
        const val BASE_LOCKOUT_MILLIS = 30_000L

        /** Ceiling on the lockout length regardless of backoff. */
        const val MAX_LOCKOUT_MILLIS = 300_000L

        /** Cap on the doubling exponent so the shift can never overflow. */
        private const val MAX_BACKOFF_SHIFTS = 4
    }
}
