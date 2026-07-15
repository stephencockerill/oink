package com.oink.app.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * A Compose [Shape] that renders a point along an Expressive shape [Morph].
 *
 * [androidx.graphics.shapes.RoundedPolygon]s are defined in a unit space centered
 * at the origin with radius 1, so the outline is scaled to fill the target [Size]
 * and translated to its center. Only [percentage] varies as the morph animates;
 * the [Morph] itself is built once and reused, so animating the shape allocates
 * just this lightweight wrapper.
 *
 * @param morph The two-shape morph to sample.
 * @param percentage Position along the morph, `0f` (start shape) to `1f` (end shape).
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float
) : Shape {

    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)

        val path: Path = morph.toPath(progress = percentage).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
