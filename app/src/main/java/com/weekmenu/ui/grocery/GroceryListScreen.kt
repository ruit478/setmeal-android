package com.weekmenu.ui.grocery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weekmenu.data.Categories
import java.time.LocalDate

private fun displayCategory(key: String): String =
    Categories.GROCERY_LABELS[key.lowercase()] ?: key.replaceFirstChar { it.uppercase() }

@Composable
fun GroceryListScreen(
    viewModel: GroceryViewModel = hiltViewModel()
) {
    val autoItems by viewModel.autoItems.collectAsStateWithLifecycle()
    val checkedKeys by viewModel.checkedKeys.collectAsStateWithLifecycle()
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val weekEnd by viewModel.weekEnd.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Week navigation header ──
        WeekNavHeader(
            weekStart = weekStart,
            weekEnd = weekEnd,
            onPrevious = { viewModel.previousWeek() },
            onNext = { viewModel.nextWeek() }
        )

        // ── Content ──
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!hasPlan) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No plan for this week.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (autoItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ingredients found for this week's meals.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val grouped = autoItems.groupBy { it.category }
                val sortedCategories = grouped.keys.sortedBy { cat ->
                    val idx = Categories.GROCERY.indexOf(cat.lowercase())
                    if (idx >= 0) idx else 99
                }

                sortedCategories.forEach { category ->
                    val items = grouped[category] ?: return@forEach
                    item {
                        Text(
                            text = displayCategory(category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                        )
                    }

                    items(items) { item ->
                        val isChecked = checkedKeys.contains(item.key)
                        AutoGroceryCard(
                            item = item,
                            isChecked = isChecked,
                            onToggle = { viewModel.toggleItem(item) }
                        )
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
                onClick = { viewModel.clearChecked() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Uncheck all")
            }
        }
    }
}

// ── Week navigation header ──

@Composable
private fun WeekNavHeader(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
            }

            Text(
                text = buildWeekLabel(weekStart, weekEnd),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
            }
        }
        HorizontalDivider()
    }
}

// ── AutoGroceryCard ──

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
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.quantity.isNullOrBlank()) {
                        Text(
                            text = item.quantity,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

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

// ── Helpers ──

private fun buildWeekLabel(start: LocalDate, end: LocalDate): String {
    val sf = GroceryViewModel.WEEK_FORMATTER
    return "${start.format(sf)} - ${end.format(sf)}"
}
