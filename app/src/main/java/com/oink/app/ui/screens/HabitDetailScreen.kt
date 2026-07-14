package com.oink.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.data.HabitType
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTealContainer
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.utils.Formatters
import com.oink.app.utils.HabitCopy
import com.oink.app.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Habit detail screen - the per-habit view.
 *
 * Reached by tapping a card on the home list. Shows this habit's balance,
 * streak, and check-in controls. The whole screen is driven by a [MainViewModel]
 * scoped to the route's `{habitId}`, so every balance, streak, and freeze action
 * targets exactly this habit and never leaks into another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRewards: () -> Unit,
    onPrivateLocked: () -> Unit
) {
    // A private habit must not stay visible once the gate re-locks (e.g. after
    // backgrounding). Leave the private subtree for the PIN gate the moment it
    // does. Public habits never trip this.
    val privateLocked by viewModel.privateLocked.collectAsStateWithLifecycle()
    LaunchedEffect(privateLocked) {
        if (privateLocked) onPrivateLocked()
    }

    val habitName by viewModel.habitName.collectAsStateWithLifecycle()
    val habitEmoji by viewModel.habitEmoji.collectAsStateWithLifecycle()
    val habitType by viewModel.habitType.collectAsStateWithLifecycle()
    val balance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val todayCheckIn by viewModel.todayCheckIn.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val completedPreview by viewModel.completedPreview.collectAsStateWithLifecycle()
    val missPreview by viewModel.missPreview.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val availableFreezes by viewModel.availableFreezes.collectAsStateWithLifecycle()
    val missedDayForFreeze by viewModel.missedDayForFreeze.collectAsStateWithLifecycle()
    val freezeCost by viewModel.freezeCost.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Title falls back to the brand mark until the habit row loads.
    val title = if (habitName.isBlank()) "🐷 Oink" else "$habitEmoji $habitName".trim()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRewards) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Rewards"
                        )
                    }
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "View Calendar"
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "View History"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Balance Card
            BalanceCard(balance = balance, habitType = habitType)

            Spacer(modifier = Modifier.height(24.dp))

            // Streak and freezes display
            StreakAndFreezeDisplay(
                streak = streak,
                availableFreezes = availableFreezes,
                habitType = habitType
            )

            // Missed day freeze prompt
            missedDayForFreeze?.let { missedDate ->
                Spacer(modifier = Modifier.height(16.dp))
                FreezePromptCard(
                    missedDate = missedDate,
                    availableFreezes = availableFreezes,
                    balance = balance,
                    freezeCost = freezeCost,
                    habitType = habitType,
                    onUseFreeze = { viewModel.useFreeze(missedDate) },
                    onDismiss = { viewModel.dismissFreezePrompt() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Check-in section
            CheckInSection(
                todayCheckIn = todayCheckIn,
                completedPreview = completedPreview,
                missPreview = missPreview,
                streak = streak,
                habitType = habitType,
                isLoading = isLoading,
                onCheckIn = { didSucceed ->
                    viewModel.recordTodayCheckIn(didSucceed)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Animated balance display card.
 *
 * Uses a coral → magenta gradient for visual impact with
 * white text for maximum contrast and readability.
 */
@Composable
private fun BalanceCard(balance: Long, habitType: HabitType) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "balance_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            OinkPink,              // Coral pink at top
                            OinkPinkDark           // Deeper pink at bottom
                        )
                    )
                )
                .padding(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = HabitCopy.balanceLabel(habitType),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = balance,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f))
                            .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "balance_animation"
                ) { targetBalance ->
                    Text(
                        text = Formatters.formatCurrency(targetBalance),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Streak and freeze display - styled as chips for visual punch.
 */
@Composable
private fun StreakAndFreezeDisplay(streak: Int, availableFreezes: Int, habitType: HabitType) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Streak chip. A quit habit's streak is its run of clean days, so it is
        // labelled accordingly.
        val streakText = when (habitType) {
            HabitType.BUILD -> Formatters.formatStreakWithEmoji(streak)
            HabitType.QUIT ->
                if (streak == 0) "No clean days yet"
                else "${Formatters.formatStreakWithEmoji(streak)} clean"
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (streak > 0) OinkPink.copy(alpha = 0.15f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = streakText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (streak > 0) OinkPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Freeze count chip
        if (availableFreezes > 0) {
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OinkTeal.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = OinkTeal
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$availableFreezes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OinkTeal
                    )
                }
            }
        }
    }
}

/**
 * Card prompting user to use a freeze for a missed day.
 * Uses amber/warning colors - not punishing, just informative.
 */
@Composable
private fun FreezePromptCard(
    missedDate: LocalDate,
    availableFreezes: Int,
    balance: Long,
    freezeCost: Long,
    habitType: HabitType,
    onUseFreeze: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
    val formattedDate = if (missedDate == LocalDate.now().minusDays(1)) {
        "Yesterday"
    } else {
        missedDate.format(dateFormatter)
    }

    val canAffordFreeze = balance >= freezeCost
    val hasFreeze = availableFreezes > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = OinkWarning.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = HabitCopy.freezePromptTitle(habitType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OinkWarning
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = HabitCopy.freezePromptBody(habitType, formattedDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Let it go")
                }

                // Use freeze button - teal, action-oriented
                if (hasFreeze && canAffordFreeze) {
                    Button(
                        onClick = onUseFreeze,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OinkTeal
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use Freeze")
                    }
                } else if (!hasFreeze) {
                    Button(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OinkPink
                        )
                    ) {
                        Text("Get Freeze")
                    }
                } else {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = false
                    ) {
                        Text("Need ${Formatters.formatCurrency(freezeCost)}")
                    }
                }
            }
        }
    }
}

/**
 * Check-in section.
 *
 * Branches on [habitType]: a build habit asks "did you do it?" with YES/NO, while
 * a quit habit's day is passively clean and the one affirmative control is a
 * destructive "I slipped" - success is silent (see docs/negative-habits.md).
 */
@Composable
private fun CheckInSection(
    todayCheckIn: com.oink.app.data.CheckIn?,
    completedPreview: Long,
    missPreview: Long,
    streak: Int,
    habitType: HabitType,
    isLoading: Boolean,
    onCheckIn: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (habitType) {
                HabitType.BUILD -> BuildCheckInContent(
                    todayCheckIn = todayCheckIn,
                    completedPreview = completedPreview,
                    missPreview = missPreview,
                    isLoading = isLoading,
                    onCheckIn = onCheckIn
                )
                HabitType.QUIT -> QuitCheckInContent(
                    todayCheckIn = todayCheckIn,
                    streak = streak,
                    isLoading = isLoading,
                    onCheckIn = onCheckIn
                )
            }
        }
    }
}

/**
 * Build-habit check-in content: prompt + YES/NO, or the logged status with an
 * undo. This is the original check-in behavior, unchanged.
 */
@Composable
private fun BuildCheckInContent(
    todayCheckIn: com.oink.app.data.CheckIn?,
    completedPreview: Long,
    missPreview: Long,
    isLoading: Boolean,
    onCheckIn: (Boolean) -> Unit
) {
    if (todayCheckIn == null) {
        Text(
            text = HabitCopy.CHECK_IN_PROMPT,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        PreviewSection(
            completedPreview = completedPreview,
            missPreview = missPreview
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // YES button - teal, prominent, encouraging!
            Button(
                onClick = { onCheckIn(true) },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OinkTeal
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "YES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // NO button - subtle, not punishing
            OutlinedButton(
                onClick = { onCheckIn(false) },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = OinkWarning
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        CheckInStatus(didSucceed = todayCheckIn.didSucceed, habitType = HabitType.BUILD)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can update this if you change your mind",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onCheckIn(!todayCheckIn.didSucceed) },
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (todayCheckIn.didSucceed) HabitCopy.UNDO_DONE else HabitCopy.UNDO_REST,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Quit-habit check-in content.
 *
 * While the day is clean-so-far (no check-in) the state is a passive win: no
 * affirmative control, only the destructive "I slipped". Once a slip is logged,
 * the status shows with an empathetic recovery line and an undo; a day marked
 * clean can still be corrected to a slip.
 */
@Composable
private fun QuitCheckInContent(
    todayCheckIn: com.oink.app.data.CheckIn?,
    streak: Int,
    isLoading: Boolean,
    onCheckIn: (Boolean) -> Unit
) {
    when {
        todayCheckIn == null -> {
            Text(
                text = HabitCopy.STAYING_CLEAN,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (streak > 0) {
                    "You've stayed clean ${HabitCopy.dayCount(streak)}. Today banks automatically - you only log a slip."
                } else {
                    "Every clean day banks your reward automatically - you only log a slip."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            SlipButton(isLoading = isLoading, onSlip = { onCheckIn(false) })
        }

        !todayCheckIn.didSucceed -> {
            // A slip is logged today. Show it empathetically and offer an undo.
            CheckInStatus(didSucceed = false, habitType = HabitType.QUIT)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { onCheckIn(true) },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = HabitCopy.UNDO_SLIP,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        else -> {
            // Today has been marked clean (e.g. via undo). Still correctable.
            CheckInStatus(didSucceed = true, habitType = HabitType.QUIT)

            Spacer(modifier = Modifier.height(16.dp))

            SlipButton(isLoading = isLoading, onSlip = { onCheckIn(false) })
        }
    }
}

/**
 * The destructive "I slipped" control - a quit habit's only affirmative action.
 */
@Composable
private fun SlipButton(isLoading: Boolean, onSlip: () -> Unit) {
    OutlinedButton(
        onClick = onSlip,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = HabitCopy.SLIP_ACTION,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Preview showing what balance would be for each choice.
 * Uses brand colors: teal for gains, amber for losses.
 */
@Composable
private fun PreviewSection(
    completedPreview: Long,
    missPreview: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Completed preview - teal to match YES button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = OinkTeal
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = HabitCopy.PREVIEW_DONE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = Formatters.formatCurrency(completedPreview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OinkTeal
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )

        // Miss preview - amber (not harsh red)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = OinkWarning
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Miss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = Formatters.formatCurrency(missPreview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OinkWarning
            )
        }
    }
}

/**
 * Status display for when the user has already logged today.
 * Teal for the positive outcome, subtle gray for the negative one; wording
 * branches on [habitType] (done/off day vs stayed clean/slipped).
 */
@Composable
private fun CheckInStatus(didSucceed: Boolean, habitType: HabitType) {
    val backgroundColor by animateColorAsState(
        targetValue = if (didSucceed) {
            OinkTealContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "status_bg_color"
    )

    val contentColor = if (didSucceed) {
        OinkTeal
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (didSucceed) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (didSucceed) HabitCopy.successStatus(habitType) else HabitCopy.failureStatus(habitType),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (didSucceed) HabitCopy.successSubtitle(habitType) else HabitCopy.failureSubtitle(habitType),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}
