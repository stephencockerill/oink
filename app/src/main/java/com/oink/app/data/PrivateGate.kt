package com.oink.app.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped holder of the private-area unlock state and its brute-force rate
 * limiter. A single instance lives on the dependency graph
 * ([com.oink.app.AppContainer]) and is shared by the ProcessLifecycle re-lock
 * observer and the private ViewModel, so both see the same unlocked flag and the
 * same attempt history.
 *
 * Nothing here is persisted. The unlocked flag is intentionally ephemeral: it
 * starts locked on every cold start and is cleared whenever the app goes to the
 * background, so there is no on-disk "unlocked" state to recover or tamper with.
 *
 * The rate limiter is also in memory. After [MAX_ATTEMPTS] consecutive failures
 * it imposes a lockout whose length backs off with each successive lockout, and
 * exposes enough to drive the UI ("try again in Xs", remaining attempts).
 *
 * Time is read through [elapsedRealtime] - a monotonic clock, so it cannot be
 * rewound by changing the wall clock - and is injected so tests drive it
 * without real waiting.
 */
class PrivateGate(
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
) {

    /**
     * The rate-limiter's persistent-across-attempts counters.
     *
     * [consecutiveFailures] resets to zero both on success and when a lockout is
     * imposed (the next window starts fresh). [lockoutCount] only resets on
     * success, so backoff keeps growing across repeated lockouts.
     * [lockedOutUntilElapsedMs] is the monotonic instant the current lockout
     * ends, or null when not locked out. This value re-emits on every failure or
     * lockout; callers derive the live "still locked out?" answer from the
     * injected clock, since time passing does not itself emit.
     */
    data class AttemptState(
        val consecutiveFailures: Int = 0,
        val lockoutCount: Int = 0,
        val lockedOutUntilElapsedMs: Long? = null
    )

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _attemptState = MutableStateFlow(AttemptState())
    val attemptState: StateFlow<AttemptState> = _attemptState.asStateFlow()

    /**
     * Mark the private area unlocked. Callers reset the limiter via
     * [recordSuccess] before this on a correct PIN.
     */
    fun unlock() {
        _isUnlocked.value = true
    }

    /**
     * Re-lock the private area.
     *
     * Clears only the unlocked flag; the rate-limiter counters are left intact
     * on purpose, so sending the app to the background (which calls this) cannot
     * be used to shed accumulated failures and bypass the lockout.
     */
    fun lock() {
        _isUnlocked.value = false
    }

    /**
     * Whether a lockout window is currently in effect.
     */
    fun isLockedOut(): Boolean {
        val until = _attemptState.value.lockedOutUntilElapsedMs ?: return false
        return elapsedRealtime() < until
    }

    /**
     * Milliseconds left on the current lockout, or 0 when not locked out.
     */
    fun remainingLockoutMillis(): Long {
        val until = _attemptState.value.lockedOutUntilElapsedMs ?: return 0L
        return (until - elapsedRealtime()).coerceAtLeast(0L)
    }

    /**
     * PIN attempts remaining before the next lockout. Zero while locked out.
     */
    fun remainingAttempts(): Int {
        if (isLockedOut()) return 0
        return (MAX_ATTEMPTS - _attemptState.value.consecutiveFailures).coerceAtLeast(0)
    }

    /**
     * Record one wrong PIN. Ignored while already locked out. The
     * [MAX_ATTEMPTS]th consecutive failure opens a lockout window and resets the
     * failure count for the next window, growing the lockout with each round.
     */
    fun recordFailure() {
        if (isLockedOut()) return
        val current = _attemptState.value
        val failures = current.consecutiveFailures + 1
        _attemptState.value = if (failures >= MAX_ATTEMPTS) {
            val lockoutCount = current.lockoutCount + 1
            current.copy(
                consecutiveFailures = 0,
                lockoutCount = lockoutCount,
                lockedOutUntilElapsedMs = elapsedRealtime() + lockoutDurationMillis(lockoutCount)
            )
        } else {
            current.copy(consecutiveFailures = failures)
        }
    }

    /**
     * Record a correct PIN: clear all failure history and any lockout, so the
     * limiter is back to a clean slate.
     */
    fun recordSuccess() {
        _attemptState.value = AttemptState()
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
        /** Consecutive wrong PINs that trigger a lockout. */
        const val MAX_ATTEMPTS = 5

        /** First lockout length; doubles on each subsequent lockout. */
        const val BASE_LOCKOUT_MILLIS = 30_000L

        /** Ceiling on the lockout length regardless of backoff. */
        const val MAX_LOCKOUT_MILLIS = 300_000L

        /** Cap on the doubling exponent so the shift can never overflow. */
        private const val MAX_BACKOFF_SHIFTS = 4
    }
}
