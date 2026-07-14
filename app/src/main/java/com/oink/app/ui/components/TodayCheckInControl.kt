package com.oink.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.utils.HabitCopy

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
 * and carries a spoken [contentDescription] for TalkBack.
 */
@Composable
fun TodayCheckInControl(
    todayCompleted: Boolean?,
    onCheckIn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
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
                onClick = { onCheckIn(true) }
            )
            CheckInIconButton(
                icon = Icons.Default.Close,
                tint = OinkWarning,
                contentDescription = HabitCopy.CHECK_IN_MISSED_ACTION,
                onClick = { onCheckIn(false) }
            )
        }
    } else {
        val tint = if (todayCompleted) OinkTeal else MaterialTheme.colorScheme.onSurfaceVariant
        CheckInIconButton(
            icon = if (todayCompleted) Icons.Default.Check else Icons.Default.Close,
            tint = tint,
            contentDescription = HabitCopy.CHECK_IN_CHANGE_ACTION,
            onClick = { onCheckIn(!todayCompleted) },
            modifier = modifier
        )
    }
}

/**
 * A single circular, tinted 48dp check-in affordance. The 48dp [IconButton] is
 * the touch target; the tinted circle inside is the visible surface.
 */
@Composable
private fun CheckInIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
