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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oink.app.ui.components.TodayCheckInControl
import com.oink.app.ui.theme.OinkError
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.HabitCardState
import com.oink.app.viewmodel.PrivateUiState
import com.oink.app.viewmodel.PrivateViewModel
import com.oink.app.viewmodel.RecoveryUiState
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
    val recovery by viewModel.recovery.collectAsStateWithLifecycle()

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
            when (val active = recovery) {
                RecoveryUiState.Idle -> when (val state = uiState) {
                    PrivateUiState.Loading -> LoadingContent()
                    is PrivateUiState.NeedsPinSetup -> PinSetupContent(
                        requiresSecurityQuestion = state.requiresSecurityQuestion,
                        onCreatePin = viewModel::createPin
                    )
                    is PrivateUiState.Locked -> LockedContent(
                        state = state,
                        onSubmitPin = viewModel::submitPin,
                        onForgotPin = viewModel::onForgotPin
                    )
                    is PrivateUiState.Unlocked -> UnlockedContent(
                        habits = state.habits,
                        onHabitClick = onHabitClick,
                        onCheckIn = viewModel::recordCheckIn
                    )
                }
                is RecoveryUiState.SecurityQuestion -> SecurityQuestionContent(
                    state = active,
                    onSubmit = viewModel::submitSecurityAnswer,
                    onCancel = viewModel::cancelRecovery
                )
                RecoveryUiState.SettingNewPin -> NewPinContent(
                    onSetPin = viewModel::completeRecovery,
                    onCancel = viewModel::cancelRecovery
                )
                RecoveryUiState.Unavailable -> RecoveryUnavailableContent(
                    onDismiss = viewModel::cancelRecovery
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
 *
 * When [requiresSecurityQuestion] is set - the device offers no biometric or
 * lockscreen-credential recovery - a question and answer are collected too, as
 * the only way to recover a forgotten PIN, and the button stays disabled until
 * both are filled.
 */
@Composable
private fun PinSetupContent(
    requiresSecurityQuestion: Boolean,
    onCreatePin: (pin: String, question: String?, answer: String?) -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }

    val pinValid = PrivateViewModel.isValidPin(pin)
    val matches = confirm.isNotEmpty() && pin == confirm
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val questionComplete = !requiresSecurityQuestion ||
        (question.isNotBlank() && answer.isNotBlank())
    val canSubmit = pinValid && matches && questionComplete

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

        if (requiresSecurityQuestion) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Set a security question so you can reset your PIN if you forget it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            RecoveryTextField(
                value = question,
                onValueChange = { question = it },
                label = "Security question"
            )
            Spacer(modifier = Modifier.height(16.dp))
            RecoveryTextField(
                value = answer,
                onValueChange = { answer = it },
                label = "Answer"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        GateButton(
            text = "Set PIN",
            enabled = canSubmit,
            onClick = {
                if (requiresSecurityQuestion) onCreatePin(pin, question, answer)
                else onCreatePin(pin, null, null)
            }
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
    onSubmitPin: (String) -> Unit,
    onForgotPin: () -> Unit
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
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onForgotPin) {
            Text(
                text = "Forgot PIN?",
                style = MaterialTheme.typography.bodyMedium,
                color = OinkTeal
            )
        }
    }
}

/**
 * Recovery step: answer the security question. Shown only when the device has no
 * biometric or lockscreen-credential recovery and a question was configured. A
 * wrong answer shows an error; too many wrong answers lock the field out with a
 * countdown, mirroring the PIN prompt's rate limit.
 */
@Composable
private fun SecurityQuestionContent(
    state: RecoveryUiState.SecurityQuestion,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var answer by rememberSaveable { mutableStateOf("") }
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
            text = "Reset PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        RecoveryTextField(
            value = answer,
            onValueChange = { answer = it },
            label = "Answer",
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
                    text = "That's not right. Try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OinkError
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        GateButton(
            text = "Continue",
            enabled = !lockedOut && answer.isNotBlank(),
            onClick = {
                onSubmit(answer)
                answer = ""
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = OinkTeal
            )
        }
    }
}

/**
 * Recovery step: set a new PIN once ownership has been proven (via biometric,
 * lockscreen credential, or a correct security answer). Mirrors [PinSetupContent]'s
 * enter-and-confirm form; completing it unlocks the area.
 */
@Composable
private fun NewPinContent(
    onSetPin: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val pinValid = PrivateViewModel.isValidPin(pin)
    val matches = confirm.isNotEmpty() && pin == confirm
    val mismatch = confirm.isNotEmpty() && pin != confirm

    GateColumn {
        LockBadge()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Set a new PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        PinField(
            value = pin,
            onValueChange = { pin = it.digitsOnly() },
            label = "New PIN"
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
            enabled = pinValid && matches,
            onClick = { onSetPin(pin) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = OinkTeal
            )
        }
    }
}

/**
 * Recovery dead end: no biometric or lockscreen credential is enrolled and no
 * security question was configured, so there is no way to reset the PIN. The copy
 * stays generic to preserve plausible deniability about the area's contents.
 */
@Composable
private fun RecoveryUnavailableContent(onDismiss: () -> Unit) {
    GateColumn {
        LockBadge()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Can't reset PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No recovery method is set up on this device. Set a screen lock to " +
                "reset your PIN with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        GateButton(
            text = "Back",
            enabled = true,
            onClick = onDismiss
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
    onHabitClick: (Long) -> Unit,
    onCheckIn: (Long, Boolean) -> Unit
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
            PrivateHabitCard(
                card = card,
                onClick = { onHabitClick(card.id) },
                onCheckIn = { completed -> onCheckIn(card.id, completed) }
            )
        }
    }
}

/**
 * A private habit card: emoji, name, streak, this habit's spendable balance, and
 * an inline quick check-in control. Mirrors the home habit card so the two areas
 * read alike. Tapping the card body opens the habit detail; the check-in control
 * logs today without navigating (see [TodayCheckInControl]).
 */
@Composable
private fun PrivateHabitCard(
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.width(8.dp))

            TodayCheckInControl(
                todayCompleted = card.todayCompleted,
                onCheckIn = onCheckIn
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

