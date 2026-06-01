package com.weekmenu.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

private val dayLabels = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
fun WeekOverviewScreen(
    onNavigateToOverride: () -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val weekEnd by viewModel.weekEnd.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    val weekGrid by viewModel.weekGrid.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for move dialog
    var mealToMove by remember { mutableStateOf<WeekSummary?>(null) }

    // Derive work days from grid
    val workDays = remember(weekGrid) {
        weekGrid.filter { it.meals.any { m -> m.slotType == "work" } }
            .map { it.dayOfWeek }.toSet()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Week navigation header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
                }

                Text(
                    text = buildWeekLabel(weekStart, weekEnd),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = { viewModel.nextWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
                }
            }

            if (!hasPlan) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No plan for this week",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToOverride) {
                            Text("Create plan")
                        }
                    }
                }
            } else {
                // ── Day-by-day list ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(weekGrid) { dayIndex, dayMeals ->
                        DayCard(
                            dayLabel = dayLabels[dayIndex],
                            meals = dayMeals.meals,
                            onMealClick = { summary ->
                                when (summary.slotType) {
                                    null -> onNavigateToOverride()
                                    "work" -> {}
                                    else -> mealToMove = summary
                                }
                            }
                        )
                    }
                }

                // ── Action buttons ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToOverride,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetPlan() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    // ── Move meal dialog ──
    mealToMove?.let { summary ->
        MoveMealDialog(
            recipeName = summary.recipeName ?: "Meal",
            sourceDay = summary.dayOfWeek,
            sourceMeal = if (daySlotsContainsMeal(weekGrid, summary.dayOfWeek, "lunch", summary.recipeName)) "lunch" else "dinner",
            workDays = workDays,
            onDismiss = { mealToMove = null },
            onConfirm = { targetDay, targetMeal ->
                val sourceMeal = if (daySlotsContainsMeal(weekGrid, summary.dayOfWeek, "lunch", summary.recipeName)) "lunch" else "dinner"
                if (summary.slotId != null) {
                    viewModel.moveMeal(
                        slotId = summary.slotId,
                        sourceDay = summary.dayOfWeek,
                        sourceMeal = sourceMeal,
                        targetDay = targetDay,
                        targetMeal = targetMeal
                    )
                }
                mealToMove = null
                if (targetDay == summary.dayOfWeek && targetMeal == sourceMeal) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Already there",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        )
    }
}

/** Figure out if a meal slot on a given day is in lunch or dinner position. */
private fun daySlotsContainsMeal(
    grid: List<DayMeals>,
    day: Int,
    mealPosition: String,
    recipeName: String?
): Boolean {
    val targetDay = grid.find { it.dayOfWeek == day } ?: return false
    val idx = if (mealPosition == "lunch") 0 else 1
    return targetDay.meals.getOrNull(idx)?.recipeName == recipeName
}

// ── Move Meal Dialog ─────────────────────────────────────────────

@Composable
private fun MoveMealDialog(
    recipeName: String,
    sourceDay: Int,
    sourceMeal: String,
    workDays: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (targetDay: Int, targetMeal: String) -> Unit
) {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var selectedDay by remember { mutableStateOf(sourceDay) }
    var selectedMeal by remember { mutableStateOf(sourceMeal) }

    // Lunch is only available for non-work days
    val mealOptions = if (selectedDay in workDays) listOf("dinner") else listOf("lunch", "dinner")

    // Reset meal to dinner if day is a work day and currently lunch
    LaunchedEffect(selectedDay) {
        if (selectedDay in workDays && selectedMeal == "lunch") {
            selectedMeal = "dinner"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Move $recipeName",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Day selector
                Text("Day", style = MaterialTheme.typography.labelLarge)
                dayNames.forEachIndexed { index, name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedDay == index,
                            onClick = { selectedDay = index }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { selectedDay = index }
                        )
                    }
                }

                HorizontalDivider()

                // Meal time selector
                Text("Meal", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    mealOptions.forEach { meal ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedMeal == meal,
                                onClick = { selectedMeal = meal }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = meal.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { selectedMeal = meal }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDay, selectedMeal) }) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ── Day card ─────────────────────────────────────────────────────

@Composable
private fun DayCard(
    dayLabel: String,
    meals: List<WeekSummary>,
    onMealClick: (WeekSummary) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            meals.forEach { summary ->
                MealRow(summary = summary, onClick = { onMealClick(summary) })
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ── Meal row ─────────────────────────────────────────────────────

@Composable
private fun MealRow(
    summary: WeekSummary,
    onClick: () -> Unit
) {
    val isEmpty = summary.slotType == null
    val isWork = summary.slotType == "work"
    val bgColor = when {
        isWork -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        isEmpty -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    }

    val displayText = when {
        isWork -> "Work"
        isEmpty -> "—"
        else -> (summary.recipeName ?: "")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isWork) onClick() },
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isWork -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            when {
                isWork -> {}
                isEmpty -> {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────

private fun buildWeekLabel(start: LocalDate, end: LocalDate): String {
    val sf = WeekViewModel.WEEK_FORMATTER
    return "${start.format(sf)} - ${end.format(sf)}"
}
