package com.oink.app.utils

import androidx.compose.runtime.Immutable

/**
 * The financial milestone tiers and the pure logic that resolves a balance to
 * its progress toward the next one.
 *
 * The tiers come straight from the product's financial milestone system
 * (docs/habit-psychology.md): sub-goals that create variety and keep progress
 * visible so the balance always has a "next thing to chase". Progress fills from
 * the previous tier's threshold to the next one, not from zero, so early progress
 * inside a tier reads as real movement instead of a nearly-empty bar.
 *
 * Pure and money-safe: every threshold is Long cents, and [resolve] does no I/O
 * so it is trivially unit-tested at its boundaries.
 */
object Milestone {

    /**
     * One financial tier: the balance (in cents) that unlocks it and its name.
     */
    @Immutable
    data class Tier(val thresholdCents: Long, val name: String)

    /**
     * The four financial tiers, ascending. Kept private and immutable so callers
     * resolve through [resolve] rather than depending on the raw list.
     */
    private val tiers: List<Tier> = listOf(
        Tier(2_500L, "Quarter Pounder"),
        Tier(5_000L, "Half-Swole"),
        Tier(10_000L, "Century Club"),
        Tier(20_000L, "Swole Savings")
    )

    /**
     * The number of tiers a balance has cleared - its milestone "rank".
     *
     * `0` below the first tier, rising by one at each threshold reached, up to
     * [tiers]`.size` when every tier is cleared. Pure and money-safe (Long cents,
     * negative treated as zero) so a UI can detect a tier crossing by comparing
     * the rank before and after a balance change without re-deriving thresholds.
     *
     * @param balanceCents Current balance in cents (negative is treated as zero).
     */
    fun rankFor(balanceCents: Long): Int {
        val balance = balanceCents.coerceAtLeast(0L)
        return tiers.count { balance >= it.thresholdCents }
    }

    /**
     * Resolve a balance to its milestone progress.
     *
     * @param balanceCents Current balance in cents (negative is treated as zero).
     */
    fun resolve(balanceCents: Long): MilestoneProgress {
        val balance = balanceCents.coerceAtLeast(0L)

        val currentTier = tiers.lastOrNull { balance >= it.thresholdCents }
        val nextTier = tiers.firstOrNull { balance < it.thresholdCents }
        val prevThreshold = currentTier?.thresholdCents ?: 0L

        // All tiers cleared: show the top tier as achieved with a full, maxed bar
        // and no next arrow.
        if (nextTier == null) {
            return MilestoneProgress(
                currentTierName = currentTier?.name,
                nextThresholdCents = null,
                nextTierName = null,
                prevThresholdCents = prevThreshold,
                progress = 1f
            )
        }

        val span = nextTier.thresholdCents - prevThreshold
        val progress = if (span <= 0L) {
            0f
        } else {
            ((balance - prevThreshold).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }

        return MilestoneProgress(
            currentTierName = currentTier?.name,
            nextThresholdCents = nextTier.thresholdCents,
            nextTierName = nextTier.name,
            prevThresholdCents = prevThreshold,
            progress = progress
        )
    }

    /**
     * The full milestone track, each tier tagged with its [MilestoneTierStatus],
     * derived from cumulative lifetime earnings rather than spendable balance.
     *
     * Using lifetime earnings (balance + everything already cashed out) means a
     * trophy stays earned after the user spends the money - achievements don't
     * un-happen when the pot shrinks. Exactly one tier is [MilestoneTierStatus.ACTIVE]
     * (the first not-yet-reached one, the goal being chased); every lower tier is
     * [MilestoneTierStatus.DONE] and every higher tier is [MilestoneTierStatus.LOCKED].
     * When every tier is reached, none is ACTIVE.
     *
     * Pure and money-safe: [lifetimeEarnedCents] is Long cents (negative is treated
     * as zero) and the result is trivially unit-tested at its boundaries.
     */
    fun track(lifetimeEarnedCents: Long): List<MilestoneTier> {
        val earned = lifetimeEarnedCents.coerceAtLeast(0L)
        val activeIndex = tiers.indexOfFirst { earned < it.thresholdCents }
        return tiers.mapIndexed { index, tier ->
            val status = when {
                earned >= tier.thresholdCents -> MilestoneTierStatus.DONE
                index == activeIndex -> MilestoneTierStatus.ACTIVE
                else -> MilestoneTierStatus.LOCKED
            }
            MilestoneTier(thresholdCents = tier.thresholdCents, name = tier.name, status = status)
        }
    }
}

/**
 * Where a single milestone tier sits relative to the user's lifetime earnings.
 *
 * - [DONE] - the tier's threshold has been reached; it is an earned trophy.
 * - [ACTIVE] - the first not-yet-reached tier: the goal currently being chased.
 *   At most one tier is ACTIVE, and none is when every tier is [DONE].
 * - [LOCKED] - beyond the active tier; not yet in reach.
 */
enum class MilestoneTierStatus { DONE, ACTIVE, LOCKED }

/**
 * One financial milestone tier with its resolved [status].
 *
 * All `val`s and [Immutable], so a list of these is stable for Compose skipping.
 *
 * @param thresholdCents The lifetime-earnings threshold that unlocks the tier, in cents.
 * @param name The tier's display name (e.g. "Century Club").
 * @param status Where the tier sits relative to lifetime earnings.
 */
@Immutable
data class MilestoneTier(
    val thresholdCents: Long,
    val name: String,
    val status: MilestoneTierStatus
)

/**
 * A balance's resolved position in the financial milestone system.
 *
 * All `val`s and [Immutable], so it is stable for Compose skipping and safe to
 * carry inside [com.oink.app.viewmodel.HeroBankState].
 *
 * @param currentTierName Highest tier reached, or null below the first tier.
 * @param nextThresholdCents The next tier's threshold in cents, or null when
 *   every tier is cleared (maxed out).
 * @param nextTierName The next tier's name, or null when maxed out.
 * @param prevThresholdCents The floor the progress bar fills from - the current
 *   tier's threshold, or zero below the first tier.
 * @param progress Fill fraction from [prevThresholdCents] to [nextThresholdCents],
 *   in `0f..1f`; always `1f` when maxed out.
 */
@Immutable
data class MilestoneProgress(
    val currentTierName: String?,
    val nextThresholdCents: Long?,
    val nextTierName: String?,
    val prevThresholdCents: Long,
    val progress: Float
) {
    /**
     * Whether every tier has been cleared. When true there is no next goal and
     * [progress] is full.
     */
    val isMaxed: Boolean get() = nextThresholdCents == null
}
