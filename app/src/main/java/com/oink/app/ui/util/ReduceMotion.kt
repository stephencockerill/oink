package com.oink.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.oink.app.utils.Motion

/**
 * Whether animations should be skipped, read from the system animator duration
 * scale ([Motion.prefersReducedMotion]).
 *
 * The single shared reduce-motion gate for the UI layer: every animated surface
 * reads this before deciding whether to animate or jump straight to the final
 * state. Held for the composition; a mid-session accessibility change applies on
 * the next recomposition, which is enough for a value this coarse.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Motion.prefersReducedMotion(Motion.animatorDurationScale(context.contentResolver))
    }
}
