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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import com.oink.app.data.PreferencesRepository
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTealContainer
import com.oink.app.ui.theme.OinkSuccess
import com.oink.app.ui.theme.OinkSuccessContainer
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home screen - the main screen of the app.
 *
 * This is where users see their balance, streak, and can check in
 * for the day. It's designed to be visually motivating and easy to use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRewards: () -> Unit
) {
    val balance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val todayCheckIn by viewModel.todayCheckIn.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val exercisePreview by viewModel.exercisePreview.collectAsStateWithLifecycle()
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

    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🐷 Oink",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
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
            BalanceCard(balance = balance)

            Spacer(modifier = Modifier.height(24.dp))

            // Streak and freezes display
            StreakAndFreezeDisplay(streak = streak, availableFreezes = availableFreezes)

            // Missed day freeze prompt
            missedDayForFreeze?.let { missedDate ->
                Spacer(modifier = Modifier.height(16.dp))
                FreezePromptCard(
                    missedDate = missedDate,
                    availableFreezes = availableFreezes,
                    balance = balance,
                    freezeCost = freezeCost,
                    onUseFreeze = { viewModel.useFreeze(missedDate) },
                    onDismiss = { viewModel.dismissFreezePrompt() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Check-in section
            CheckInSection(
                todayCheckIn = todayCheckIn,
                exercisePreview = exercisePreview,
                missPreview = missPreview,
                isLoading = isLoading,
                onCheckIn = { didExercise ->
                    viewModel.recordTodayCheckIn(didExercise)
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
private fun BalanceCard(balance: Double) {
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
                    text = "🐷 Piggy Bank",
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
private fun StreakAndFreezeDisplay(streak: Int, availableFreezes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Streak chip
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (streak > 0) OinkPink.copy(alpha = 0.15f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatStreakWithEmoji(streak),
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
    balance: Double,
    freezeCost: Double,
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
                    text = "Streak in danger!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OinkWarning
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You missed $formattedDate. Use a freeze to save your streak!",
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
                        Text("Need \$${freezeCost.toInt()}")
                    }
                }
            }
        }
    }
}

/**
 * Check-in section with YES/NO buttons or status display.
 */
@Composable
private fun CheckInSection(
    todayCheckIn: com.oink.app.data.CheckIn?,
    exercisePreview: Double,
    missPreview: Double,
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
            if (todayCheckIn == null) {
                // Not checked in yet - show question and buttons
                Text(
                    text = "Did you exercise today?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Preview what happens with each choice
                PreviewSection(
                    exercisePreview = exercisePreview,
                    missPreview = missPreview
                )

                Spacer(modifier = Modifier.height(24.dp))

                // YES / NO buttons - teal for success, outlined for miss
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
                // Already checked in - show status
                CheckInStatus(didExercise = todayCheckIn.didExercise)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You can update this if you change your mind",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show change button
                OutlinedButton(
                    onClick = { onCheckIn(!todayCheckIn.didExercise) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (todayCheckIn.didExercise) "Actually, I didn't exercise" else "Wait, I did exercise!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Preview showing what balance would be for each choice.
 * Uses brand colors: teal for gains, amber for losses.
 */
@Composable
private fun PreviewSection(
    exercisePreview: Double,
    missPreview: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Exercise preview - teal to match YES button
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
                    text = "Exercise",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = Formatters.formatCurrency(exercisePreview),
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
 * Status display for when user has already checked in.
 * Teal for exercise, subtle gray for rest day.
 */
@Composable
private fun CheckInStatus(didExercise: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (didExercise) {
            OinkTealContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "status_bg_color"
    )

    val contentColor = if (didExercise) {
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
                    imageVector = if (didExercise) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (didExercise) "Crushed it! 💪" else "Rest day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (didExercise) "You exercised today" else "Tomorrow's another chance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

