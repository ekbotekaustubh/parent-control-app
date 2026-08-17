package com.familyguard.parent.ui.advancedrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedRulesScreen(
    childId: String,
    onBack: () -> Unit,
    viewModel: AdvancedRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var showAddSchedule by remember { mutableStateOf(false) }
    var showAddCategoryLimit by remember { mutableStateOf(false) }

    LaunchedEffect(childId) { viewModel.load(childId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedules & categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is AdvancedRulesUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is AdvancedRulesUiState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.load(childId) },
                modifier = Modifier.padding(padding),
            )
            is AdvancedRulesUiState.Content -> Column(modifier = Modifier.padding(padding)) {
                if (actionError != null) {
                    Snackbar(
                        modifier = Modifier.padding(12.dp),
                        action = { TextButton(onClick = viewModel::clearActionError) { Text("Dismiss") } },
                    ) { Text(actionError!!) }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { SectionHeader(title = "Schedules", onAdd = { showAddSchedule = true }) }
                    if (state.schedules.isEmpty()) {
                        item { Text("No schedules yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(state.schedules, key = { "sched-${it.id}" }) { schedule ->
                            ScheduleCard(schedule, onDelete = { viewModel.deleteSchedule(childId, schedule.id) })
                        }
                    }

                    item { SectionHeader(title = "Category limits", onAdd = { showAddCategoryLimit = true }) }
                    if (state.categoryRules.isEmpty()) {
                        item { Text("No category limits yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(state.categoryRules, key = { "cat-${it.category}" }) { rule ->
                            CategoryRuleCard(rule, onDelete = { viewModel.deleteCategoryRule(childId, rule.category) })
                        }
                    }
                }
            }
        }
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            onDismiss = { showAddSchedule = false },
            onCreate = { name, days, start, end, mode ->
                viewModel.createSchedule(childId, name, days, start, end, mode)
                showAddSchedule = false
            },
        )
    }
    if (showAddCategoryLimit) {
        AddCategoryLimitDialog(
            onDismiss = { showAddCategoryLimit = false },
            onSave = { category, minutes ->
                viewModel.upsertCategoryRule(childId, category, minutes)
                showAddCategoryLimit = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun ScheduleCard(schedule: ScheduleResponse, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${schedule.daysOfWeek.joinToString(", ") { it.name }} · ${schedule.startTime}-${schedule.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (schedule.mode == ScheduleMode.BLOCK) "Everything blocked" else "Only approved apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete schedule")
            }
        }
    }
}

@Composable
private fun CategoryRuleCard(rule: CategoryRuleResponse, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(rule.category, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${rule.dailyLimitMinutes} min/day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete category limit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, days: Set<Weekday>, startTime: String, endTime: String, mode: ScheduleMode) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<Weekday>()) }
    var startTime by remember { mutableStateOf("21:00") }
    var endTime by remember { mutableStateOf("07:00") }
    var mode by remember { mutableStateOf(ScheduleMode.BLOCK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Bedtime)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Weekday.entries.forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                            label = { Text(day.name.take(1)) },
                            modifier = Modifier.padding(end = 2.dp),
                        )
                    }
                }

                Row(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(140.dp),
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .width(140.dp),
                    )
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 12.dp)) {
                    SegmentedButton(
                        selected = mode == ScheduleMode.BLOCK,
                        onClick = { mode = ScheduleMode.BLOCK },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Block all") }
                    SegmentedButton(
                        selected = mode == ScheduleMode.ALLOW_ONLY,
                        onClick = { mode = ScheduleMode.ALLOW_ONLY },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Allow only") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, selectedDays, startTime, endTime, mode) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddCategoryLimitDialog(
    onDismiss: () -> Unit,
    onSave: (category: String, dailyLimitMinutes: Int) -> Unit,
) {
    var category by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category limit") },
        text = {
            Column {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (e.g. social)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Daily limit (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category, minutesText.toIntOrNull() ?: 0) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
