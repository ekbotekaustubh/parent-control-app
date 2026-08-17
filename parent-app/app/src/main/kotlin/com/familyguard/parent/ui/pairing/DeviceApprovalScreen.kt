package com.familyguard.parent.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.EmptyState
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceApprovalScreen(
    childId: String,
    onApproved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DeviceApprovalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val approved by viewModel.approved.collectAsState()

    LaunchedEffect(childId) { viewModel.load(childId) }
    LaunchedEffect(approved) { if (approved) onApproved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Approve device") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is DeviceApprovalUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is DeviceApprovalUiState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.load(childId) },
                modifier = Modifier.padding(padding),
            )
            is DeviceApprovalUiState.Empty -> EmptyState(
                title = "No device waiting yet",
                message = "Once the child device scans or enters the pairing code, it will show up here.",
                modifier = Modifier.padding(padding),
            )
            is DeviceApprovalUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.pendingDevices, key = { it.id }) { device ->
                    PendingDeviceCard(
                        model = device.model ?: "Unknown device",
                        osVersion = device.osVersion,
                        isApproving = state.approvingDeviceId == device.id,
                        onApprove = { viewModel.approve(childId, device.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingDeviceCard(
    model: String,
    osVersion: String?,
    isApproving: Boolean,
    onApprove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(model, style = MaterialTheme.typography.titleMedium)
                if (osVersion != null) {
                    Text(
                        "Android $osVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = onApprove, enabled = !isApproving) {
                if (isApproving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Approve")
                }
            }
        }
    }
}
