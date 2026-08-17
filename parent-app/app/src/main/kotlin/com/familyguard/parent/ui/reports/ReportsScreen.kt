package com.familyguard.parent.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.shared.dto.ReportAppUsage
import com.familyguard.shared.dto.TopAppUsage
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    childId: String,
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val range by viewModel.range.collectAsState()

    LaunchedEffect(childId) { viewModel.load(childId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is ReportsUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is ReportsUiState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.load(childId) },
                modifier = Modifier.padding(padding),
            )
            is ReportsUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("daily" to "Day", "weekly" to "Week", "monthly" to "Month").forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = range == value,
                                onClick = { viewModel.setRange(childId, value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(label) }
                        }
                    }
                }
                item { InsightsCard(state.insights.currentWeekMinutes, state.insights.previousWeekMinutes, state.insights.weekOverWeekChangePercent, state.insights.topApps) }
                item {
                    Text(
                        "Usage by app (${state.report.startDate} to ${state.report.endDate})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (state.report.byApp.isEmpty()) {
                    item {
                        Text(
                            "No usage recorded for this range yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val maxMinutes = state.report.byApp.maxOf { it.totalMinutes }.coerceAtLeast(1)
                    items(state.report.byApp, key = { it.packageName }) { app -> UsageBar(app, maxMinutes) }
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(
    currentWeekMinutes: Int,
    previousWeekMinutes: Int,
    changePercent: Double?,
    topApps: List<TopAppUsage>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (changePercent == null) {
                    "$currentWeekMinutes min so far (no usage last week to compare)"
                } else {
                    val direction = if (changePercent >= 0) "up" else "down"
                    "$currentWeekMinutes min — $direction ${kotlin.math.abs(changePercent).roundToInt()}% vs last week ($previousWeekMinutes min)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (topApps.isNotEmpty()) {
                Text("Top apps this week", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                topApps.forEach { app ->
                    Text(
                        "${app.displayName ?: app.packageName} — ${app.minutes} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageBar(app: ReportAppUsage, maxMinutes: Int) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(app.displayName ?: app.packageName, style = MaterialTheme.typography.bodyMedium)
            Text("${app.totalMinutes} min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fraction = (app.totalMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary),
            ) {}
        }
    }
}
