package com.weekmenu.ui.grocery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weekmenu.data.Categories

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun displayCategory(key: String): String =
    Categories.GROCERY_LABELS[key.lowercase()] ?: key.replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(
    viewModel: GroceryViewModel = hiltViewModel()
) {
    val manualItems by viewModel.manualItems.collectAsStateWithLifecycle()
    val autoItems by viewModel.autoItems.collectAsStateWithLifecycle()
    val checkedAutoKeys by viewModel.checkedAutoKeys.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val weekEnd by viewModel.weekEnd.collectAsStateWithLifecycle()

    // ── Week navigation header ──
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.previousWeek() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
        }
        TextButton(onClick = { viewModel.resetToCurrentWeek() }) {
            Text(
                text = buildWeekLabel(weekStart, weekEnd),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        IconButton(onClick = { viewModel.nextWeek() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tab Row: From Plan / Manual ──
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("From Plan") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Manual") }
            )
        }

        // ── Content ──
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // ── "From Plan" tab ──
                    if (autoItems.isEmpty()) {
                        item {
                            EmptySectionMessage("No plan-based items.\nAdd recipes to your weekly plan first.")
                        }
                    } else {
                        item {
                            Text(
                                text = "From Plan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        val grouped = autoItems.groupBy { it.category }
                        val sortedCategories = grouped.keys.sortedBy { cat ->
                            val idx = Categories.GROCERY.indexOf(cat.lowercase())
                            if (idx >= 0) idx else 99
                        }

                        sortedCategories.forEach { category ->
                            val items = grouped[category] ?: return@forEach
                            item {
                                CategoryHeader(displayCategory(category))
                            }

                            items(items) { item ->
                                val isChecked = checkedAutoKeys.contains(item.key)
                                AutoGroceryCard(
                                    item = item,
                                    isChecked = isChecked,
                                    onToggle = { viewModel.toggleAutoItem(item) }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // ── "Manual" tab ──
                    if (manualItems.isEmpty()) {
                        item {
                            EmptySectionMessage("No manual items.\nTap + to add one.")
                        }
                    } else {
                        item {
                            Text(
                                text = "Manual",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        val grouped = manualItems.groupBy { it.category }
                        val sortedCategories = grouped.keys.sortedBy { cat ->
                            val idx = Categories.GROCERY.indexOf(cat.lowercase())
                            if (idx >= 0) idx else 99
                        }

                        sortedCategories.forEach { category ->
                            val items = grouped[category] ?: return@forEach
                            item {
                                CategoryHeader(displayCategory(category))
                            }

                            items(items) { item ->
                                ManualGroceryCard(
                                    item = item,
                                    onToggle = { viewModel.toggleManualItem(item) },
                                    onDelete = { viewModel.deleteManualItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom action bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.shareGroceryList(context) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }

            OutlinedButton(
                onClick = {
                    if (selectedTabIndex == 0) {
                        viewModel.clearAutoChecks()
                    } else {
                        showClearConfirmDialog = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (selectedTabIndex == 0) "Uncheck all" else "Clear all")
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }
    }

    if (showAddDialog) {
        AddGroceryItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, quantity, category ->
                viewModel.addManualItem(name, quantity, category)
                showAddDialog = false
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear all manual items?") },
            text = { Text("This will delete all manual items from your list. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearManualItems()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptySectionMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun AutoGroceryCard(
    item: AutoGroceryItem,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isChecked, onCheckedChange = { onToggle() })

            Column(modifier = Modifier.weight(1f)) {
                // Name
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Quantity + source badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.quantity.isNullOrBlank()) {
                        Text(
                            text = item.quantity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Source badge (recipe names)
                    if (item.sourceRecipeNames.isNotEmpty()) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                text = item.sourceRecipeNames.joinToString(", "),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualGroceryCard(
    item: com.weekmenu.data.db.entity.ManualGroceryItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.checked)
                MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() })

            Column(modifier = Modifier.weight(1f)) {
                // Name
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Quantity
                if (!item.quantity.isNullOrBlank()) {
                    Text(
                        text = item.quantity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Item Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroceryItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: String?, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = displayCategory(category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Categories.GROCERY.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(displayCategory(cat)) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, quantity.ifBlank { null }, category) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildWeekLabel(start: LocalDate, end: LocalDate): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    return "${start.format(fmt)} - ${end.format(fmt)}"
}
