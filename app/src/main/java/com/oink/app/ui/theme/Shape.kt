package com.oink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens for Oink.
 *
 * A single rounded scale drives every surface so radii stay consistent and are
 * tuned in one place. The scale leans slightly rounder than the Material 3
 * baseline, matching Oink's soft, playful character.
 *
 * | Token       | Radius | Typical use                                  |
 * |-------------|--------|----------------------------------------------|
 * | extraSmall  | 8dp    | chips, small inline surfaces                 |
 * | small       | 12dp   | compact cards, list rows, badges             |
 * | medium      | 16dp   | standard cards, buttons, input fields        |
 * | large       | 20dp   | prominent cards, tiles, dialogs              |
 * | extraLarge  | 28dp   | hero surfaces (balance, piggy bank)          |
 *
 * Wired into [OinkTheme] via `MaterialTheme(shapes = OinkShapes)`, so components
 * resolve their default shapes from this scale and screens read tokens through
 * `MaterialTheme.shapes.*` rather than inlining raw radii.
 */
val OinkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
