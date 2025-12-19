package com.oink.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oink.app.data.CashOut
import com.oink.app.data.CheckIn
import com.oink.app.ui.theme.OinkPink
import com.oink.app.ui.theme.OinkPinkDark
import com.oink.app.ui.theme.OinkTeal
import com.oink.app.ui.theme.OinkTealContainer
import com.oink.app.ui.theme.OinkWarning
import com.oink.app.viewmodel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar screen showing a monthly view of all check-ins.
 *
 * This is where users can:
 * - See their exercise history at a glance
 * - Tap on past days to log retroactive workouts
 * - Visualize streaks and patterns
 *
 * The calendar makes missed days painfully obvious (red) and
 * exercise days satisfying (green). Psychological warfare, baby.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val checkIns by viewModel.allCheckIns.collectAsStateWithLifecycle()
    val cashOuts by viewModel.allCashOuts.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Current month being displayed
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }

    // Selected date for logging dialog (single day)
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    // Multi-select mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var rangeStartDate by remember { mutableStateOf<LocalDate?>(null) }

    // Create a map of date -> check-in for quick lookup
    val checkInMap by remember(checkIns) {
        derivedStateOf {
            checkIns.associateBy { it.date }
        }
    }

    // Create a map of date -> cash-outs for reward indicators
    val cashOutsByDate by remember(cashOuts) {
        derivedStateOf {
            cashOuts.groupBy { cashOut ->
                // Convert millis to LocalDate
                java.time.Instant.ofEpochMilli(cashOut.cashedOutAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }
        }
    }

    // Exit selection mode when navigating away
    val exitSelectionMode = {
        isSelectionMode = false
        selectedDates = emptySet()
        rangeStartDate = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            "${selectedDates.size} selected"
                        } else {
                            "Calendar"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                exitSelectionMode()
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) {
                                Icons.Default.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (isSelectionMode) "Cancel selection" else "Go back"
                        )
                    }
                },
                actions = {
                    if (!isSelectionMode) {
                        // Enter selection mode button
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Multi-select mode"
                            )
                        }
                    } else {
                        // Clear selection button
                        if (selectedDates.isNotEmpty()) {
                            IconButton(onClick = { selectedDates = emptySet() }) {
                                Icon(
                                    imageVector = Icons.Default.Deselect,
                                    contentDescription = "Clear selection"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    }
                )
            )
        },
        bottomBar = {
            // Bulk action bar when in selection mode with items selected
            AnimatedVisibility(
                visible = isSelectionMode && selectedDates.isNotEmpty(),
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it }
            ) {
                BulkActionBar(
                    selectedCount = selectedDates.size,
                    onMarkExercised = {
                        viewModel.bulkRecordCheckIns(selectedDates, true)
                        exitSelectionMode()
                    },
                    onMarkMissed = {
                        viewModel.bulkRecordCheckIns(selectedDates, false)
                        exitSelectionMode()
                    },
                    isLoading = isLoading
                )
            }
        }
    ) { paddingValues ->
        // Swipe threshold in pixels
        val swipeThreshold = 100f
        var totalDragAmount by remember { mutableStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragAmount = 0f },
                        onDragEnd = {
                            // Swipe left = next month, swipe right = previous month
                            when {
                                totalDragAmount < -swipeThreshold && currentYearMonth < YearMonth.now() -> {
                                    currentYearMonth = currentYearMonth.plusMonths(1)
                                }
                                totalDragAmount > swipeThreshold -> {
                                    currentYearMonth = currentYearMonth.minusMonths(1)
                                }
                            }
                            totalDragAmount = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDragAmount += dragAmount
                        }
                    )
                }
        ) {
            // Streak banner at top
            if (streak > 0) {
                StreakBanner(streak = streak)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Month navigation
            MonthNavigator(
                currentYearMonth = currentYearMonth,
                onPreviousMonth = { currentYearMonth = currentYearMonth.minusMonths(1) },
                onNextMonth = {
                    // Don't allow navigating to future months
                    if (currentYearMonth < YearMonth.now()) {
                        currentYearMonth = currentYearMonth.plusMonths(1)
                    }
                },
                canGoNext = currentYearMonth < YearMonth.now()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Day of week headers
            DayOfWeekHeader()

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            CalendarGrid(
                yearMonth = currentYearMonth,
                checkInMap = checkInMap,
                cashOutsByDate = cashOutsByDate,
                isSelectionMode = isSelectionMode,
                selectedDates = selectedDates,
                rangeStartDate = rangeStartDate,
                onDayClick = { date ->
                    // Only allow clicking on past or today
                    if (!date.isAfter(LocalDate.now())) {
                        if (isSelectionMode) {
                            // In selection mode, toggle date selection
                            selectedDates = if (selectedDates.contains(date)) {
                                selectedDates - date
                            } else {
                                selectedDates + date
                            }
                        } else {
                            // Normal mode - open single day dialog
                            selectedDate = date
                        }
                    }
                },
                onDayLongClick = { date ->
                    if (!date.isAfter(LocalDate.now())) {
                        if (!isSelectionMode) {
                            // Long press enters selection mode and selects this date
                            isSelectionMode = true
                            selectedDates = setOf(date)
                            rangeStartDate = date
                        } else if (rangeStartDate != null) {
                            // In selection mode, long press extends range selection
                            val start = minOf(rangeStartDate!!, date)
                            val end = maxOf(rangeStartDate!!, date)
                            val today = LocalDate.now()
                            val range = generateSequence(start) { it.plusDays(1) }
                                .takeWhile { !it.isAfter(end) && !it.isAfter(today) }
                                .toSet()
                            selectedDates = selectedDates + range
                            rangeStartDate = date
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Legend
            CalendarLegend()
        }
    }

    // Log day dialog
    selectedDate?.let { date ->
        LogDayDialog(
            date = date,
            existingCheckIn = checkInMap[date],
            isLoading = isLoading,
            onDismiss = { selectedDate = null },
            onLog = { didExercise ->
                viewModel.recordCheckIn(date, didExercise)
                selectedDate = null
            }
        )
    }
}

/**
 * Banner showing current streak with fire emoji.
 */
@Composable
private fun StreakBanner(streak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = OinkPink
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$streak day${if (streak > 1) "s" else ""} streak!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OinkPink
                )
                if (streak >= 7) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "🔥", fontSize = 24.sp)
                }
                if (streak >= 30) {
                    Text(text = "🔥", fontSize = 24.sp)
                }
            }
        }
    }
}

/**
 * Month navigation with previous/next arrows.
 */
@Composable
private fun MonthNavigator(
    currentYearMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(32.dp)
            )
        }

        AnimatedContent(
            targetState = currentYearMonth,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "month_animation"
        ) { yearMonth ->
            Text(
                text = yearMonth.format(monthFormatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        IconButton(
            onClick = onNextMonth,
            enabled = canGoNext
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next month",
                modifier = Modifier.size(32.dp),
                tint = if (canGoNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }
}

/**
 * Day of week headers (Sun, Mon, Tue, etc.)
 */
@Composable
private fun DayOfWeekHeader() {
    val daysOfWeek = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * The actual calendar grid.
 */
@Composable
private fun CalendarGrid(
    yearMonth: YearMonth,
    checkInMap: Map<LocalDate, CheckIn>,
    cashOutsByDate: Map<LocalDate, List<CashOut>>,
    isSelectionMode: Boolean,
    selectedDates: Set<LocalDate>,
    rangeStartDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstDayOfMonth = yearMonth.atDay(1)
    val lastDayOfMonth = yearMonth.atEndOfMonth()

    // Calculate offset for first day (what day of week does month start on?)
    // Sunday = 0, Saturday = 6
    val firstDayOffset = (firstDayOfMonth.dayOfWeek.value % 7)

    // Create list of all days to display (including empty cells for offset)
    val days = buildList {
        // Empty cells for days before first of month
        repeat(firstDayOffset) {
            add(null)
        }
        // Actual days of month
        var currentDay = firstDayOfMonth
        while (currentDay <= lastDayOfMonth) {
            add(currentDay)
            currentDay = currentDay.plusDays(1)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        items(days) { date ->
            if (date == null) {
                // Empty cell
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp)
                )
            } else {
                val checkIn = checkInMap[date]
                val isToday = date == today
                val isFuture = date.isAfter(today)
                val isSelected = selectedDates.contains(date)
                val isRangeStart = rangeStartDate == date
                val hasReward = cashOutsByDate.containsKey(date)

                CalendarDay(
                    date = date,
                    checkIn = checkIn,
                    isToday = isToday,
                    isFuture = isFuture,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    isRangeStart = isRangeStart,
                    hasReward = hasReward,
                    onClick = { onDayClick(date) },
                    onLongClick = { onDayLongClick(date) }
                )
            }
        }
    }
}

/**
 * Individual day cell in the calendar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDay(
    date: LocalDate,
    checkIn: CheckIn?,
    isToday: Boolean,
    isFuture: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isRangeStart: Boolean,
    hasReward: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Determine background color based on selection and check-in status
    val backgroundColor = when {
        isFuture -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        checkIn?.didExercise == true -> OinkTealContainer
        checkIn?.didExercise == false -> MaterialTheme.colorScheme.errorContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        checkIn?.didExercise == true -> OinkTeal
        checkIn?.didExercise == false -> OinkWarning
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Border: today gets primary border, selected items get thicker primary border
    val borderModifier = when {
        isSelected && isRangeStart -> Modifier.border(
            width = 3.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        )
        isSelected -> Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        )
        isToday -> Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        )
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(borderModifier)
            .combinedClickable(
                enabled = !isFuture,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Reward indicator at top
            if (hasReward && !isSelectionMode) {
                Text(
                    text = "🎁",
                    fontSize = 8.sp,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )

            // Small icon indicator
            when {
                isSelected && isSelectionMode -> {
                    // Show checkmark for selected in selection mode
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Selected",
                        modifier = Modifier.size(12.dp),
                        tint = textColor
                    )
                }
                checkIn != null -> {
                    Icon(
                        imageVector = if (checkIn.didExercise) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = textColor
                    )
                }
            }
        }
    }
}

/**
 * Legend explaining the colors.
 */
@Composable
private fun CalendarLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(
                color = OinkTealContainer,
                iconColor = OinkTeal,
                icon = Icons.Default.Check,
                label = "Exercised"
            )
            LegendItem(
                color = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                icon = Icons.Default.Close,
                label = "Missed"
            )
            LegendItem(
                color = Color.Transparent,
                iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                icon = null,
                label = "No log"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reward indicator legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🎁 = Reward claimed on this day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LegendItem(
    color: Color,
    iconColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconColor
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Dialog for logging a day (new or edit).
 */
@Composable
private fun LogDayDialog(
    date: LocalDate,
    existingCheckIn: CheckIn?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onLog: (Boolean) -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    val isToday = date == LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isToday) "Today" else date.format(dateFormatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (existingCheckIn != null) {
                    Text(
                        text = if (existingCheckIn.didExercise) {
                            "You logged this as an exercise day ✓"
                        } else {
                            "You logged this as a rest day"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Want to change it?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Did you exercise on this day?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLog(true) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OinkTeal
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Yes!")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onLog(false) },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("No")
                }
            }
        }
    )
}

/**
 * Bottom bar with bulk action buttons for selection mode.
 */
@Composable
private fun BulkActionBar(
    selectedCount: Int,
    onMarkExercised: () -> Unit,
    onMarkMissed: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mark $selectedCount day${if (selectedCount != 1) "s" else ""} as:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mark as Missed button
                OutlinedButton(
                    onClick = onMarkMissed,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Missed")
                }

                // Mark as Exercised button
                Button(
                    onClick = onMarkExercised,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OinkTeal
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exercised!")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 Tip: Long-press a day to start range selection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

