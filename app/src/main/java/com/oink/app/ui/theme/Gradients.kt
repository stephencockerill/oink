package com.oink.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.math.max

/**
 * The hero brush: a soft mesh-style gradient for the balance and piggy-bank
 * cards.
 *
 * A bright highlight glows from the upper-left over a coral-pink base and settles
 * into a deeper pink toward the lower-right, giving the flat pink fill a lit,
 * dimensional feel. Because it is a [ShaderBrush] the highlight is placed
 * relative to the surface size, so it reads correctly at any card dimension.
 *
 * Foundation only - this helper is not applied to any screen yet. Draw it as a
 * `Modifier.background(brush = oinkHeroBrush())` (or on a `Box`) on a hero
 * surface when the hero is styled.
 *
 * @param base the coral-pink field the highlight sits over
 * @param highlight the lit tone glowing from the upper-left
 * @param shade the deeper pink the field falls into
 */
fun oinkHeroBrush(
    base: Color = OinkPink,
    highlight: Color = OinkPinkLight,
    shade: Color = OinkPinkDark
): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radius = max(size.width, size.height) * 1.15f
        return RadialGradientShader(
            center = Offset(x = size.width * 0.18f, y = size.height * 0.12f),
            radius = if (radius > 0f) radius else 1f,
            colors = listOf(
                highlight.copy(alpha = 0.85f),
                base,
                base,
                shade
            ),
            colorStops = listOf(0f, 0.35f, 0.7f, 1f),
            tileMode = TileMode.Clamp
        )
    }
}
