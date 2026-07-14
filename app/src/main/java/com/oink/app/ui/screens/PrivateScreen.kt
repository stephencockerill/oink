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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oink.app.ui.theme.OinkError
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.HabitCardState
import com.oink.app.viewmodel.PrivateUiState
import com.oink.app.viewmodel.PrivateViewModel
import kotlinx.coroutines.delay

/**
 * The private area behind the PIN gate.
 *
 * Renders per [PrivateUiState]: a create-PIN form on first run, a PIN prompt
 * when locked (with a rate-limit countdown), or the private habit list when
 * unlocked. The locked and setup states reveal nothing about whether any private
 * habits exist - that is enforced in [PrivateViewModel], and mirrored here by
 * showing the add FAB only once unlocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateScreen(
    viewModel: PrivateViewModel,
    onHabitClick: (Long) -> Unit,
    onAddPrivateHabit: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🔒 Private",
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
        },
        floatingActionButton = {
            if (uiState is PrivateUiState.Unlocked) {
                FloatingActionButton(
                    onClick = onAddPrivateHabit,
                    containerColor = OinkPink,
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add private habit"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                PrivateUiState.Loading -> LoadingContent()
                PrivateUiState.NeedsPinSetup -> PinSetupContent(onCreatePin = viewModel::createPin)
                is PrivateUiState.Locked -> LockedContent(state = state, onSubmitPin = viewModel::submitPin)
                is PrivateUiState.Unlocked -> UnlockedContent(
                    habits = state.habits,
                    onHabitClick = onHabitClick
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OinkPink)
    }
}

/**
 * First-run create-PIN form: enter and confirm a 4-6 digit PIN. The copy is
 * deliberately generic and says nothing about what the area contains.
 */
@Composable
private fun PinSetupContent(onCreatePin: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val pinValid = PrivateViewModel.isValidPin(pin)
    val matches = confirm.isNotEmpty() && pin == confirm
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val canSubmit = pinValid && matches

    GateColumn {
        LockBadge()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Set a PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Protect this space with a 4-6 digit PIN. You'll enter it each time you open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        PinField(
            value = pin,
            onValueChange = { pin = it.digitsOnly() },
            label = "PIN"
        )
        Spacer(modifier = Modifier.height(16.dp))
        PinField(
            value = confirm,
            onValueChange = { confirm = it.digitsOnly() },
            label = "Confirm PIN",
            isError = mismatch
        )

        if (mismatch) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PINs don't match",
                style = MaterialTheme.typography.bodySmall,
                color = OinkError
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        GateButton(
            text = "Set PIN",
            enabled = canSubmit,
            onClick = { onCreatePin(pin) }
        )
    }
}

/**
 * PIN prompt when locked. Wrong attempts show an error; a rate-limit lockout
 * disables entry and counts down locally, re-enabling input when it elapses.
 */
@Composable
private fun LockedContent(
    state: PrivateUiState.Locked,
    onSubmitPin: (String) -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var secondsLeft by remember(state.remainingLockoutSeconds, state.isLockedOut) {
        mutableIntStateOf(if (state.isLockedOut) state.remainingLockoutSeconds else 0)
    }

    LaunchedEffect(state.isLockedOut, state.remainingLockoutSeconds) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    val lockedOut = state.isLockedOut && secondsLeft > 0

    GateColumn {
        LockBadge()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        PinField(
            value = pin,
            onValueChange = { pin = it.digitsOnly() },
            label = "PIN",
            enabled = !lockedOut,
            isError = state.showError
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
            state.showError -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incorrect PIN. ${state.remainingAttempts} attempts left.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OinkError
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        GateButton(
            text = "Unlock",
            enabled = !lockedOut && PrivateViewModel.isValidPin(pin),
            onClick = {
                onSubmitPin(pin)
                pin = ""
            }
        )
    }
}

/**
 * The private habit list. An empty list reads as a calm invitation, not an
 * error, so an unlocked-but-empty area looks intentional.
 */
@Composable
private fun UnlockedContent(
    habits: List<HabitCardState>,
    onHabitClick: (Long) -> Unit
) {
    if (habits.isEmpty()) {
        GateColumn {
            LockBadge()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to add a habit only you can see.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = habits,
            key = { it.id },
            contentType = { "private-habit-card" }
        ) { card ->
            PrivateHabitCard(card = card, onClick = { onHabitClick(card.id) })
        }
    }
}

/**
 * A private habit card: emoji, name, streak, and this habit's spendable balance.
 * Mirrors the home habit card so the two areas read alike.
 */
@Composable
private fun PrivateHabitCard(
    card: HabitCardState,
    onClick: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(OinkPink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = card.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatStreakWithEmoji(card.streak),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (card.streak > 0) OinkPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = Formatters.formatCurrency(card.spendable),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OinkTeal
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
 * Shared centered column used by the gate states (setup, locked, empty).
 */
@Composable
private fun GateColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
private fun LockBadge() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(OinkTeal.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = OinkTeal,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun GateButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OinkPink,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

