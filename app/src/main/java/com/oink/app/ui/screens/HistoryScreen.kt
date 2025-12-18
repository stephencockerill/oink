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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.data.CashOut
import com.oink.app.data.CheckIn
import com.oink.app.ui.theme.MoneyGreen
import com.oink.app.ui.theme.MoneyGreenDark
import com.oink.app.ui.theme.SuccessContainerLight
import com.oink.app.ui.theme.SuccessLight
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.MainViewModel

/**
 * History screen showing all check-ins.
 *
 * This screen displays a scrollable list of all past check-ins,
 * ordered by date (newest first). Each item shows the date,
 * whether the user exercised, and the balance after that check-in.
 *
 * We'll enhance this later with editing capabilities, but for now
 * it's a simple read-only list.
 */
/**
 * Represents an item in the history timeline.
 * Can be either a check-in or a cash-out.
 */
private sealed class TimelineItem {
    abstract val sortKey: Long // For sorting by date/time

    data class CheckInItem(val checkIn: CheckIn) : TimelineItem() {
        override val sortKey: Long = checkIn.date.toEpochDay() * 1_000_000 // Multiply to ensure check-ins come before same-day cash-outs
    }

    data class CashOutItem(val cashOut: CashOut) : TimelineItem() {
        override val sortKey: Long = cashOut.cashedOutAt / 86_400_000 * 1_000_000 + (cashOut.cashedOutAt % 86_400_000) / 1000
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val checkIns by viewModel.allCheckIns.collectAsStateWithLifecycle()
    val cashOuts by viewModel.allCashOuts.collectAsStateWithLifecycle()

    // Create combined timeline of check-ins and cash-outs
    val timelineItems by remember(checkIns, cashOuts) {
        derivedStateOf {
            val items = mutableListOf<TimelineItem>()
            items.addAll(checkIns.map { TimelineItem.CheckInItem(it) })
            items.addAll(cashOuts.map { TimelineItem.CashOutItem(it) })
            items.sortedByDescending { it.sortKey }
        }
    }

    // Calculate stats from check-ins
    val stats by remember(checkIns, cashOuts) {
        derivedStateOf {
            val totalDays = checkIns.size
            val exerciseDays = checkIns.count { it.didExercise }
            val missedDays = totalDays - exerciseDays
            val percentage = if (totalDays > 0) (exerciseDays * 100) / totalDays else 0
            val rewardsCount = cashOuts.size
            HistoryStats(totalDays, exerciseDays, missedDays, percentage, rewardsCount)
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
                visible = timelineItems.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyHistoryState()
            }

            AnimatedVisibility(
                visible = timelineItems.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TimelineList(items = timelineItems)
            }
        }
    }
}

/**
 * Data class for history statistics.
 */
private data class HistoryStats(
    val totalDays: Int,
    val exerciseDays: Int,
    val missedDays: Int,
    val exercisePercentage: Int,
    val rewardsCount: Int = 0
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Exercise days
            StatItem(
                value = stats.exerciseDays.toString(),
                label = "Exercise\nDays",
                color = SuccessLight
            )

            // Missed days
            StatItem(
                value = stats.missedDays.toString(),
                label = "Missed\nDays",
                color = MaterialTheme.colorScheme.error
            )

            // Success rate
            StatItem(
                value = "${stats.exercisePercentage}%",
                label = "Success\nRate",
                color = MaterialTheme.colorScheme.primary
            )

            // Rewards count (only show if there are rewards)
            if (stats.rewardsCount > 0) {
                StatItem(
                    value = stats.rewardsCount.toString(),
                    label = "Rewards\n🎁",
                    color = MoneyGreen
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
                imageVector = Icons.Default.FitnessCenter,
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
                text = "Start your fitness journey by\nlogging your first workout!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Combined timeline list showing both check-ins and cash-outs.
 */
@Composable
private fun TimelineList(items: List<TimelineItem>) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is TimelineItem.CheckInItem -> "checkin_${item.checkIn.id}"
                    is TimelineItem.CashOutItem -> "cashout_${item.cashOut.id}"
                }
            }
        ) { item ->
            when (item) {
                is TimelineItem.CheckInItem -> CheckInItem(checkIn = item.checkIn)
                is TimelineItem.CashOutItem -> CashOutHistoryItem(cashOut = item.cashOut)
            }
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
                        if (checkIn.didExercise) {
                            SuccessContainerLight
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (checkIn.didExercise) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (checkIn.didExercise) "Exercised" else "Missed",
                    modifier = Modifier.size(24.dp),
                    tint = if (checkIn.didExercise) {
                        SuccessLight
                    } else {
                        MaterialTheme.colorScheme.error
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
                    text = if (checkIn.didExercise) "Worked out 💪" else "Rest day",
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

/**
 * Cash-out item in the history timeline.
 */
@Composable
private fun CashOutHistoryItem(cashOut: CashOut) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MoneyGreen.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gift icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MoneyGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = cashOut.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = cashOut.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "🎁 Reward claimed!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoneyGreen
                )
                Text(
                    text = Formatters.formatDateFromMillis(cashOut.cashedOutAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Amount (negative since it's a cash-out)
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "-${Formatters.formatCurrency(cashOut.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MoneyGreen
                )
                Text(
                    text = "${cashOut.workoutsToEarn} workouts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

