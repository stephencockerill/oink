package com.oink.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale for Oink.
 *
 * A single 4dp-based rhythm for padding, gaps, and arrangement spacing. Reading
 * spacing through named tokens keeps vertical and horizontal rhythm consistent
 * across screens and makes a global adjustment a one-line change.
 *
 * | Token | Value | Typical use                                        |
 * |-------|-------|----------------------------------------------------|
 * | xs    | 4dp   | tight gaps (icon-to-label, chip internals)         |
 * | sm    | 8dp   | small gaps between related elements                |
 * | md    | 12dp  | gaps inside a grouped row/column                   |
 * | lg    | 16dp  | default content padding and inter-item spacing     |
 * | xl    | 24dp  | screen edge padding, section separation            |
 * | xxl   | 32dp  | large section breaks, empty-state breathing room   |
 *
 * These are plain [Dp] constants rather than a composition-local: spacing never
 * varies by theme, so a global object is the simplest source of truth.
 */
object OinkSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
