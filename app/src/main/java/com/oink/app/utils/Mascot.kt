package com.oink.app.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The emotional state of the Oink pig mascot.
 *
 * Each state maps to a distinct piece of vector art and carries a specific
 * behavioral-economics intent (see [Mascot.stateFor]).
 */
enum class MascotState { HAPPY, COMEBACK, SLEEPING, NEUTRAL }

/**
 * Pure mapping from the user's recent progress to the mascot's emotional state.
 *
 * The mascot is a loss-aversion feedback loop made visible: it celebrates momentum,
 * softens (but never shames) a setback, and quietly waits when the user drifts away.
 * Keeping this logic pure and clock-free makes it deterministic and trivially testable -
 * `today` is passed in rather than read from a hidden system clock.
 */
object Mascot {

    /**
     * Resolve the mascot state for the current hero/Rewards context.
     *
     * Precedence is deliberate and exhaustive:
     * 1. No check-in ever -> [MascotState.SLEEPING]. Nothing has happened yet; the pig is idle,
     *    not sad. First impression should feel calm and inviting, not judgmental.
     * 2. Gone quiet (more than one day since the last check-in) -> [MascotState.SLEEPING].
     *    The habit loop has lapsed; a dozing pig invites a gentle return rather than guilt.
     * 3. Recent loss/halving (`balanceDelta < 0`) -> [MascotState.COMEBACK]. Loss aversion stings,
     *    so the pig stays hopeful and supportive to pull the user back instead of punishing them.
     *    This takes precedence over HAPPY so a streak can't paper over a fresh loss.
     * 4. Growing on a streak (`balanceDelta > 0` and `currentStreak >= 2`) -> [MascotState.HAPPY].
     *    Momentum earns visible celebration, reinforcing the winning behavior.
     * 5. Otherwise -> [MascotState.NEUTRAL]. A calm baseline for the in-between (flat delta,
     *    a single day, or gains without an established streak).
     *
     * @param balanceDelta Recent change in spendable balance, in cents (negative = loss/halving).
     * @param currentStreak Current consecutive-day streak length.
     * @param lastCheckIn Date of the user's most recent check-in, or null if they never have.
     * @param today The date to evaluate "quiet" against; injected so there is no hidden clock.
     */
    fun stateFor(
        balanceDelta: Long,
        currentStreak: Int,
        lastCheckIn: LocalDate?,
        today: LocalDate
    ): MascotState {
        if (lastCheckIn == null) return MascotState.SLEEPING
        if (ChronoUnit.DAYS.between(lastCheckIn, today) > 1) return MascotState.SLEEPING
        if (balanceDelta < 0) return MascotState.COMEBACK
        if (balanceDelta > 0 && currentStreak >= 2) return MascotState.HAPPY
        return MascotState.NEUTRAL
    }
}
