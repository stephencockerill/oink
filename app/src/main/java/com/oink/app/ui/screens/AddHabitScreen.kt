package com.oink.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oink.app.data.HabitType
import com.oink.app.data.PreferencesRepository
import com.oink.app.ui.components.EmojiPickerField
import com.oink.app.ui.theme.OinkPink
import com.oink.app.utils.Formatters
import com.oink.app.utils.HabitCopy
import com.oink.app.viewmodel.AddHabitViewModel

/**
 * Add-habit screen - a single form for creating a habit.
 *
 * The user names the habit, picks an emoji and a per-day reward, and opts into
 * streak freezes and privacy. Save is disabled until the form is valid
 * (non-blank name, positive reward). On a successful insert the screen invokes
 * [onSaved] so the caller can pop back to the home list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitScreen(
    viewModel: AddHabitViewModel,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Habit",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Name
            SectionHeader(title = "Name")
            Spacer(modifier = Modifier.height(8.dp))
            FormCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. Meditate, Read, Run") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Type
            SectionHeader(title = "Type")
            Spacer(modifier = Modifier.height(8.dp))
            FormCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HabitTypeChip(
                            label = "Build",
                            selected = uiState.habitType == HabitType.BUILD,
                            onClick = { viewModel.onHabitTypeSelect(HabitType.BUILD) },
                            modifier = Modifier.weight(1f)
                        )
                        HabitTypeChip(
                            label = "Quit",
                            selected = uiState.habitType == HabitType.QUIT,
                            onClick = { viewModel.onHabitTypeSelect(HabitType.QUIT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (uiState.habitType) {
                            HabitType.BUILD -> "A habit you want to start. You earn each day you show up."
                            HabitType.QUIT -> "A habit you want to stop. Every clean day earns automatically; you only log a slip."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Emoji
            SectionHeader(title = "Icon")
            Spacer(modifier = Modifier.height(8.dp))
            FormCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emoji",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Pick any emoji to represent this habit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    EmojiPickerField(
                        emoji = uiState.emoji,
                        onEmojiSelected = viewModel::onEmojiSelect
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reward
            SectionHeader(title = "Reward")
            Spacer(modifier = Modifier.height(8.dp))
            FormCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = HabitCopy.REWARD_DESCRIPTION,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PreferencesRepository.REWARD_OPTIONS.forEach { amount ->
                            val isSelected = uiState.rewardValue == amount
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onRewardSelect(amount) },
                                label = {
                                    Text(
                                        text = "\$${amount / 100}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val lossLabel = when (uiState.habitType) {
                        HabitType.BUILD -> "Miss a day"
                        HabitType.QUIT -> "Slip"
                    }
                    Text(
                        text = "$lossLabel = balance halved. Freeze cost = 2× reward (${Formatters.formatCurrency(uiState.rewardValue * 2)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Options
            SectionHeader(title = "Options")
            Spacer(modifier = Modifier.height(8.dp))
            FormCard {
                Column {
                    FormToggleItem(
                        title = "Streak freezes",
                        subtitle = "Bank ${PreferencesRepository.MAX_FREEZES} freezes to protect your streak on a missed day.",
                        checked = uiState.freezesEnabled,
                        onCheckedChange = viewModel::onFreezesToggle
                    )
                    FormToggleItem(
                        title = "Private",
                        subtitle = "Hide this habit from the shared home list.",
                        checked = uiState.isPrivate,
                        onCheckedChange = viewModel::onPrivateToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = uiState.canSave,
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
                    text = "Create Habit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HabitTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
private fun FormToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

