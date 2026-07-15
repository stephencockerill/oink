package com.oink.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oink.app.data.CashOut
import com.oink.app.data.RewardCategories
import com.oink.app.ui.components.ConfettiBurst
import com.oink.app.ui.components.OinkMascot
import com.oink.app.ui.components.RewardsHeroCard
import com.oink.app.ui.theme.OinkError
import com.oink.app.ui.theme.OinkGold
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkSpacing
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTealContainer
import com.oink.app.ui.util.rememberReduceMotion
import com.oink.app.utils.Formatters
import com.oink.app.utils.HabitCopy
import com.oink.app.utils.MascotState
import com.oink.app.utils.MilestoneTier
import com.oink.app.utils.MilestoneTierStatus
import com.oink.app.utils.RewardTimelineEntry
import com.oink.app.viewmodel.PinPromptState
import com.oink.app.viewmodel.PrivateFundsAccess
import com.oink.app.viewmodel.PrivateViewModel
import com.oink.app.viewmodel.RewardsViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rewards screen - where you CASH IN your hard-earned piggy bank funds!
 *
 * Structured as a story rather than a flat list: a compact hero (balance, mascot,
 * "Treat yourself" CTA), a horizontal milestone track (done / active / locked
 * tiers), expressive stat tiles, and a vertical highlight-reel timeline that
 * interleaves earned milestone trophies with the user's cash-outs.
 *
 * The whole vibe: "You EARNED this through discipline and effort. Treat yourself!"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
    onNavigateBack: () -> Unit
) {
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val balance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val cashOuts by viewModel.allCashOuts.collectAsStateWithLifecycle()
    val milestoneTrack by viewModel.milestoneTrack.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val totalCompletedDays by viewModel.totalCompletedDays.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cashOutSuccess by viewModel.cashOutSuccess.collectAsStateWithLifecycle()
    val selectedCashOut by viewModel.selectedCashOut.collectAsStateWithLifecycle()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsStateWithLifecycle()
    val privateFundsAccess by viewModel.privateFundsAccess.collectAsStateWithLifecycle()
    val pinPrompt by viewModel.pinPrompt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCashOutSheet by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var celebrationCashOut by remember { mutableStateOf<CashOut?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle successful cash-out with celebration!
    LaunchedEffect(cashOutSuccess) {
        cashOutSuccess?.let { cashOut ->
            showCashOutSheet = false
            celebrationCashOut = cashOut
            showCelebration = true
            // Auto-dismiss celebration after a few seconds
            delay(4000)
            showCelebration = false
            celebrationCashOut = null
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    PrivateFundsAction(
                        access = privateFundsAccess,
                        onUnlockClick = {
                            viewModel.clearPinError()
                            showUnlockDialog = true
                        }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero: balance, mascot, "Treat yourself" CTA.
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    RewardsHeroCard(
                        state = heroState,
                        onTreatYourself = { showCashOutSheet = true }
                    )
                }

                // Milestone track: quarter pounder -> swole savings.
                item {
                    MilestoneTrack(tiers = milestoneTrack)
                }

                // Stat tiles.
                item {
                    StatsRow(
                        rewardCount = cashOuts.size,
                        totalCompletedDays = totalCompletedDays
                    )
                }

                if (cashOuts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Your highlight reel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    itemsIndexed(
                        items = timeline,
                        key = { _, entry -> entry.timelineKey() },
                        contentType = { _, entry -> entry.timelineContentType() }
                    ) { index, entry ->
                        TimelineEntryRow(
                            entry = entry,
                            isFirst = index == 0,
                            isLast = index == timeline.lastIndex,
                            onCashoutClick = { cashOut ->
                                viewModel.selectCashOut(cashOut)
                                showEditSheet = true
                            }
                        )
                    }
                } else {
                    item {
                        EmptyRewardsState()
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Celebration overlay
            if (showCelebration && celebrationCashOut != null) {
                CelebrationOverlay(
                    cashOut = celebrationCashOut!!,
                    onDismiss = {
                        showCelebration = false
                        celebrationCashOut = null
                        viewModel.clearSuccess()
                    }
                )
            }
        }
    }

    // Cash Out Bottom Sheet
    if (showCashOutSheet) {
        CashOutBottomSheet(
            balance = balance,
            isLoading = isLoading,
            onDismiss = { showCashOutSheet = false },
            onCashOut = { name, amount, emoji ->
                viewModel.cashOut(name, amount, emoji)
            }
        )
    }

    // Edit Reward Bottom Sheet
    if (showEditSheet && selectedCashOut != null) {
        EditRewardBottomSheet(
            cashOut = selectedCashOut!!,
            currentBalance = balance,
            isLoading = isLoading,
            onDismiss = {
                showEditSheet = false
                viewModel.clearSelection()
            },
            onSave = { name, amount, emoji ->
                viewModel.updateSelectedCashOut(name, amount, emoji)
                showEditSheet = false
            },
            onDelete = {
                viewModel.requestDelete()
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && selectedCashOut != null) {
        DeleteRewardConfirmationDialog(
            cashOut = selectedCashOut!!,
            onConfirm = {
                viewModel.deleteSelectedCashOut()
                showEditSheet = false
            },
            onDismiss = {
                viewModel.cancelDelete()
            }
        )
    }

    // Private-funds unlock dialog. Only meaningful while still locked; a
    // successful unlock flips [privateFundsAccess] to Unlocked, dismissing it.
    if (showUnlockDialog && privateFundsAccess is PrivateFundsAccess.Locked) {
        PrivateFundsUnlockDialog(
            prompt = pinPrompt,
            onSubmitPin = viewModel::submitPin,
            onDismiss = {
                showUnlockDialog = false
                viewModel.clearPinError()
            }
        )
    }
}

/**
 * Discreet private-funds control for the top app bar.
 *
 * When a PIN is set but the area is locked, this is a small lock icon that opens
 * the PIN prompt; when unlocked, an unobtrusive open-lock indicator marks that
 * private funds are included. It shows nothing when no PIN is configured, so its
 * mere presence reveals only that a PIN exists.
 */
@Composable
private fun PrivateFundsAction(
    access: PrivateFundsAccess,
    onUnlockClick: () -> Unit
) {
    when (access) {
        PrivateFundsAccess.Loading, PrivateFundsAccess.NoPin -> Unit
        PrivateFundsAccess.Locked -> {
            IconButton(onClick = onUnlockClick) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock private funds",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        PrivateFundsAccess.Unlocked -> {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = "Private funds included",
                tint = OinkTeal.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

/**
 * PIN prompt for including private funds. Reuses the shared [PinField]; a wrong
 * attempt shows an error and a rate-limit lockout disables entry and counts down
 * locally, re-enabling input when it elapses (mirrors the private area gate).
 */
@Composable
private fun PrivateFundsUnlockDialog(
    prompt: PinPromptState,
    onSubmitPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var secondsLeft by remember(prompt.remainingLockoutSeconds, prompt.isLockedOut) {
        mutableIntStateOf(if (prompt.isLockedOut) prompt.remainingLockoutSeconds else 0)
    }

    LaunchedEffect(prompt.isLockedOut, prompt.remainingLockoutSeconds) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    val lockedOut = prompt.isLockedOut && secondsLeft > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = OinkTeal)
        },
        title = { Text("Enter PIN") },
        text = {
            Column {
                PinField(
                    value = pin,
                    onValueChange = { pin = it.digitsOnly() },
                    label = "PIN",
                    enabled = !lockedOut,
                    isError = prompt.showError
                )
                when {
                    lockedOut -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Too many attempts. Try again in ${secondsLeft}s.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OinkError
                        )
                    }
                    prompt.showError -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Incorrect PIN. ${prompt.remainingAttempts} attempts left.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OinkError
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitPin(pin)
                    pin = ""
                },
                enabled = !lockedOut && PrivateViewModel.isValidPin(pin),
                colors = ButtonDefaults.buttonColors(containerColor = OinkTeal)
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =============================================================================
// Milestone track
// =============================================================================

/**
 * The horizontal milestone track: the four financial tiers, each showing its
 * done / active / locked state with a gold accent on the ones already unlocked.
 * Scrolls horizontally so long tier names never truncate.
 */
@Composable
private fun MilestoneTrack(tiers: List<MilestoneTier>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tiers.forEachIndexed { index, tier ->
            MilestoneNode(tier = tier)
            if (index != tiers.lastIndex) {
                MilestoneConnector(reached = tier.status == MilestoneTierStatus.DONE)
            }
        }
    }
}

/**
 * A short connector between two milestone nodes, aligned to the badge center. Gold
 * once the tier to its left has been reached, muted otherwise.
 */
@Composable
private fun MilestoneConnector(reached: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = 20.dp)
            .width(20.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (reached) OinkGold
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
    )
}

/**
 * One tier in the milestone track: a status badge, the tier name, and its dollar
 * threshold. Done tiers read gold, the active tier reads in the brand primary, and
 * locked tiers are muted.
 */
@Composable
private fun MilestoneNode(tier: MilestoneTier) {
    val icon: ImageVector
    val badgeColor: Color
    val iconTint: Color
    val accent: Color
    when (tier.status) {
        MilestoneTierStatus.DONE -> {
            icon = Icons.Default.EmojiEvents
            badgeColor = OinkGold
            iconTint = Color.White
            accent = OinkGold
        }
        MilestoneTierStatus.ACTIVE -> {
            icon = Icons.Default.TrackChanges
            badgeColor = MaterialTheme.colorScheme.primaryContainer
            iconTint = MaterialTheme.colorScheme.primary
            accent = MaterialTheme.colorScheme.primary
        }
        MilestoneTierStatus.LOCKED -> {
            icon = Icons.Default.Lock
            badgeColor = MaterialTheme.colorScheme.surfaceVariant
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            accent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
    }

    val stateWord = when (tier.status) {
        MilestoneTierStatus.DONE -> "reached"
        MilestoneTierStatus.ACTIVE -> "next goal"
        MilestoneTierStatus.LOCKED -> "locked"
    }

    Column(
        modifier = Modifier
            .width(96.dp)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "${tier.name}, ${Formatters.formatCurrencyCompact(tier.thresholdCents)}, $stateWord"
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .then(
                    if (tier.status == MilestoneTierStatus.ACTIVE) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = Formatters.formatCurrencyCompact(tier.thresholdCents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = tier.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (tier.status == MilestoneTierStatus.LOCKED) 0.5f else 0.85f
            )
        )
    }
}

// =============================================================================
// Stat tiles
// =============================================================================

@Composable
private fun StatsRow(
    rewardCount: Int,
    totalCompletedDays: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CardGiftcard,
            tint = OinkTeal,
            label = "Rewards",
            value = rewardCount.toString()
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarMonth,
            tint = OinkGold,
            label = HabitCopy.STAT_DAYS,
            value = totalCompletedDays.toString()
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$value $label"
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// =============================================================================
// Timeline
// =============================================================================

/** Stable LazyColumn key per timeline entry. */
private fun RewardTimelineEntry.timelineKey(): String = when (this) {
    is RewardTimelineEntry.Trophy -> "trophy-${tier.thresholdCents}"
    is RewardTimelineEntry.Cashout -> "cashout-${cashOut.id}"
}

/** LazyColumn content type so trophy and cash-out rows recycle separately. */
private fun RewardTimelineEntry.timelineContentType(): String = when (this) {
    is RewardTimelineEntry.Trophy -> "trophy"
    is RewardTimelineEntry.Cashout -> "cashout"
}

@Composable
private fun TimelineEntryRow(
    entry: RewardTimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onCashoutClick: (CashOut) -> Unit
) {
    when (entry) {
        is RewardTimelineEntry.Trophy -> TimelineRow(
            isFirst = isFirst,
            isLast = isLast,
            marker = { TrophyMarker() }
        ) {
            TrophyContent(tier = entry.tier)
        }
        is RewardTimelineEntry.Cashout -> TimelineRow(
            isFirst = isFirst,
            isLast = isLast,
            marker = { CashoutMarker(emoji = entry.cashOut.emoji) }
        ) {
            CashoutContent(
                cashOut = entry.cashOut,
                onClick = { onCashoutClick(entry.cashOut) }
            )
        }
    }
}

/**
 * A single timeline row: a fixed-width rail on the left carrying the connecting
 * line and this entry's marker, and the entry's content filling the rest. The rail
 * line skips above the first marker and below the last so the reel reads as one
 * continuous thread.
 */
@Composable
private fun TimelineRow(
    isFirst: Boolean,
    isLast: Boolean,
    marker: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val railColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .drawBehind {
                    val centerX = size.width / 2f
                    // Marker: 4dp top padding + 40dp circle -> center at 24dp.
                    val markerCenterY = 24.dp.toPx()
                    val top = if (isFirst) markerCenterY else 0f
                    val bottom = if (isLast) markerCenterY else size.height
                    drawLine(
                        color = railColor,
                        start = Offset(centerX, top),
                        end = Offset(centerX, bottom),
                        strokeWidth = 2.dp.toPx()
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.padding(top = 4.dp)) {
                marker()
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TrophyMarker() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(OinkGold),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CashoutMarker(emoji: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
private fun TrophyContent(tier: MilestoneTier) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Milestone reached: ${tier.name}, ${Formatters.formatCurrencyCompact(tier.thresholdCents)}"
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = OinkGold.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tier.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Milestone reached · ${Formatters.formatCurrencyCompact(tier.thresholdCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = OinkGold
            )
        }
    }
}

@Composable
private fun CashoutContent(
    cashOut: CashOut,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = "Edit reward"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cashOut.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(cashOut.cashedOutAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${HabitCopy.dayCount(cashOut.daysToEarn)} earned this!",
                    style = MaterialTheme.typography.bodySmall,
                    color = OinkTeal
                )
            }

            Text(
                text = Formatters.formatCurrency(cashOut.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OinkTeal
            )
        }
    }
}

@Composable
private fun EmptyRewardsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OinkMascot(
            state = MascotState.SLEEPING,
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No rewards yet!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Show up consistently to build up your piggy bank, then treat yourself to something nice!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CashOutBottomSheet(
    balance: Long,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCashOut: (name: String, amount: Long, emoji: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎁") }

    val amount = Formatters.parseDollarsToCents(amountText) ?: 0L
    val isValid = name.isNotBlank() && amount > 0 && amount <= balance

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Treat Yourself!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "You've earned it through hard work!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Emoji picker
            Text(
                text = "Pick an emoji",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RewardCategories.defaultEmojis.forEach { emoji ->
                    EmojiButton(
                        emoji = emoji,
                        isSelected = emoji == selectedEmoji,
                        onClick = { selectedEmoji = emoji }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What are you treating yourself to?") },
                placeholder = { Text("e.g., New darts, Guitar strings") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                prefix = { Text("$") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    Text("Available: ${Formatters.formatCurrency(balance)}")
                },
                isError = amount > balance
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cash out button
            Button(
                onClick = { onCashOut(name, amount, selectedEmoji) },
                enabled = isValid && !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OinkTeal
                )
            ) {
                if (isLoading) {
                    Text("Processing...")
                } else {
                    Icon(Icons.Default.Celebration, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cash Out ${if (amount > 0) Formatters.formatCurrency(amount) else ""}")
                }
            }
        }
    }
}

@Composable
private fun EmojiButton(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "emoji_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
private fun CelebrationOverlay(
    cashOut: CashOut,
    onDismiss: () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebration_scale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The happy pig is the celebratory focal point; a Compose-native
                    // confetti burst rains over the dialog (below), honoring
                    // reduce-motion.
                    OinkMascot(
                        state = MascotState.HAPPY,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "YOU EARNED IT!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = OinkTeal
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Reward details
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        OinkTeal.copy(alpha = 0.3f),
                                        OinkTeal.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = cashOut.emoji, fontSize = 40.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = cashOut.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Formatters.formatCurrency(cashOut.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OinkTeal
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${HabitCopy.dayCount(cashOut.daysToEarn)} made this possible!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OinkTeal
                        )
                    ) {
                        Text("Awesome!")
                    }
                }
            }

            // Compose-native confetti rains over the reward card; skipped under
            // reduce-motion so the dialog simply appears.
            if (!reduceMotion) {
                ConfettiBurst(modifier = Modifier.matchParentSize())
            }
        }
    }
}

/**
 * Bottom sheet for editing an existing reward.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditRewardBottomSheet(
    cashOut: CashOut,
    currentBalance: Long,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Long, emoji: String) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(cashOut.name) }
    var amountText by remember { mutableStateOf(Formatters.centsToInput(cashOut.amount)) }
    var selectedEmoji by remember { mutableStateOf(cashOut.emoji) }

    val amount = Formatters.parseDollarsToCents(amountText) ?: 0L
    // Can increase amount up to: current balance + original amount (what we'd get back if we deleted it)
    val maxAmount = currentBalance + cashOut.amount
    val isValid = name.isNotBlank() && amount > 0 && amount <= maxAmount
    val hasChanges = name != cashOut.name || amount != cashOut.amount || selectedEmoji != cashOut.emoji

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Reward",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Emoji picker
            Text(
                text = "Pick an emoji",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RewardCategories.defaultEmojis.forEach { emoji ->
                    EmojiButton(
                        emoji = emoji,
                        isSelected = emoji == selectedEmoji,
                        onClick = { selectedEmoji = emoji }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What did you treat yourself to?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                prefix = { Text("$") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    if (amount > cashOut.amount) {
                        Text("Increasing will reduce balance by ${Formatters.formatCurrency(amount - cashOut.amount)}")
                    } else if (amount < cashOut.amount) {
                        Text("Decreasing will add ${Formatters.formatCurrency(cashOut.amount - amount)} back to balance")
                    }
                },
                isError = amount > maxAmount
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(name, amount, selectedEmoji) },
                    enabled = isValid && hasChanges && !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OinkTeal
                    )
                ) {
                    if (isLoading) {
                        Text("Saving...")
                    } else {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog for deleting a reward.
 */
@Composable
private fun DeleteRewardConfirmationDialog(
    cashOut: CashOut,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Delete Reward?")
        },
        text = {
            Column {
                Text(
                    "Are you sure you want to delete \"${cashOut.name}\"?"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${Formatters.formatCurrency(cashOut.amount)} will be added back to your piggy bank!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OinkTeal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep It")
            }
        }
    )
}
