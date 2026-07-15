package com.oink.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.oink.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.data.HabitType
import com.oink.app.ui.components.HeroBankCard
import com.oink.app.ui.components.OinkMascot
import com.oink.app.ui.components.TodayCheckInControl
import com.oink.app.utils.MascotState
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTheme
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.HabitCardState
import com.oink.app.viewmodel.HabitListViewModel
import com.oink.app.viewmodel.HomeListState

/**
 * Home screen - the scrollable list of habits.
 *
 * Each card shows a habit's emoji, name, streak/freeze meta, and its own
 * spendable balance. The overall-bank card at the top shows the shared piggy
 * bank total (the pot across every public habit) and opens the rewards screen.
 * Tapping a habit card opens that habit's detail.
 *
 * With no public habits the overall-bank card and cards are replaced by a
 * no-habit hero that invites adding the first habit; the muted lock entry point
 * pinned at the bottom stays put so private-only users keep a way in. See
 * [HomeListState].
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
    val heroState by viewModel.heroState.collectAsStateWithLifecycle()
    val homeListState by viewModel.homeListState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(R.drawable.ic_oink_logo),
                        contentDescription = "Oink",
                        modifier = Modifier.size(40.dp)
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
        },
        bottomBar = {
            PrivateEntryPoint(onClick = onPrivateClick)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (homeListState) {
                // The habit set has not resolved yet: render neither the list nor
                // the empty state so a populated database never flashes empty.
                HomeListState.Loading -> Unit

                // No public habits: lead with the empty-state hero and its primary
                // action and hide the meaningless overall-bank card. The private
                // entry point lives in the Scaffold's bottomBar, so private-only
                // users keep a way in.
                HomeListState.Empty -> {
                    item(key = "empty-state", contentType = "empty-state") {
                        EmptyHome(onAddHabit = onAddHabit)
                    }
                }

                HomeListState.HasHabits -> {
                    item(key = "overall-bank", contentType = "overall-bank") {
                        HeroBankCard(
                            state = heroState,
                            onClick = onNavigateToRewards
                        )
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
    }
}

/**
 * The no-habit home state: a friendly hero that frames adding a habit as the way
 * to start earning, plus a prominent primary action. The private entry point sits
 * in the Scaffold's bottomBar, so a user whose only habits are private stays
 * reachable.
 */
@Composable
private fun EmptyHome(onAddHabit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(OinkPink.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            OinkMascot(
                state = MascotState.SLEEPING,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No habits yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add your first habit and start earning real cash every time you show up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddHabit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = OinkPink,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Add a habit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * The always-present entry point to the private area.
 *
 * A single muted lock icon pinned to the bottom of the home screen, centered and
 * wrapped in an [IconButton] for a full 48dp touch target. Present regardless of
 * whether any private habits exist and regardless of the lock state, and rendered
 * identically every time, so its presence leaks nothing about whether the user
 * hides anything. Tapping it opens the PIN gate.
 */
@Composable
private fun PrivateEntryPoint(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Private",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
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
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
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

            // Two-line content column. The card carries a lot on one row - name,
            // balance, streak/freeze meta, and up to two 48dp check-in buttons -
            // which cannot all fit on a single line on a narrow or high-density
            // device at elevated font scales. Splitting into two lines gives each
            // element a real width budget: the name and meta flex and ellipsize,
            // the balance never wraps, and nothing is ever starved into a
            // per-character wrap the way a single weighted row was (issue #108).
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: habit name (flexes, ellipsizes) + spendable balance
                // (visual hierarchy #1, never wraps).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatCurrency(card.spendable),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OinkTeal,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Line 2: streak/freeze meta (flexes, single line) + inline quick
                // check-in - logs today without leaving the list.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HabitMeta(
                        streak = card.streak,
                        availableFreezes = card.availableFreezes,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TodayCheckInControl(
                        todayCompleted = card.todayCompleted,
                        habitType = card.habitType,
                        onCheckIn = onCheckIn
                    )
                }
            }
        }
    }
}

/**
 * Streak text plus an optional freeze count, matching the detail screen's chips.
 */
@Composable
private fun HabitMeta(streak: Int, availableFreezes: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Formatters.formatStreakWithEmoji(streak),
            style = MaterialTheme.typography.bodyMedium,
            color = if (streak > 0) OinkPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Takes only the width it needs but yields to the freeze chip and
            // ellipsizes rather than wrapping when the meta line is tight.
            modifier = Modifier.weight(1f, fill = false)
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

/**
 * Regression lock for issue #108: the worst-case habit card must lay out cleanly.
 *
 * Long name, a three-digit streak ("🔥🔥🔥 234 days"), a large four-figure
 * balance, an unlogged BUILD habit (the two-button check-in control), and
 * available freezes - all at a deliberately narrow 360dp width, and again at an
 * elevated font scale. Before the two-line layout this row starved its content
 * column into a per-character wrap ("2/3/4/d/a/y/s") and truncated the name to
 * "W…"; the preview exists so that regression is caught by eye in the IDE.
 *
 * The rendering tooling (`ui-tooling`) is a `debugImplementation`, so this only
 * renders in debug tooling and is stripped from release.
 */
@Preview(name = "Habit card - worst case", widthDp = 360, showBackground = true)
@Preview(
    name = "Habit card - worst case @ 1.5x font",
    widthDp = 360,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
private fun HabitCardWorstCasePreview() {
    OinkTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HabitCard(
                card = HabitCardState(
                    id = 1L,
                    emoji = "🏋️",
                    name = "Workout with weights every morning",
                    habitType = HabitType.BUILD,
                    streak = 234,
                    availableFreezes = 3,
                    spendable = 109_728L,
                    todayCompleted = null
                ),
                onClick = {},
                onCheckIn = {}
            )
            // A logged QUIT habit (single-button control) for contrast.
            HabitCard(
                card = HabitCardState(
                    id = 2L,
                    emoji = "🚭",
                    name = "No smoking",
                    habitType = HabitType.QUIT,
                    streak = 5,
                    availableFreezes = 0,
                    spendable = 4_250L,
                    todayCompleted = true
                ),
                onClick = {},
                onCheckIn = {}
            )
        }
    }
}
