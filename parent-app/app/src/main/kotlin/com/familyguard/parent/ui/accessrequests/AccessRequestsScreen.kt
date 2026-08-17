package com.familyguard.parent.ui.accessrequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.EmptyState
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.shared.dto.AccessRequestResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessRequestsScreen(
    onBack: () -> Unit,
    viewModel: AccessRequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is AccessRequestsUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is AccessRequestsUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            is AccessRequestsUiState.Empty -> EmptyState(
                title = "No requests right now",
                message = "When a child asks for more time on a restricted or limited app, it'll show up here.",
                modifier = Modifier.padding(padding),
            )
            is AccessRequestsUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.requests, key = { it.id }) { request ->
                    AccessRequestCard(
                        request = request,
                        isResolving = state.resolvingRequestId == request.id,
                        onApprove = { viewModel.approve(request.id) },
                        onDeny = { viewModel.deny(request.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessRequestCard(
    request: AccessRequestResponse,
    isResolving: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(request.displayName ?: request.packageName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Asking for ${request.requestedMinutes} more minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isResolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onDeny) { Text("Deny") }
                    OutlinedButton(onClick = onApprove) {
                        Text("Approve ${request.requestedMinutes} min")
                    }
                }
            }
        }
    }
}
