package com.oink.app.viewmodel

import androidx.compose.runtime.Immutable
import com.oink.app.utils.MascotState
import com.oink.app.utils.MilestoneProgress

/**
 * The immutable state the shared hero bank card renders.
 *
 * Produced identically by both heroes - the home overall-bank card
 * ([HabitListViewModel.heroState]) and the per-habit detail card
 * ([MainViewModel.heroState]) - so they stay visually and behaviorally
 * consistent. Everything the card shows is precomputed here off the reactive
 * sources; the composable does no derivation.
 *
 * All `val`s and [Immutable] (every field is a primitive, an enum, a String, or
 * an immutable data class), so the card skips recomposition unless the state
 * genuinely changes.
 *
 * @param balanceCents The headline balance in cents - the shared pot on home, a
 *   habit's spendable balance on detail. The card count-animates to this value.
 * @param dailyGainCents Cents earned today (`>= 0`); drives the "+$X today" gain
 *   chip, hidden when zero.
 * @param streak The streak to flame - the hottest streak across public habits on
 *   home, this habit's streak on detail. The flame is hidden when zero.
 * @param mascotState Which pig expression to show, resolved via
 *   [com.oink.app.utils.Mascot.stateFor].
 * @param milestone Progress toward the next financial tier.
 * @param label The card's small heading (e.g. "Piggy Bank" or the balance label).
 * @param subtitle A supporting line under the balance; blank means render nothing.
 */
@Immutable
data class HeroBankState(
    val balanceCents: Long,
    val dailyGainCents: Long,
    val streak: Int,
    val mascotState: MascotState,
    val milestone: MilestoneProgress,
    val label: String,
    val subtitle: String
)
