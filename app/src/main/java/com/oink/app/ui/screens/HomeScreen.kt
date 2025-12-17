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
import androidx.compose.runtime.collectAsState
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
import com.oink.app.ui.theme.MoneyGreen
import com.oink.app.ui.theme.MoneyGreenDark
import com.oink.app.ui.theme.SuccessContainerDark
import com.oink.app.ui.theme.SuccessContainerLight
import com.oink.app.ui.theme.SuccessDark
import com.oink.app.ui.theme.SuccessLight
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
    val balance by viewModel.currentBalance.collectAsState()
    val todayCheckIn by viewModel.todayCheckIn.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val exercisePreview by viewModel.exercisePreview.collectAsState()
    val missPreview by viewModel.missPreview.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val availableFreezes by viewModel.availableFreezes.collectAsState()
    val missedDayForFreeze by viewModel.missedDayForFreeze.collectAsState()
    val freezeCost by viewModel.freezeCost.collectAsState()

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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🐷 Piggy Bank",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Streak and freeze display.
 */
@Composable
private fun StreakAndFreezeDisplay(streak: Int, availableFreezes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Streak
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (streak > 0) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = Formatters.formatStreakWithEmoji(streak),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (streak > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }

        // Freeze count
        if (availableFreezes > 0) {
            Spacer(modifier = Modifier.width(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$availableFreezes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Card prompting user to use a freeze for a missed day.
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Streak in danger!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You missed $formattedDate. Use a freeze to save your streak!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Let it go")
                }

                // Use freeze button
                if (hasFreeze && canAffordFreeze) {
                    Button(
                        onClick = onUseFreeze,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use Freeze (\$${freezeCost.toInt()})")
                    }
                } else if (!hasFreeze) {
                    Button(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Get Freeze")
                    }
                } else {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
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

                // YES / NO buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // YES button
                    Button(
                        onClick = { onCheckIn(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessLight
                        ),
                        shape = RoundedCornerShape(16.dp)
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

                    // NO button
                    OutlinedButton(
                        onClick = { onCheckIn(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
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
        // Exercise preview
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
                    tint = SuccessLight
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
                fontWeight = FontWeight.SemiBold,
                color = SuccessLight
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )

        // Miss preview
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
                    tint = MaterialTheme.colorScheme.error
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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Status display for when user has already checked in.
 */
@Composable
private fun CheckInStatus(didExercise: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (didExercise) {
            SuccessContainerLight
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        label = "status_bg_color"
    )

    val contentColor = if (didExercise) {
        SuccessLight
    } else {
        MaterialTheme.colorScheme.error
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
                    text = if (didExercise) "Great job!" else "Maybe tomorrow",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = if (didExercise) "You exercised today 💪" else "Rest day recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

