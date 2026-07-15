package com.oink.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.oink.app.ui.theme.OinkGold
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkSuccess
import com.oink.app.ui.theme.OinkTeal
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One piece of confetti and its pure projectile physics.
 *
 * A particle is launched from a normalized origin with an initial velocity and
 * falls under gravity. Position is analytic - `p = p0 + v0·t + ½·g·t²` - so the
 * physics is a pure, allocation-free function of elapsed time, unit-testable
 * without a frame clock ([offsetAt]).
 *
 * @param originXFraction Launch X as a fraction of the burst width (`0f..1f`).
 * @param originYFraction Launch Y as a fraction of the burst height (`0f..1f`).
 * @param velocityX Initial horizontal velocity, px/second.
 * @param velocityY Initial vertical velocity, px/second (negative is upward).
 * @param colorIndex Index into the confetti palette.
 * @param sizePx Edge length of the square piece, px.
 * @param spinDegPerSecond Rotation rate, degrees/second.
 */
data class ConfettiParticle(
    val originXFraction: Float,
    val originYFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val colorIndex: Int,
    val sizePx: Float,
    val spinDegPerSecond: Float
) {
    /**
     * The particle's pixel offset from its launch origin after [seconds] under
     * [gravityPxPerSecond2] (downward positive). Pure: `offsetAt(0f, g) == (0,0)`.
     */
    fun offsetAt(seconds: Float, gravityPxPerSecond2: Float): Offset {
        val x = velocityX * seconds
        val y = velocityY * seconds + 0.5f * gravityPxPerSecond2 * seconds * seconds
        return Offset(x, y)
    }

    /** The rotation, in degrees, after [seconds]. */
    fun rotationAt(seconds: Float): Float = spinDegPerSecond * seconds
}

/**
 * The confetti palette - the app's celebratory brand colors plus gold.
 */
private val ConfettiColors: List<Color> = listOf(OinkPink, OinkTeal, OinkSuccess, OinkGold)

/**
 * Downward acceleration for the burst, px/second². Tuned for a lively but brief
 * fall, not free-fall realism.
 */
private const val CONFETTI_GRAVITY = 2_600f

/**
 * How long the burst runs before it reports `onFinished` and can be removed.
 */
private const val CONFETTI_DURATION_SECONDS = 1.4f

/**
 * Seed a fresh set of particles fired upward-and-out from the top-center of the
 * burst area, in a random spread so no two bursts look identical.
 */
internal fun seedConfetti(count: Int, random: Random): List<ConfettiParticle> =
    List(count) {
        // Launch angle biased upward (about -pi/2), fanned left and right.
        val angle = random.nextDouble(-2.7, -0.44)
        val speed = random.nextDouble(700.0, 1_500.0)
        ConfettiParticle(
            originXFraction = random.nextFloat() * 0.5f + 0.25f,
            originYFraction = 0.3f,
            velocityX = (cos(angle) * speed).toFloat(),
            velocityY = (sin(angle) * speed).toFloat(),
            colorIndex = random.nextInt(ConfettiColors.size),
            sizePx = random.nextDouble(8.0, 16.0).toFloat(),
            spinDegPerSecond = random.nextDouble(-360.0, 360.0).toFloat()
        )
    }

/**
 * A Compose-native confetti burst rendered on a [Canvas] with simple gravity
 * physics - no Lottie, no external asset.
 *
 * Particles are seeded once and advanced by a single elapsed-time value driven off
 * the frame clock ([withFrameNanos]); each particle's position is computed
 * analytically in the draw phase ([ConfettiParticle.offsetAt]), so only drawing
 * re-runs per frame, not composition. The caller gates this on reduce-motion and
 * keys it to replay per celebration.
 *
 * @param onFinished Invoked once when the burst completes, so the caller can drop it.
 */
@Composable
fun ConfettiBurst(
    modifier: Modifier = Modifier,
    particleCount: Int = 44,
    onFinished: () -> Unit = {}
) {
    val particles = remember { seedConfetti(particleCount, Random(System.nanoTime())) }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var running = true
        while (running) {
            withFrameNanos { now ->
                val delta = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = now
                elapsedSeconds += delta
                if (elapsedSeconds >= CONFETTI_DURATION_SECONDS) {
                    running = false
                }
            }
        }
        onFinished()
    }

    Canvas(modifier = modifier) {
        val t = elapsedSeconds
        val fade = (1f - t / CONFETTI_DURATION_SECONDS).coerceIn(0f, 1f)
        particles.forEach { particle ->
            val origin = Offset(
                x = particle.originXFraction * size.width,
                y = particle.originYFraction * size.height
            )
            val position = origin + particle.offsetAt(t, CONFETTI_GRAVITY)
            rotate(degrees = particle.rotationAt(t), pivot = position) {
                drawRect(
                    color = ConfettiColors[particle.colorIndex].copy(alpha = fade),
                    topLeft = Offset(
                        position.x - particle.sizePx / 2f,
                        position.y - particle.sizePx / 2f
                    ),
                    size = Size(particle.sizePx, particle.sizePx)
                )
            }
        }
    }
}
