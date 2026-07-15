package com.oink.app.utils

import androidx.compose.runtime.Immutable
import com.oink.app.data.CashOut

/**
 * A single entry in the Rewards highlight-reel timeline: either an earned
 * milestone trophy or a cash-out the user claimed.
 *
 * A closed hierarchy so the timeline composable renders each kind exhaustively
 * with no `else` catch-all; both variants are [Immutable] and hold only stable
 * values, so a list of them is safe for Compose skipping.
 */
sealed interface RewardTimelineEntry {
    /** An earned ([MilestoneTierStatus.DONE]) milestone tier, shown as a trophy marker. */
    @Immutable
    data class Trophy(val tier: MilestoneTier) : RewardTimelineEntry

    /** A cash-out the user claimed, shown with the reward emoji they chose. */
    @Immutable
    data class Cashout(val cashOut: CashOut) : RewardTimelineEntry
}

/**
 * Pure derivation of the Rewards timeline from the milestone track and the
 * cash-out history.
 *
 * The reel leads with earned trophies (highest tier first, the proudest
 * achievement up top) and then the claimed rewards in the order the caller
 * supplies them (the DAO flows are already newest-first). It does not attempt to
 * reconstruct the exact earnings moment each tier was crossed - that is not
 * derivable from cash-outs alone - so trophies are grouped as achievement markers
 * rather than interleaved by timestamp.
 *
 * Pure and clock-free, so it is trivially unit-tested.
 */
object RewardTimeline {

    /**
     * Build the timeline entries.
     *
     * @param track The milestone track from [Milestone.track]; only [MilestoneTierStatus.DONE]
     *   tiers become trophy markers.
     * @param cashOuts The visible cash-out history, already ordered newest-first.
     */
    fun build(
        track: List<MilestoneTier>,
        cashOuts: List<CashOut>
    ): List<RewardTimelineEntry> {
        val trophies = track
            .filter { it.status == MilestoneTierStatus.DONE }
            .sortedByDescending { it.thresholdCents }
            .map { RewardTimelineEntry.Trophy(it) }
        val claims = cashOuts.map { RewardTimelineEntry.Cashout(it) }
        return trophies + claims
    }
}
