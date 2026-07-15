package com.oink.app.utils

import com.oink.app.data.CheckIn
import java.time.LocalDate

/**
 * Pure derivations of the hero card's "recent activity" signals from a habit's
 * check-in history.
 *
 * The hero mascot is a loss-aversion feedback loop (see [Mascot.stateFor]): it
 * needs to know when the user last acted and whether their most recent action
 * grew or halved the balance. Both are computable from the check-in list alone,
 * so this stays pure and clock-free - deterministic and trivially testable.
 */
object HeroSignals {

    /**
     * The most recent check-in's date and the balance change it produced.
     *
     * @param lastCheckIn Date of the newest check-in, or null when there are none.
     * @param balanceDelta The newest check-in's `balanceAfter` minus the prior
     *   check-in's (or zero, the starting balance, when it is the first).
     *   Negative on a fresh halving, positive on a gain.
     */
    data class Recent(val lastCheckIn: LocalDate?, val balanceDelta: Long)

    /**
     * Resolve the [Recent] signals for one habit's check-ins.
     *
     * Order-independent: the list is keyed by date internally, so callers can
     * pass check-ins in any order (the DAO flows are newest-first).
     */
    fun recent(checkIns: List<CheckIn>): Recent {
        if (checkIns.isEmpty()) return Recent(lastCheckIn = null, balanceDelta = 0L)

        val sorted = checkIns.sortedBy { it.date }
        val last = sorted.last()
        val previousBalance = if (sorted.size >= 2) sorted[sorted.size - 2].balanceAfter else 0L
        return Recent(lastCheckIn = last.date, balanceDelta = last.balanceAfter - previousBalance)
    }
}
