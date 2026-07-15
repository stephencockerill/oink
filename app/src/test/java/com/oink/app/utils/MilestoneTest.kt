package com.oink.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Milestone.resolve].
 *
 * Pure function, so every boundary is covered directly: zero, exactly on a
 * threshold, between thresholds, and above the top tier. Progress must fill from
 * the previous tier, not from zero, so early progress inside a tier is visible.
 */
class MilestoneTest {

    @Test
    fun `zero balance targets the first tier from an empty bar`() {
        val result = Milestone.resolve(0L)

        assertNull(result.currentTierName)
        assertEquals(2_500L, result.nextThresholdCents)
        assertEquals("Quarter Pounder", result.nextTierName)
        assertEquals(0L, result.prevThresholdCents)
        assertEquals(0f, result.progress, 0f)
        assertFalse(result.isMaxed)
    }

    @Test
    fun `just below the first tier is nearly full toward it`() {
        val result = Milestone.resolve(2_499L)

        assertNull(result.currentTierName)
        assertEquals(2_500L, result.nextThresholdCents)
        assertEquals("Quarter Pounder", result.nextTierName)
        assertEquals(0L, result.prevThresholdCents)
        assertEquals(2_499f / 2_500f, result.progress, 0.0001f)
    }

    @Test
    fun `exactly on a threshold reaches that tier and resets the bar toward the next`() {
        val result = Milestone.resolve(2_500L)

        assertEquals("Quarter Pounder", result.currentTierName)
        assertEquals(5_000L, result.nextThresholdCents)
        assertEquals("Half-Swole", result.nextTierName)
        assertEquals(2_500L, result.prevThresholdCents)
        // On the threshold the next-tier bar starts empty, not full.
        assertEquals(0f, result.progress, 0f)
    }

    @Test
    fun `between two tiers fills from the previous threshold`() {
        // $127.50, between Century Club ($100) and Swole Savings ($200).
        val result = Milestone.resolve(12_750L)

        assertEquals("Century Club", result.currentTierName)
        assertEquals(20_000L, result.nextThresholdCents)
        assertEquals("Swole Savings", result.nextTierName)
        assertEquals(10_000L, result.prevThresholdCents)
        // (12750 - 10000) / (20000 - 10000) = 0.275
        assertEquals(0.275f, result.progress, 0.0001f)
        assertFalse(result.isMaxed)
    }

    @Test
    fun `exactly on the top tier is maxed with a full bar and no next goal`() {
        val result = Milestone.resolve(20_000L)

        assertEquals("Swole Savings", result.currentTierName)
        assertNull(result.nextThresholdCents)
        assertNull(result.nextTierName)
        assertEquals(20_000L, result.prevThresholdCents)
        assertEquals(1f, result.progress, 0f)
        assertTrue(result.isMaxed)
    }

    @Test
    fun `above the top tier stays maxed`() {
        val result = Milestone.resolve(50_000L)

        assertEquals("Swole Savings", result.currentTierName)
        assertNull(result.nextThresholdCents)
        assertEquals(1f, result.progress, 0f)
        assertTrue(result.isMaxed)
    }

    @Test
    fun `negative balance is treated as zero`() {
        val result = Milestone.resolve(-100L)

        assertNull(result.currentTierName)
        assertEquals(2_500L, result.nextThresholdCents)
        assertEquals(0f, result.progress, 0f)
    }

    @Test
    fun `rankFor is zero below the first tier`() {
        assertEquals(0, Milestone.rankFor(0L))
        assertEquals(0, Milestone.rankFor(2_499L))
    }

    @Test
    fun `rankFor treats a negative balance as zero rank`() {
        assertEquals(0, Milestone.rankFor(-500L))
    }

    @Test
    fun `rankFor increments exactly on each threshold`() {
        assertEquals(1, Milestone.rankFor(2_500L))
        assertEquals(2, Milestone.rankFor(5_000L))
        assertEquals(3, Milestone.rankFor(10_000L))
        assertEquals(4, Milestone.rankFor(20_000L))
    }

    @Test
    fun `rankFor holds steady between thresholds`() {
        // Just past a threshold and just before the next both read the lower rank.
        assertEquals(1, Milestone.rankFor(2_501L))
        assertEquals(1, Milestone.rankFor(4_999L))
        assertEquals(3, Milestone.rankFor(12_750L))
    }

    @Test
    fun `rankFor caps at the top tier above the highest threshold`() {
        assertEquals(4, Milestone.rankFor(50_000L))
        assertEquals(4, Milestone.rankFor(Long.MAX_VALUE))
    }

    // =========================================================================
    // track() - the full per-tier status list off cumulative lifetime earnings
    // =========================================================================

    @Test
    fun `track below the first tier makes the first tier active and the rest locked`() {
        val track = Milestone.track(0L)

        assertEquals(
            listOf("Quarter Pounder", "Half-Swole", "Century Club", "Swole Savings"),
            track.map { it.name }
        )
        assertEquals(
            listOf(2_500L, 5_000L, 10_000L, 20_000L),
            track.map { it.thresholdCents }
        )
        assertEquals(
            listOf(
                MilestoneTierStatus.ACTIVE,
                MilestoneTierStatus.LOCKED,
                MilestoneTierStatus.LOCKED,
                MilestoneTierStatus.LOCKED
            ),
            track.map { it.status }
        )
    }

    @Test
    fun `track exactly on a threshold marks that tier done and the next active`() {
        // Exactly $50 lifetime: first two tiers reached, third is the new goal.
        val track = Milestone.track(5_000L)

        assertEquals(
            listOf(
                MilestoneTierStatus.DONE,
                MilestoneTierStatus.DONE,
                MilestoneTierStatus.ACTIVE,
                MilestoneTierStatus.LOCKED
            ),
            track.map { it.status }
        )
    }

    @Test
    fun `track between two tiers keeps the lower ones done and the next one active`() {
        // $127.50 lifetime: through Century Club, chasing Swole Savings.
        val track = Milestone.track(12_750L)

        assertEquals(
            listOf(
                MilestoneTierStatus.DONE,
                MilestoneTierStatus.DONE,
                MilestoneTierStatus.DONE,
                MilestoneTierStatus.ACTIVE
            ),
            track.map { it.status }
        )
    }

    @Test
    fun `track with every tier cleared has no active tier`() {
        val track = Milestone.track(20_000L)

        assertTrue(track.all { it.status == MilestoneTierStatus.DONE })
        assertFalse(track.any { it.status == MilestoneTierStatus.ACTIVE })
    }

    @Test
    fun `track above the top tier stays all done`() {
        val track = Milestone.track(999_999L)

        assertTrue(track.all { it.status == MilestoneTierStatus.DONE })
    }

    @Test
    fun `track treats negative earnings as zero`() {
        val track = Milestone.track(-500L)

        assertEquals(MilestoneTierStatus.ACTIVE, track.first().status)
        assertTrue(track.drop(1).all { it.status == MilestoneTierStatus.LOCKED })
    }
}
