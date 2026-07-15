package com.oink.app.utils

import com.oink.app.data.CashOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RewardTimeline.build].
 *
 * Pure function, so the ordering rules are asserted directly: earned trophies lead
 * (highest tier first), followed by the cash-outs in the order supplied. Locked and
 * active tiers never appear.
 */
class RewardTimelineTest {

    private fun cashOut(id: Long, name: String) = CashOut(
        id = id,
        name = name,
        amount = 1_000L,
        balanceBefore = 5_000L,
        balanceAfter = 4_000L
    )

    @Test
    fun `empty track and no cash-outs yields an empty timeline`() {
        assertTrue(RewardTimeline.build(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `only done tiers become trophies, highest threshold first`() {
        val track = Milestone.track(12_750L) // three tiers done, top one active

        val entries = RewardTimeline.build(track, emptyList())

        val trophies = entries.filterIsInstance<RewardTimelineEntry.Trophy>()
        assertEquals(3, trophies.size)
        assertEquals(
            listOf(10_000L, 5_000L, 2_500L),
            trophies.map { it.tier.thresholdCents }
        )
        assertTrue(trophies.all { it.tier.status == MilestoneTierStatus.DONE })
    }

    @Test
    fun `trophies lead and cash-outs follow in the supplied order`() {
        val track = Milestone.track(2_500L) // one tier done
        val cashOuts = listOf(cashOut(2L, "Newest"), cashOut(1L, "Older"))

        val entries = RewardTimeline.build(track, cashOuts)

        assertTrue(entries.first() is RewardTimelineEntry.Trophy)
        val claims = entries.filterIsInstance<RewardTimelineEntry.Cashout>()
        assertEquals(listOf(2L, 1L), claims.map { it.cashOut.id })
    }

    @Test
    fun `no done tiers yields cash-outs only`() {
        val track = Milestone.track(0L) // nothing reached
        val cashOuts = listOf(cashOut(1L, "Treat"))

        val entries = RewardTimeline.build(track, cashOuts)

        assertEquals(1, entries.size)
        assertTrue(entries.single() is RewardTimelineEntry.Cashout)
    }
}
