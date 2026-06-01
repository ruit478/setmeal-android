package com.setmeal.ui.override

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

private val dayLabels = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

private val dayShortLabels = listOf(
    "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
)

private val mealOptions = listOf("lunch" to "Lunch", "dinner" to "Dinner")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OverrideFormScreen(
    navController: NavController,
    viewModel: OverrideViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Week of ${uiState.weekStart.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${uiState.weekStart.dayOfMonth}"
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !uiState.isSaving && !uiState.isAutoFilling
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Plan")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Work days section ──────────────────────────────
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Work Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayShortLabels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = index in uiState.workDays,
                            onClick = { viewModel.toggleWorkDay(index) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // ── Claims section ─────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Planned Meals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.addClaim() }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            // ── Claim cards ────────────────────────────────────
            itemsIndexed(uiState.claims) { index, claim ->
                ClaimCard(
                    index = index,
                    claim = claim,
                    workDays = uiState.workDays,
                    onDishNameChanged = { viewModel.updateClaimDishName(claim.id, it) },
                    onDayChanged = { viewModel.updateClaimDayOfWeek(claim.id, it) },
                    onMealChanged = { viewModel.updateClaimMealTime(claim.id, it) },
                    onPortionCountChanged = { viewModel.updateClaimPortionCount(claim.id, it) },
                    onDelete = { viewModel.removeClaim(claim.id) }
                )
            }

            // ── Auto-fill button ───────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.autoFillRemaining() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isAutoFilling && !uiState.isSaving
                ) {
                    if (uiState.isAutoFilling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Auto-fill remaining")
                }
            }

            // ── Error message ──────────────────────────────────
            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Bottom spacer for the bottom bar
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ── Claim card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimCard(
    index: Int,
    claim: Claim,
    workDays: Set<Int>,
    onDishNameChanged: (String) -> Unit,
    onDayChanged: (Int) -> Unit,
    onMealChanged: (String) -> Unit,
    onPortionCountChanged: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: claim index + delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meal ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Dish name (free text)
            OutlinedTextField(
                value = claim.recipeName ?: "",
                onValueChange = { onDishNameChanged(it) },
                label = { Text("Dish *") },
                placeholder = { Text("e.g. Spaghetti Bolognese") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Day of week + meal time side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Day of week dropdown
                var dayExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = dayExpanded,
                    onExpandedChange = { dayExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = dayLabels.getOrElse(claim.dayOfWeek) { "?" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dayExpanded,
                        onDismissRequest = { dayExpanded = false }
                    ) {
                        dayLabels.forEachIndexed { dayIndex, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onDayChanged(dayIndex)
                                    dayExpanded = false
                                }
                            )
                        }
                    }
                }

                // Meal time dropdown (filtered by work day)
                var mealExpanded by remember { mutableStateOf(false) }
                val availableMeals = if (claim.dayOfWeek in workDays) {
                    mealOptions.filter { it.first == "dinner" }
                } else {
                    mealOptions
                }

                ExposedDropdownMenuBox(
                    expanded = mealExpanded,
                    onExpandedChange = { mealExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = mealOptions
                            .firstOrNull { it.first == claim.mealTime }
                            ?.second ?: "?",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Meal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mealExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = mealExpanded,
                        onDismissRequest = { mealExpanded = false }
                    ) {
                        availableMeals.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onMealChanged(key)
                                    mealExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Portion counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Portions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                FilledIconButton(
                    onClick = { onPortionCountChanged(claim.portionCount - 1) },
                    modifier = Modifier.size(36.dp),
                    enabled = claim.portionCount > 1
                ) {
                    Text("−", fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${claim.portionCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "/N",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledIconButton(
                    onClick = { onPortionCountChanged(claim.portionCount + 1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
