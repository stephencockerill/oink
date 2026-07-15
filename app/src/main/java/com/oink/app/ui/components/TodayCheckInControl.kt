package com.oink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import com.oink.app.data.HabitType
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.ui.util.Haptics
import com.oink.app.ui.util.rememberReduceMotion
import com.oink.app.utils.HabitCopy
import kotlinx.coroutines.launch

/**
 * The inline quick check-in control shown on a habit card, trailing the balance.
 *
 * Two states, both entirely self-contained so tapping the control never triggers
 * the card's own click (in Compose a child [IconButton]'s pointer input is
 * consumed by the button, so the parent card's `clickable` does not also fire -
 * navigation into the detail screen only happens when the card body is tapped):
 *
 * - Not logged today ([todayCompleted] `null`): two explicit controls, a teal
 *   "done" and an amber "missed". Two buttons rather than a single toggle so a
 *   stray tap can never trigger the irreversible-feeling miss halving by
 *   accident - it mirrors the detail screen's YES/NO.
 * - Already logged today: a single status badge (teal check for done, muted
 *   cross for an off day, matching the detail screen's status card) that is
 *   tappable to flip the outcome. That flip is the in-card undo/recovery path;
 *   it is exact, because the miss halving is computed off the previous day's
 *   balance and replayed, not off the halved value.
 *
 * Every control is a 48dp [IconButton], satisfying the minimum touch-target size,
 * and carries a spoken [contentDescription] for TalkBack. A tap fires a haptic
 * (confirm for a done/clean day, a softer reject for a miss/slip) regardless of
 * reduce-motion, and the affirmative "done" control shape-morphs on tap.
 *
 * A quit habit reads the same [todayCompleted] differently: a clean day is the
 * passive default and the one affirmative action is reporting a slip, so instead
 * of two buttons it shows a single destructive "I slipped" control. Tapping a
 * logged slip undoes it (back to clean); success is never a routine tap.
 */
@Composable
fun TodayCheckInControl(
    todayCompleted: Boolean?,
    habitType: HabitType,
    onCheckIn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    when (habitType) {
        HabitType.BUILD -> BuildCheckInControl(todayCompleted, onCheckIn, modifier)
        HabitType.QUIT -> QuitCheckInControl(todayCompleted, onCheckIn, modifier)
    }
}

@Composable
private fun BuildCheckInControl(
    todayCompleted: Boolean?,
    onCheckIn: (Boolean) -> Unit,
    modifier: Modifier
) {
    if (todayCompleted == null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CheckInIconButton(
                icon = Icons.Default.Check,
                tint = OinkTeal,
                contentDescription = HabitCopy.CHECK_IN_DONE_ACTION,
                done = true,
                celebratory = true,
                onClick = { onCheckIn(true) }
            )
            CheckInIconButton(
                icon = Icons.Default.Close,
                tint = OinkWarning,
                contentDescription = HabitCopy.CHECK_IN_MISSED_ACTION,
                done = false,
                onClick = { onCheckIn(false) }
            )
        }
    } else {
        val tint = if (todayCompleted) OinkTeal else MaterialTheme.colorScheme.onSurfaceVariant
        CheckInIconButton(
            icon = if (todayCompleted) Icons.Default.Check else Icons.Default.Close,
            tint = tint,
            contentDescription = HabitCopy.CHECK_IN_CHANGE_ACTION,
            done = !todayCompleted,
            celebratory = !todayCompleted,
            onClick = { onCheckIn(!todayCompleted) },
            modifier = modifier
        )
    }
}

@Composable
private fun QuitCheckInControl(
    todayCompleted: Boolean?,
    onCheckIn: (Boolean) -> Unit,
    modifier: Modifier
) {
    when (todayCompleted) {
        // Clean-so-far: the only control is the destructive "I slipped".
        null -> CheckInIconButton(
            icon = Icons.Default.Close,
            tint = OinkWarning,
            contentDescription = HabitCopy.CHECK_IN_SLIP_ACTION,
            done = false,
            onClick = { onCheckIn(false) },
            modifier = modifier
        )
        // A slip is logged: tapping undoes it back to clean.
        false -> CheckInIconButton(
            icon = Icons.Default.Close,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = HabitCopy.CHECK_IN_SLIP_CHANGE_ACTION,
            done = true,
            onClick = { onCheckIn(true) },
            modifier = modifier
        )
        // Marked clean (e.g. via undo): tapping corrects it back to a slip.
        true -> CheckInIconButton(
            icon = Icons.Default.Check,
            tint = OinkTeal,
            contentDescription = HabitCopy.CHECK_IN_SLIP_CHANGE_ACTION,
            done = false,
            onClick = { onCheckIn(false) },
            modifier = modifier
        )
    }
}

/**
 * A single circular, tinted 48dp check-in affordance. The 48dp [IconButton] is
 * the touch target; the tinted circle inside is the visible surface.
 *
 * @param done Whether this tap records a positive outcome (done / clean day),
 *   which selects the confirm haptic; a miss / slip uses the softer reject.
 * @param celebratory When true and motion is enabled, the button shape-morphs
 *   from a disc to a scalloped star on tap - the Expressive flourish reserved for
 *   the affirmative "done" action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckInIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    celebratory: Boolean = false
) {
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val morphEnabled = celebratory && !reduceMotion
    val morph = remember { Morph(checkInDisc, checkInStar) }
    val morphProgress = remember { Animatable(0f) }
    val pulseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    val shape: Shape = if (morphEnabled) {
        MorphPolygonShape(morph, morphProgress.value)
    } else {
        CircleShape
    }

    IconButton(
        onClick = {
            if (done) Haptics.confirm(view) else Haptics.reject(view)
            if (morphEnabled) {
                scope.launch {
                    morphProgress.snapTo(0f)
                    morphProgress.animateTo(1f, pulseSpec)
                    morphProgress.animateTo(0f, pulseSpec)
                }
            }
            onClick()
        },
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.12f), shape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The two shapes the affirmative check-in button morphs between: a smooth disc at
 * rest and a scalloped star at the peak of the tap pulse. Built once - polygon
 * construction is pure and cheap - and shared across every check-in button.
 */
private val checkInDisc: RoundedPolygon = RoundedPolygon.circle(numVertices = 12)
private val checkInStar: RoundedPolygon = RoundedPolygon.star(
    numVerticesPerRadius = 8,
    innerRadius = 0.75f,
    rounding = CornerRounding(radius = 0.2f)
)
