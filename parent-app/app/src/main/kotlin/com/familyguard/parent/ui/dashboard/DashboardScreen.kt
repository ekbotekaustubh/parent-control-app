package com.familyguard.parent.ui.dashboard

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.domain.OnlineStatus
import com.familyguard.parent.ui.common.ConfirmationDialog
import com.familyguard.parent.ui.common.EmptyState
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.shared.dto.UsageTodayItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    childId: String,
    onPairDevice: () -> Unit,
    onEditRules: () -> Unit,
    onBackToChildList: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val revoked by viewModel.revoked.collectAsState()
    var showRevokeConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(childId) { viewModel.load(childId) }
    LaunchedEffect(revoked) { if (revoked) onPairDevice() } // revoked -> needs re-pairing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBackToChildList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val content = uiState as? DashboardUiState.Content
                    if (content?.deviceId != null) {
                        IconButton(onClick = { showRevokeConfirmation = true }) {
                            Icon(Icons.Outlined.PhonelinkErase, contentDescription = "Revoke device")
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(childId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> LoadingState()
                is DashboardUiState.Error -> ErrorState(message = state.message, onRetry = { viewModel.refresh(childId) })
                is DashboardUiState.NoDevicePaired -> EmptyState(
                    title = "No device paired yet",
                    message = "Pair this child's device to start seeing status, rules, and usage here.",
                    actionLabel = "Pair a device",
                    onAction = onPairDevice,
                )
                is DashboardUiState.Content -> DashboardContent(
                    state = state,
                    onEditRules = onEditRules,
                )
            }
        }
    }

    val content = uiState as? DashboardUiState.Content
    if (showRevokeConfirmation && content?.deviceId != null) {
        ConfirmationDialog(
            title = "Revoke this device?",
            message = "The device will lose access immediately and will need to be paired again from scratch. This can't be undone.",
            confirmLabel = "Revoke",
            onConfirm = {
                showRevokeConfirmation = false
                viewModel.revokeDevice(childId, content.deviceId)
            },
            onDismiss = { showRevokeConfirmation = false },
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    onEditRules: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { StatusCard(state) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today's usage", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Edit rules",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onEditRules)
                        .padding(4.dp),
                )
            }
        }
        if (state.usageToday.isEmpty()) {
            item {
                EmptyState(
                    title = "No rules set yet",
                    message = "Set a rule for an app to start tracking today's usage against a limit.",
                    actionLabel = "Set a rule",
                    onAction = onEditRules,
                )
            }
        } else {
            items(state.usageToday, key = { it.packageName }) { usage -> UsageCard(usage) }
        }
    }
}

@Composable
private fun StatusCard(state: DashboardUiState.Content) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnlineDot(state.online)
                Text(
                    text = when (state.online) {
                        OnlineStatus.ONLINE -> "Online"
                        OnlineStatus.OFFLINE -> "Offline"
                        OnlineStatus.UNKNOWN -> "Not yet connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (state.isStale) {
                    Text(
                        text = " (last known)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Last seen: ${relativeTimeLabel(state.lastSeenAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Last synced: ${relativeTimeLabel(state.lastSyncAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnlineDot(status: OnlineStatus) {
    val color = when (status) {
        OnlineStatus.ONLINE -> MaterialTheme.colorScheme.tertiary
        OnlineStatus.OFFLINE -> MaterialTheme.colorScheme.error
        OnlineStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun UsageCard(usage: UsageTodayItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (usage.isOverLimit) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = usage.displayName ?: usage.packageName,
                style = MaterialTheme.typography.titleMedium,
            )
            val limit = usage.dailyLimitMinutes
            if (limit != null && limit > 0) {
                val fraction = (usage.durationMinutes.toFloat() / limit).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    color = if (usage.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${usage.durationMinutes} of $limit min today" +
                        if (usage.isOverLimit) " — over limit" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${usage.durationMinutes} min today (blocked)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
