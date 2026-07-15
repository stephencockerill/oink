package com.oink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.ui.theme.OinkGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A single gold coin that drops in from above and settles with a spring, then
 * fades away - the visible "cha-ching" paired with a balance gain on the hero card
 * (docs/habit-psychology.md: "coins dropping").
 *
 * The drop uses a spatial [MaterialTheme.motionScheme] spring so it lands with a
 * little weight; the fade uses an effects spring (no overshoot). Position and
 * opacity are read in the `graphicsLayer` lambda, so only the draw phase re-runs
 * as the coin animates, not composition. The caller mounts this only when motion
 * is enabled and keys it so each gain replays a fresh coin.
 *
 * @param onFinished Invoked once the coin has settled and faded, so the caller can drop it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FallingCoin(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    val dropSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    val dropDistancePx = with(LocalDensity.current) { 84.dp.toPx() }
    val offsetY = remember { Animatable(-dropDistancePx) }
    val alpha = remember { Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, fadeSpec) }
        offsetY.animateTo(0f, dropSpec)
        delay(450)
        alpha.animateTo(0f, fadeSpec)
        onFinished()
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = offsetY.value
                this.alpha = alpha.value
            }
            .size(30.dp)
            .clip(CircleShape)
            .drawBehind {
                // A thin darker rim reads the disc as a coin rather than a dot.
                drawCircle(
                    color = Color(0xFFB8860B),
                    style = Stroke(width = size.minDimension * 0.08f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFE082), OinkGold)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF7A5C00)
            )
        }
    }
}
