package com.oink.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.ui.components.TodayCheckInControl
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.HabitCardState
import com.oink.app.viewmodel.HabitListViewModel

/**
 * Home screen - the scrollable list of habits.
 *
 * Each card shows a habit's emoji, name, streak/freeze meta, and its own
 * spendable balance. The overall-bank card at the top shows the shared piggy
 * bank total (the pot across every public habit) and opens the rewards screen.
 * Tapping a habit card opens that habit's detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onHabitClick: (Long) -> Unit,
    onNavigateToRewards: () -> Unit,
    onPrivateClick: () -> Unit,
    onAddHabit: () -> Unit
) {
    val habitCards by viewModel.habitCards.collectAsStateWithLifecycle()
    val overallBank by viewModel.overallBank.collectAsStateWithLifecycle()

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddHabit,
                containerColor = OinkPink,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add habit"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "overall-bank", contentType = "overall-bank") {
                OverallBankCard(
                    overallBank = overallBank,
                    onClick = onNavigateToRewards
                )
            }

            item(key = "private-tile", contentType = "private-tile") {
                PrivateTile(onClick = onPrivateClick)
            }

            items(
                items = habitCards,
                key = { it.id },
                contentType = { "habit-card" }
            ) { card ->
                HabitCard(
                    card = card,
                    onClick = { onHabitClick(card.id) },
                    onCheckIn = { completed -> viewModel.recordCheckIn(card.id, completed) }
                )
            }
        }
    }
}

/**
 * The shared piggy bank total, styled like the detail balance card. Tapping it
 * opens the rewards screen, where the pot is cashed out.
 */
@Composable
private fun OverallBankCard(
    overallBank: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(OinkPink, OinkPinkDark)
                    )
                )
                .padding(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🐷 Piggy Bank",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = "Rewards",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = Formatters.formatCurrency(overallBank),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                Text(
                    text = "Shared across all your habits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * The always-present entry point to the private area.
 *
 * Fixed in the list regardless of whether any private habits exist and
 * regardless of the lock state, and rendered identically every time, so its
 * presence leaks nothing about whether the user hides anything. Tapping it
 * opens the PIN gate.
 */
@Composable
private fun PrivateTile(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OinkTeal.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = OinkTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Private",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * A single habit card: emoji, name, streak/freeze meta, this habit's own
 * spendable balance, and an inline quick check-in control. Tapping the card body
 * opens the habit detail; tapping the check-in control logs today (and never
 * navigates - see [TodayCheckInControl]).
 */
@Composable
private fun HabitCard(
    card: HabitCardState,
    onClick: () -> Unit,
    onCheckIn: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OinkPink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = card.emoji,
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Name + streak/freeze meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                HabitMeta(streak = card.streak, availableFreezes = card.availableFreezes)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Per-habit spendable balance (visual hierarchy #1).
            Text(
                text = Formatters.formatCurrency(card.spendable),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OinkTeal
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Inline quick check-in - logs today without leaving the list.
            TodayCheckInControl(
                todayCompleted = card.todayCompleted,
                onCheckIn = onCheckIn
            )
        }
    }
}

/**
 * Streak text plus an optional freeze count, matching the detail screen's chips.
 */
@Composable
private fun HabitMeta(streak: Int, availableFreezes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Formatters.formatStreakWithEmoji(streak),
            style = MaterialTheme.typography.bodyMedium,
            color = if (streak > 0) OinkPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        if (availableFreezes > 0) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = OinkTeal
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "$availableFreezes",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = OinkTeal
            )
        }
    }
}
