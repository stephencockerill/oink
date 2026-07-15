package com.oink.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.oink.app.R
import com.oink.app.utils.MascotState

/**
 * Renders the Oink pig mascot for a given [MascotState].
 *
 * Each state maps to a bespoke, in-app vector drawable via an exhaustive `when`, so adding a
 * new [MascotState] is a compile error here until it is handled. The drawable colors are baked
 * into the vector art (not theme attributes) and chosen to read on both the pink hero gradient
 * and dark surfaces.
 *
 * @param state Which expression to show.
 * @param modifier Standard Compose [Modifier]; the caller controls size (e.g. `Modifier.size(96.dp)`).
 * @param contentDescription Accessibility label; defaults to a per-state description. Pass `null`
 *   for a decorative instance sitting next to redundant text.
 */
@Composable
fun OinkMascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    contentDescription: String? = stringResource(state.contentDescriptionRes())
) {
    Image(
        painter = painterResource(state.drawableRes()),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@DrawableRes
private fun MascotState.drawableRes(): Int = when (this) {
    MascotState.HAPPY -> R.drawable.ic_mascot_happy
    MascotState.COMEBACK -> R.drawable.ic_mascot_comeback
    MascotState.SLEEPING -> R.drawable.ic_mascot_sleeping
    MascotState.NEUTRAL -> R.drawable.ic_mascot_neutral
}

private fun MascotState.contentDescriptionRes(): Int = when (this) {
    MascotState.HAPPY -> R.string.mascot_desc_happy
    MascotState.COMEBACK -> R.string.mascot_desc_comeback
    MascotState.SLEEPING -> R.string.mascot_desc_sleeping
    MascotState.NEUTRAL -> R.string.mascot_desc_neutral
}
