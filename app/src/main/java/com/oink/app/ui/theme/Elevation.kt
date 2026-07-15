package com.oink.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens for Oink.
 *
 * [level0]..[level5] mirror the Material 3 tonal elevation levels, so cards and
 * surfaces express depth through the same dp scale Material uses for its tonal
 * overlay (and, in light theme, its shadow).
 *
 * | Token  | Value | Typical use                                   |
 * |--------|-------|-----------------------------------------------|
 * | level0 | 0dp   | flat surfaces flush with the background       |
 * | level1 | 1dp   | subtle separation                             |
 * | level2 | 3dp   | resting cards                                 |
 * | level3 | 6dp   | raised cards, list items                      |
 * | level4 | 8dp   | prominent cards                               |
 * | level5 | 12dp  | the highest standard surfaces                 |
 *
 * [hero] is the soft ambient-shadow depth for hero surfaces (the balance and
 * piggy-bank cards). Paired with [OinkShadowSoft] it lifts a hero gently instead
 * of casting the hard drop shadow a raw 12dp `cardElevation` produces.
 */
object OinkElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp

    val hero: Dp = 8.dp
}
