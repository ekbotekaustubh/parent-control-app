package com.familyguard.parent.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.shared.enums.RuleType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    childId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: RuleEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()

    LaunchedEffect(childId) { viewModel.load(childId) }
    LaunchedEffect(uiState) {
        if (uiState is RuleEditorUiState.Saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set a rule") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is RuleEditorUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is RuleEditorUiState.Error -> Column(modifier = Modifier.padding(padding)) {
                ErrorState(message = state.message, onRetry = { viewModel.load(childId) })
            }
            else -> RuleEditorForm(
                modifier = Modifier.padding(padding),
                form = form,
                isSaving = state is RuleEditorUiState.Saving,
                onSelectApp = viewModel::selectApp,
                onRuleTypeChange = viewModel::setRuleType,
                onLimitChange = viewModel::setDailyLimitMinutesText,
                onSave = { viewModel.save(childId) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorForm(
    modifier: Modifier,
    form: RuleEditorFormState,
    isSaving: Boolean,
    onSelectApp: (CommonApp) -> Unit,
    onRuleTypeChange: (RuleType) -> Unit,
    onLimitChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("Which app?", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommonApp.entries.forEach { app ->
                FilterChip(
                    selected = form.selectedApp == app,
                    onClick = { onSelectApp(app) },
                    label = { Text(app.displayName) },
                )
            }
        }

        Text("What should happen?", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            SegmentedButton(
                selected = form.ruleType == RuleType.BLOCK,
                onClick = { onRuleTypeChange(RuleType.BLOCK) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Block entirely") }
            SegmentedButton(
                selected = form.ruleType == RuleType.ALLOW,
                onClick = { onRuleTypeChange(RuleType.ALLOW) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Allow with a daily limit") }
        }

        if (form.ruleType == RuleType.ALLOW) {
            OutlinedTextField(
                value = form.dailyLimitMinutesText,
                onValueChange = onLimitChange,
                label = { Text("Daily limit (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Save rule")
            }
        }
    }
}
