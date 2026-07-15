package com.oink.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [ConfettiParticle]'s pure projectile physics and the [seedConfetti]
 * seeder. No frame clock or Android framework needed - position is an analytic
 * function of elapsed time, so every claim about the motion is checked directly.
 */
class ConfettiParticleTest {

    private fun particle(
        velocityX: Float = 100f,
        velocityY: Float = -200f,
        spin: Float = 90f
    ) = ConfettiParticle(
        originXFraction = 0.5f,
        originYFraction = 0.3f,
        velocityX = velocityX,
        velocityY = velocityY,
        colorIndex = 0,
        sizePx = 10f,
        spinDegPerSecond = spin
    )

    @Test
    fun `offset at time zero is the origin`() {
        val offset = particle().offsetAt(seconds = 0f, gravityPxPerSecond2 = 1_000f)

        assertEquals(0f, offset.x, 0f)
        assertEquals(0f, offset.y, 0f)
    }

    @Test
    fun `horizontal offset advances linearly with velocity`() {
        val offset = particle(velocityX = 100f).offsetAt(seconds = 2f, gravityPxPerSecond2 = 0f)

        assertEquals(200f, offset.x, 0.0001f)
    }

    @Test
    fun `gravity pulls the piece downward over time`() {
        val p = particle(velocityY = 0f)
        val early = p.offsetAt(seconds = 0.1f, gravityPxPerSecond2 = 1_000f)
        val late = p.offsetAt(seconds = 0.5f, gravityPxPerSecond2 = 1_000f)

        // y grows downward (positive) and accelerates.
        assertTrue(late.y > early.y)
        // y = 0.5 * g * t^2 = 0.5 * 1000 * 0.25 = 125 at t = 0.5.
        assertEquals(125f, late.y, 0.0001f)
    }

    @Test
    fun `an upward launch rises before it falls`() {
        // velocityY negative = upward; gravity eventually overcomes it.
        val p = particle(velocityY = -200f)
        val rising = p.offsetAt(seconds = 0.1f, gravityPxPerSecond2 = 1_000f)
        val fallen = p.offsetAt(seconds = 1f, gravityPxPerSecond2 = 1_000f)

        assertTrue("piece should be above origin early", rising.y < 0f)
        assertTrue("piece should be below origin later", fallen.y > 0f)
    }

    @Test
    fun `rotation advances linearly with spin rate`() {
        assertEquals(45f, particle(spin = 90f).rotationAt(seconds = 0.5f), 0.0001f)
    }

    @Test
    fun `seedConfetti produces the requested count with valid colors`() {
        val particles = seedConfetti(count = 30, random = Random(seed = 42))

        assertEquals(30, particles.size)
        // Four brand colors in the palette; every index must be addressable.
        assertTrue(particles.all { it.colorIndex in 0..3 })
        // Seeded upward-and-out: every launch has upward (negative) vertical velocity.
        assertTrue(particles.all { it.velocityY < 0f })
    }
}
