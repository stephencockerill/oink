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
}
