package com.oink.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oink.app.data.CheckIn
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTealContainer
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.utils.Formatters
import com.oink.app.utils.HabitCopy
import com.oink.app.viewmodel.MainViewModel

/**
 * History screen showing this habit's check-ins.
 *
 * This screen displays a scrollable list of past check-ins for the habit,
 * ordered by date (newest first). Each item shows the date, whether the day
 * was completed, and the balance after that check-in, above a stats summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onPrivateLocked: () -> Unit
) {
    // Leave a private habit's history for the PIN gate when the gate re-locks.
    val privateLocked by viewModel.privateLocked.collectAsStateWithLifecycle()
    LaunchedEffect(privateLocked) {
        if (privateLocked) onPrivateLocked()
    }

    val checkIns by viewModel.allCheckIns.collectAsStateWithLifecycle()

    // Check-ins newest first.
    val sortedCheckIns by remember(checkIns) {
        derivedStateOf {
            checkIns.sortedByDescending { it.date }
        }
    }

    // Calculate stats from check-ins
    val stats by remember(checkIns) {
        derivedStateOf {
            val totalDays = checkIns.size
            val completedDays = checkIns.count { it.completed }
            val missedDays = totalDays - completedDays
            val percentage = if (totalDays > 0) (completedDays * 100) / totalDays else 0
            HistoryStats(totalDays, completedDays, missedDays, percentage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats summary card
            if (checkIns.isNotEmpty()) {
                StatsCard(
                    stats = stats,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Check-in list or empty state
            AnimatedVisibility(
                visible = sortedCheckIns.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyHistoryState()
            }

            AnimatedVisibility(
                visible = sortedCheckIns.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CheckInList(checkIns = sortedCheckIns)
            }
        }
    }
}

/**
 * Data class for history statistics.
 */
private data class HistoryStats(
    val totalDays: Int,
    val completedDays: Int,
    val missedDays: Int,
    val completionRate: Int
)

/**
 * Card showing summary statistics.
 */
@Composable
private fun StatsCard(
    stats: HistoryStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(OinkPink.copy(alpha = 0.15f), OinkPink.copy(alpha = 0.25f))
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Completed days
                StatItem(
                    value = stats.completedDays.toString(),
                    label = HabitCopy.STAT_DONE_DAYS,
                    color = OinkTeal
                )

                // Missed days
                StatItem(
                    value = stats.missedDays.toString(),
                    label = "Missed\nDays",
                    color = OinkWarning
                )

                // Success rate
                StatItem(
                    value = "${stats.completionRate}%",
                    label = "Success\nRate",
                    color = OinkPink
                )
            }
        }
    }
}

/**
 * Individual stat item in the stats card.
 */
@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Empty state when no check-ins exist.
 */
@Composable
private fun EmptyHistoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No check-ins yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = HabitCopy.EMPTY_HISTORY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Scrollable list of this habit's check-ins, newest first.
 */
@Composable
private fun CheckInList(checkIns: List<CheckIn>) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = checkIns,
            key = { checkIn -> checkIn.id }
        ) { checkIn ->
            CheckInItem(checkIn = checkIn)
        }

        // Bottom spacer for nice padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Individual check-in item in the list.
 */
@Composable
private fun CheckInItem(checkIn: CheckIn) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (checkIn.completed) {
                            OinkTealContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (checkIn.completed) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (checkIn.completed) HabitCopy.CONTENT_DESC_DONE else "Missed",
                    modifier = Modifier.size(24.dp),
                    tint = if (checkIn.completed) {
                        OinkTeal
                    } else {
                        OinkWarning
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Date and status text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = Formatters.formatDateRelative(checkIn.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (checkIn.completed) HabitCopy.HISTORY_DONE else HabitCopy.HISTORY_REST,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Balance after this check-in
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = Formatters.formatCurrency(checkIn.balanceAfter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

