package com.oink.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.oink.app.data.CashOut
import com.oink.app.data.RewardCategories
import com.oink.app.ui.theme.MoneyGreen
import com.oink.app.ui.theme.MoneyGreenDark
import com.oink.app.utils.Formatters
import com.oink.app.viewmodel.RewardsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rewards screen - where you CASH IN your hard-earned piggy bank funds!
 *
 * This is the celebration zone. The whole vibe should be:
 * "You EARNED this through discipline and sweat. Treat yourself!"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
    onNavigateBack: () -> Unit
) {
    val balance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val cashOuts by viewModel.allCashOuts.collectAsStateWithLifecycle()
    val totalCashedOut by viewModel.totalCashedOut.collectAsStateWithLifecycle()
    val totalWorkouts by viewModel.totalWorkouts.collectAsStateWithLifecycle()
    val totalEarned by viewModel.totalEarned.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val cashOutSuccess by viewModel.cashOutSuccess.collectAsStateWithLifecycle()
    val selectedCashOut by viewModel.selectedCashOut.collectAsStateWithLifecycle()
    val showDeleteConfirmation by viewModel.showDeleteConfirmation.collectAsStateWithLifecycle()

    // Use totalEarned from ViewModel (raw check-in balance = total accumulated through exercise)
    // This is correct even with freeze spending, because freezes don't reduce earnings - just spending
    val lifetimeEarned = totalEarned

    val snackbarHostState = remember { SnackbarHostState() }
    var showCashOutSheet by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var celebrationCashOut by remember { mutableStateOf<CashOut?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }

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
                title = { Text("🎁 Rewards", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
                // Balance & Cash Out Button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    BalanceCard(
                        balance = balance,
                        onCashOut = { showCashOutSheet = true }
                    )
                }

                // Stats
                item {
                    StatsRow(
                        lifetimeEarned = lifetimeEarned,
                        rewardCount = cashOuts.size,
                        totalWorkouts = totalWorkouts
                    )
                }

                // Reward History Header
                if (cashOuts.isNotEmpty()) {
                    item {
                        Text(
                            text = "🏆 Rewards Earned",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Reward History
                items(cashOuts) { cashOut ->
                    RewardHistoryItem(
                        cashOut = cashOut,
                        onClick = {
                            viewModel.selectCashOut(cashOut)
                            showEditSheet = true
                        }
                    )
                }

                // Empty state
                if (cashOuts.isEmpty()) {
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
}

@Composable
private fun BalanceCard(
    balance: Double,
    onCashOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🐷 Piggy Bank Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Formatters.formatCurrency(balance),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCashOut,
                enabled = balance > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MoneyGreen
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Treat Yourself! 🎉",
                    fontWeight = FontWeight.Bold
                )
            }
            if (balance == 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Work out to earn rewards!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    lifetimeEarned: Double,
    rewardCount: Int,
    totalWorkouts: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "💰",
            label = "Earned",
            value = Formatters.formatCurrency(lifetimeEarned)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "🎁",
            label = "Rewards",
            value = rewardCount.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            emoji = "🏋️",
            label = "Workouts",
            value = totalWorkouts.toString()
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun RewardHistoryItem(
    cashOut: CashOut,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            // Emoji
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(text = cashOut.emoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
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
                    text = "${cashOut.workoutsToEarn} workouts earned this!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoneyGreen
                )
            }

            // Amount
            Text(
                text = Formatters.formatCurrency(cashOut.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MoneyGreen
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
        Text(text = "🎁", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No rewards yet!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Work out consistently to build up your piggy bank, then treat yourself to something nice!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CashOutBottomSheet(
    balance: Double,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCashOut: (name: String, amount: Double, emoji: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎁") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
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
                text = "🎉 Treat Yourself!",
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
                    containerColor = MoneyGreen
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
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "celebration_scale"
    )

    Dialog(onDismissRequest = onDismiss) {
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
                // Big celebration emoji
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "YOU EARNED IT!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MoneyGreen
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
                                    MoneyGreen.copy(alpha = 0.3f),
                                    MoneyGreen.copy(alpha = 0.1f)
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
                    color = MoneyGreen
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${cashOut.workoutsToEarn} workouts made this possible!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoneyGreen
                    )
                ) {
                    Text("Awesome! 🐷")
                }
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
    currentBalance: Double,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, emoji: String) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(cashOut.name) }
    var amountText by remember { mutableStateOf(cashOut.amount.toString()) }
    var selectedEmoji by remember { mutableStateOf(cashOut.emoji) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
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
                    text = "✏️ Edit Reward",
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
                        containerColor = MoneyGreen
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
                    color = MoneyGreen
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

