package com.oink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.ui.theme.OinkElevation
import com.oink.app.ui.theme.OinkGold
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkShadowSoft
import com.oink.app.ui.theme.OinkSuccess
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.ui.theme.oinkHeroBrush
import com.oink.app.ui.util.Haptics
import com.oink.app.ui.util.rememberReduceMotion
import com.oink.app.utils.Formatters
import com.oink.app.utils.Milestone
import com.oink.app.viewmodel.HeroBankState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Peak opacity of the amber loss flash washed over the card on a halving. Amber,
// not red: the penalty is clear but never punitive.
private const val LOSS_FLASH_ALPHA = 0.45f

/**
 * The app's living "score": the shared hero bank card.
 *
 * One composable drives both heroes - the home overall-bank card and the
 * per-habit detail card - so they stay consistent (issue #92). It renders a mesh
 * gradient ([oinkHeroBrush]), the pig mascot, an animated count-up balance, a
 * daily-gain chip, a streak flame, and a milestone progress bar, all off an
 * immutable [HeroBankState].
 *
 * Motion is spring-based, driven by [MaterialTheme.motionScheme] (Material 3
 * Expressive) and shared with [RewardsHeroCard] through [HeroCardSurface]: a gain
 * rolls the balance up on a snappy spatial spring and drops a coin into the bank;
 * a halving sweeps down on a slow, weighty spring under an amber flash so the loss
 * lands; crossing into a new milestone tier bursts confetti, drops a coin, and
 * makes the pig hop. Every animation honors the system reduce-motion setting
 * ([rememberReduceMotion]): when motion is off the balance jumps straight to its
 * value with no count-up, sweep, coin, flame flicker, or confetti.
 *
 * Accessibility: the whole card is one actionable, merged semantics node with a
 * spoken summary of balance, gain, streak, and progress; decorative icons and the
 * mascot carry no redundant description inside it.
 *
 * @param state Everything to render, precomputed by the ViewModel.
 * @param onClick Invoked when the card is tapped (both heroes open Rewards).
 * @param modifier Standard [Modifier]; the caller controls width/placement.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroBankCard(
    state: HeroBankState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroCardSurface(
        balanceCents = state.balanceCents,
        modifier = modifier,
        interaction = Modifier
            .clickable(
                onClick = onClick,
                onClickLabel = "Open rewards"
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = heroContentDescription(state)
            }
    ) { displayedCents, mascotHopFraction ->
        HeroHeaderRow(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        HeroBalanceRow(state = state, displayedCents = displayedCents, mascotHopFraction = mascotHopFraction)
        Spacer(modifier = Modifier.height(20.dp))
        MilestoneBar(state = state)
    }
}

/**
 * The Rewards-screen hero: the same living bank card treatment as [HeroBankCard]
 * (mesh gradient, mascot, count-up balance, streak flame, halving sweep, coin drop,
 * and milestone celebration), but compact for the Rewards story.
 *
 * It drops the milestone bar - the Rewards screen has a dedicated milestone track
 * below - and replaces the tap-to-open-rewards behavior with an explicit "Treat
 * yourself" CTA (we are already on Rewards). The CTA is disabled at a zero balance
 * and paired with a "show up to earn" hint.
 *
 * Accessibility: the balance summary is one merged, spoken node; the CTA is a
 * separate, independently actionable button so TalkBack reaches it directly.
 *
 * @param state Everything to render, precomputed by the ViewModel.
 * @param onTreatYourself Invoked when the CTA is tapped (opens the cash-out sheet).
 * @param modifier Standard [Modifier]; the caller controls width/placement.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RewardsHeroCard(
    state: HeroBankState,
    onTreatYourself: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroCardSurface(
        balanceCents = state.balanceCents,
        modifier = modifier
    ) { displayedCents, mascotHopFraction ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = rewardsHeroContentDescription(state)
                }
        ) {
            HeroHeaderRow(state = state)
            Spacer(modifier = Modifier.height(12.dp))
            HeroBalanceRow(state = state, displayedCents = displayedCents, mascotHopFraction = mascotHopFraction)
        }

        Spacer(modifier = Modifier.height(20.dp))

        TreatYourselfButton(
            enabled = state.balanceCents > 0,
            onClick = onTreatYourself
        )

        if (state.balanceCents == 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Show up to earn rewards!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * The shared living-bank surface both heroes render into: mesh gradient, soft
 * shadow, and every balance-driven animation.
 *
 * This is the single source of truth for the hero's motion, all spring-based off
 * [MaterialTheme.motionScheme]: the count-up (a gain rolls up briskly, a halving
 * sweeps down slower under an amber flash), the coin that drops in on a gain, and
 * the milestone celebration (confetti burst + a pig hop) fired when the balance
 * crosses into a new [Milestone] tier. Motion honors the system reduce-motion
 * setting; the milestone haptic is tactile, not motion, so it fires regardless.
 *
 * The currently-displayed cents and a mascot hop-fraction provider are handed to
 * [content] so each hero lays out its own body while the mascot hop stays driven
 * here. The hop fraction is a provider read late in a `graphicsLayer`, so a hop
 * redraws only the mascot rather than recomposing the body.
 *
 * @param balanceCents The real balance the display animates toward.
 * @param modifier Standard [Modifier]; the caller controls width/placement.
 * @param interaction Click and/or semantics applied to the whole card (the full
 *   hero is one actionable node; the Rewards hero passes none and makes its CTA
 *   the actionable element instead).
 * @param content The card body, given the animated displayed-cents value and the
 *   mascot hop-fraction provider.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCardSurface(
    balanceCents: Long,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
    content: @Composable ColumnScope.(displayedCents: Long, mascotHopFraction: () -> Float) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val view = LocalView.current

    // The value the balance text currently shows; count-animated toward the real
    // balance so a gain rolls up and a halving sweeps down.
    var displayedCents by remember { mutableLongStateOf(balanceCents) }

    // Amber wash overlaid on a halving; 0 at rest. Read at draw time so pulsing it
    // never triggers recomposition.
    val lossFlash = remember { Animatable(0f) }

    // Replay counters: bumped to fire a fresh coin drop (a gain) and a fresh
    // milestone celebration (confetti + pig hop) via key().
    var coinDropId by remember { mutableIntStateOf(0) }
    var celebrateId by remember { mutableIntStateOf(0) }

    // The milestone rank the last time balance changed. Seeded to the current rank
    // so the celebration never fires on the first composition (initial load).
    var previousRank by remember { mutableIntStateOf(Milestone.rankFor(balanceCents)) }

    // Mascot vertical hop, in fraction of the hop height; read late in graphicsLayer.
    val hopOffset = remember { Animatable(0f) }

    // Expressive springs: a gain is snappy, a loss is slow and weighty, the flash
    // eases out without overshoot, the hop settles with a little bounce.
    val gainSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val lossSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val lossFlashSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val hopSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(balanceCents, reduceMotion) {
        val start = displayedCents
        val end = balanceCents

        // Detect a tier crossing before anything returns early.
        val newRank = Milestone.rankFor(end)
        val crossedUp = newRank > previousRank
        previousRank = newRank
        if (crossedUp) {
            // Haptics are tactile, not motion - fire on unlock regardless.
            Haptics.milestone(view)
            if (!reduceMotion) celebrateId++
        }

        if (start == end) return@LaunchedEffect

        // Reduce motion: no count-up, no sweep, no flash, no coin - jump to value.
        if (reduceMotion) {
            displayedCents = end
            return@LaunchedEffect
        }

        val increasing = end > start
        if (increasing) {
            coinDropId++
        } else {
            // Flash concurrently with the down-sweep.
            launch {
                lossFlash.snapTo(LOSS_FLASH_ALPHA)
                lossFlash.animateTo(0f, lossFlashSpec)
            }
        }

        // Clamp within the endpoints so a bouncy spatial spring can't momentarily
        // show more (or less) money than was actually earned or lost.
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = if (increasing) gainSpec else lossSpec
        ) {
            displayedCents = (start + ((end - start) * value).toLong()).coerceIn(lo, hi)
        }
        displayedCents = end
    }

    // The pig hops when a new tier unlocks: up, then a bouncy landing.
    LaunchedEffect(celebrateId) {
        if (celebrateId == 0 || reduceMotion) return@LaunchedEffect
        hopOffset.snapTo(0f)
        hopOffset.animateTo(1f, hopSpec)
        hopOffset.animateTo(0f, hopSpec)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = OinkElevation.hero,
                shape = MaterialTheme.shapes.extraLarge,
                ambientColor = OinkShadowSoft,
                spotColor = OinkShadowSoft
            )
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush = oinkHeroBrush())
            .drawBehind {
                if (lossFlash.value > 0f) {
                    drawRect(color = OinkWarning, alpha = lossFlash.value)
                }
            }
            .then(interaction)
            .padding(28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content(displayedCents) { hopOffset.value }
        }

        // A coin drops into the bank on a gain; a fresh coin per gain via key().
        if (!reduceMotion && coinDropId > 0) {
            key(coinDropId) {
                FallingCoin(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 44.dp, end = 40.dp)
                )
            }
        }

        // A confetti burst on a milestone unlock; a fresh burst per crossing.
        if (!reduceMotion && celebrateId > 0) {
            key(celebrateId) {
                ConfettiBurst(modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * The card's top line: the label, and the streak flame when there is a streak.
 */
@Composable
private fun HeroHeaderRow(state: HeroBankState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f)
        )
        if (state.streak > 0) {
            StreakFlame(streak = state.streak)
        }
    }
}

/**
 * The card's balance line: the count-up balance, optional subtitle and gain chip
 * on the left, and the mascot on the right. The mascot is described by the card's
 * merged semantics, so it carries no redundant description of its own here; it
 * hops on a milestone unlock via [mascotHopFraction], read late in a graphicsLayer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroBalanceRow(
    state: HeroBankState,
    displayedCents: Long,
    mascotHopFraction: () -> Float
) {
    val hopDistancePx = with(LocalDensity.current) { 22.dp.toPx() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Formatters.formatCurrency(displayedCents),
                style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = 44.sp),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (state.subtitle.isNotBlank()) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            if (state.dailyGainCents > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                GainChip(gainCents = state.dailyGainCents)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        OinkMascot(
            state = state.mascotState,
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { translationY = -mascotHopFraction() * hopDistancePx }
        )
    }
}

/**
 * The Rewards hero's "Treat yourself" CTA: a full-width white button that pops on
 * the pink gradient. Disabled at a zero balance.
 */
@Composable
private fun TreatYourselfButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = OinkPinkDark
        )
    ) {
        Icon(
            imageVector = Icons.Default.Redeem,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Treat yourself",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The streak flame chip: a fire icon plus the streak length.
 *
 * When motion is enabled the flame is alive - it flickers with a subtle,
 * organically-timed scale, rotation, and opacity driven by looping
 * [MaterialTheme.motionScheme] springs, read late in a `graphicsLayer` so only the
 * draw phase re-runs. When motion is reduced the flame is static.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreakFlame(streak: Int) {
    val reduceMotion = rememberReduceMotion()
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val flameAlpha = remember { Animatable(1f) }

    val fastSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val slowSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            scale.snapTo(1f)
            rotation.snapTo(0f)
            flameAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        // Three independent loops so scale, sway, and glow drift out of phase and
        // read as a living flicker rather than a metronome.
        launch { while (isActive) { scale.animateTo(1.16f, fastSpec); scale.animateTo(0.9f, slowSpec) } }
        launch { while (isActive) { rotation.animateTo(7f, fastSpec); rotation.animateTo(-7f, slowSpec) } }
        launch { while (isActive) { flameAlpha.animateTo(1f, effectsSpec); flameAlpha.animateTo(0.7f, effectsSpec) } }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = OinkGold,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = rotation.value
                    alpha = flameAlpha.value
                }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$streak",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * The daily-gain chip: an upward-trend icon plus "+$X today". Success green -
 * gains are celebratory (docs/habit-psychology.md).
 */
@Composable
private fun GainChip(gainCents: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(OinkSuccess)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "+${Formatters.formatCurrency(gainCents)} today",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * The milestone bar: a trophy, a progress track, and the next-goal line. Always
 * shows a visible next goal; when every tier is cleared, the bar reads full and
 * the copy states the top tier is reached.
 */
@Composable
private fun MilestoneBar(state: HeroBankState) {
    val milestone = state.milestone

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = OinkGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = milestoneLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { milestone.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = OinkGold,
            trackColor = Color.White.copy(alpha = 0.25f),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

/**
 * The next-goal copy, e.g. "$127.50 -> $200 · Swole Savings", or the maxed line
 * when every tier is cleared.
 */
private fun milestoneLabel(state: HeroBankState): String {
    val milestone = state.milestone
    val nextThreshold = milestone.nextThresholdCents
    val nextTier = milestone.nextTierName
    return if (nextThreshold == null || nextTier == null) {
        val topTier = milestone.currentTierName
        if (topTier != null) "$topTier · Top tier reached" else "Top tier reached"
    } else {
        "${Formatters.formatCurrency(state.balanceCents)} -> ${Formatters.formatCurrencyCompact(nextThreshold)} · $nextTier"
    }
}

/**
 * The merged, spoken summary of the Rewards hero for TalkBack: balance, today's
 * gain, and streak. Milestone progress is omitted - the Rewards screen has its own
 * milestone track - and the "Treat yourself" CTA is a separate node, so this is a
 * summary rather than an actionable element.
 */
private fun rewardsHeroContentDescription(state: HeroBankState): String {
    val parts = mutableListOf<String>()
    parts += "${state.label}: ${Formatters.formatCurrency(state.balanceCents)}"
    if (state.dailyGainCents > 0) {
        parts += "up ${Formatters.formatCurrency(state.dailyGainCents)} today"
    }
    if (state.streak > 0) {
        parts += "${state.streak} day streak"
    }
    return parts.joinToString(separator = ", ")
}

/**
 * The merged, spoken summary of the card for TalkBack: balance, today's gain,
 * streak, and milestone progress, read as one actionable element.
 */
private fun heroContentDescription(state: HeroBankState): String {
    val parts = mutableListOf<String>()
    parts += "${state.label}: ${Formatters.formatCurrency(state.balanceCents)}"
    if (state.dailyGainCents > 0) {
        parts += "up ${Formatters.formatCurrency(state.dailyGainCents)} today"
    }
    if (state.streak > 0) {
        parts += "${state.streak} day streak"
    }
    val milestone = state.milestone
    val nextThreshold = milestone.nextThresholdCents
    val nextTier = milestone.nextTierName
    if (nextThreshold != null && nextTier != null) {
        parts += "${Formatters.formatCurrencyCompact(nextThreshold)} to reach $nextTier"
    } else {
        parts += "top milestone reached"
    }
    return parts.joinToString(separator = ", ")
}
