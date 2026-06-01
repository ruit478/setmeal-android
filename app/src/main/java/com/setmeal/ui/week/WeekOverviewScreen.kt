package com.setmeal.ui.week

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Pastel-ish tints shared by batch groups. */
private val batchTints = listOf(
    Color(0xFFA5D6A7), // soft green
    Color(0xFF90CAF9), // soft blue
    Color(0xFFFFCC80), // soft orange
    Color(0xFFCE93D8), // soft purple
    Color(0xFF80DEEA), // soft cyan
    Color(0xFFF48FB1), // soft pink
)

/** Background for work-typed slots. */
private val workBackground = Color(0xFFE0E0E0)

/** Empty-cell background. */
private val emptyBackground = Color(0xFFF5F5F5)

@Composable
fun WeekOverviewScreen(
    onNavigateToOverride: () -> Unit,
    onNavigateToBatch: (String) -> Unit,
    onNavigateToRecipeDetail: (String) -> Unit = {},
    viewModel: WeekViewModel = hiltViewModel()
) {
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val weekEnd by viewModel.weekEnd.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    val weekGrid by viewModel.weekGrid.collectAsStateWithLifecycle()
    val hasBatchRecipes by viewModel.hasBatchRecipes.collectAsStateWithLifecycle()
    val currentPlanId by viewModel.currentPlanId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (hasBatchRecipes && currentPlanId != null) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToBatch(currentPlanId!!) },
                    icon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                    text = { Text("Batch Cooking") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
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
                // ── Empty state ──
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
                // ── Day label row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    dayLabels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Week grid: 2 rows (Lunch, Dinner) × 7 columns ──
                // weekGrid layout: indices 0-6 = Lunch Mon-Sun, 7-13 = Dinner Mon-Sun
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val lunchCells = weekGrid.take(7)
                    val dinnerCells = weekGrid.drop(7)

                    MealRow(
                        label = "Lunch",
                        cells = lunchCells,
                        onCellClick = { summary ->
                            handleCellClick(
                                summary = summary,
                                onNavigateToOverride = onNavigateToOverride,
                                onNavigateToRecipeDetail = onNavigateToRecipeDetail,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )
                        }
                    )

                    MealRow(
                        label = "Dinner",
                        cells = dinnerCells,
                        onCellClick = { summary ->
                            handleCellClick(
                                summary = summary,
                                onNavigateToOverride = onNavigateToOverride,
                                onNavigateToRecipeDetail = onNavigateToRecipeDetail,
                                snackbarHostState = snackbarHostState,
                                scope = scope
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Action buttons ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasBatchRecipes && currentPlanId != null) {
                        OutlinedButton(
                            onClick = { onNavigateToBatch(currentPlanId!!) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batch")
                        }
                    }

                    Button(
                        onClick = onNavigateToOverride,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit")
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetToCurrentWeek() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MealRow(
    label: String,
    cells: List<WeekSummary>,
    onCellClick: (WeekSummary) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Meal label column
        Surface(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Seven day cells
        cells.forEach { summary ->
            WeekCell(summary = summary, onClick = { onCellClick(summary) })
        }
    }
}

@Composable
private fun WeekCell(
    summary: WeekSummary,
    onClick: () -> Unit
) {
    val isEmpty = summary.slotType == null || summary.recipeId == null
    val isWorkSlot = summary.slotType == "work"
    val isBatch = summary.batchGroup != null

    val bgColor = when {
        isEmpty -> emptyBackground
        isWorkSlot -> workBackground
        isBatch -> batchTints[summary.batchGroup!! % batchTints.size]
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        tonalElevation = if (isEmpty) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Recipe name (truncated, 1 line)
            Text(
                text = if (isEmpty) "" else (summary.recipeName ?: ""),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isEmpty) Color.Transparent else Color.Black
            )

            // Badge
            val badgeText = badgeFor(summary)
            if (badgeText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColorFor(summary),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun badgeFor(summary: WeekSummary): String {
    if (summary.slotType == null) return "+" // completely empty slot
    if (summary.recipeId == null) {
        // Slot exists but no recipe assigned
        return if (summary.slotType == "work") "-" else "+"
    }
    return when (summary.slotType) {
        "auto_fill" -> "Auto"
        "claimed" -> {
            if (summary.batchGroup != null) {
                "Batch ${summary.batchGroup}/${summary.batchTotal ?: "?"}"
            } else {
                "Cooked"
            }
        }
        "work" -> "Office"
        "leftover" -> "Leftover"
        else -> ""
    }
}

private fun badgeColorFor(summary: WeekSummary): Color {
    return when (summary.slotType) {
        "auto_fill" -> Color(0xFFBBDEFB) // light blue
        "claimed" -> {
            if (summary.batchGroup != null) {
                batchTints[summary.batchGroup % batchTints.size]
            } else {
                Color(0xFFC8E6C9) // light green
            }
        }
        "work" -> Color(0xFFFFF9C4) // light yellow
        "leftover" -> Color(0xFFE1BEE7) // light purple
        else -> Color(0xFFE0E0E0) // grey (empty)
    }
}

private fun buildWeekLabel(start: LocalDate, end: LocalDate): String {
    val sf = WeekViewModel.WEEK_FORMATTER
    return "${start.format(sf)} - ${end.format(sf)}"
}

private fun handleCellClick(
    summary: WeekSummary,
    onNavigateToOverride: () -> Unit,
    onNavigateToRecipeDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    if (summary.slotType == null || summary.recipeId == null) {
        // Empty cell → navigate to OverrideForm
        onNavigateToOverride()
    } else {
        // Filled cell → show recipe name in snackbar
        val name = summary.recipeName ?: summary.recipeId ?: "Recipe"
        scope.launch {
            snackbarHostState.showSnackbar(
                message = name,
                duration = SnackbarDuration.Short
            )
        }
        // To navigate to recipe detail instead, uncomment:
        // onNavigateToRecipeDetail(summary.recipeId!!)
    }
}
