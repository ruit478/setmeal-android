package com.setmeal.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val dayLabels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sab", "Dom")

@Composable
fun WeekOverviewScreen(
    onNavigateToOverride: () -> Unit,
    onNavigateToBatch: (String) -> Unit,
    viewModel: WeekViewModel = hiltViewModel()
) {
    val weekStart by viewModel.currentWeekStart.collectAsStateWithLifecycle()
    val hasPlan by viewModel.hasPlan.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Week navigation header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previousWeek() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Semana anterior")
            }

            Text(
                text = "Semana de ${weekStart.dayOfMonth} ${monthName(weekStart.monthValue)}",
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = { viewModel.nextWeek() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Próxima semana")
            }
        }

        if (!hasPlan) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nenhum plano para esta semana",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToOverride) {
                        Text("Criar plano")
                    }
                }
            }
        } else {
            // Week grid header: day labels
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // TODO: Add slots grid when plan data is available
            // For Phase 1, this is a placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Grid de refeições (Phase 3)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* onNavigateToBatch needs a planId */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver dias de cozinha")
                }

                Button(
                    onClick = onNavigateToOverride,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Editar plano")
                }
            }
        }
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Jan"
    2 -> "Fev"
    3 -> "Mar"
    4 -> "Abr"
    5 -> "Mai"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Ago"
    9 -> "Set"
    10 -> "Out"
    11 -> "Nov"
    12 -> "Dez"
    else -> ""
}
