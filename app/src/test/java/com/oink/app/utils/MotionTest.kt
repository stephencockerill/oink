package com.oink.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Motion.prefersReducedMotion], the pure reduce-motion gate.
 *
 * A scale of exactly zero (the user turned animations off) reduces motion; any
 * positive scale animates. This is the predicate every animated surface checks
 * before deciding whether to animate or jump straight to the final value.
 */
class MotionTest {

    @Test
    fun `zero scale prefers reduced motion`() {
        assertTrue(Motion.prefersReducedMotion(0f))
    }

    @Test
    fun `normal scale does not prefer reduced motion`() {
        assertFalse(Motion.prefersReducedMotion(1f))
    }

    @Test
    fun `slowed scale still animates`() {
        assertFalse(Motion.prefersReducedMotion(0.5f))
    }

    @Test
    fun `sped-up scale still animates`() {
        assertFalse(Motion.prefersReducedMotion(2f))
    }
}
