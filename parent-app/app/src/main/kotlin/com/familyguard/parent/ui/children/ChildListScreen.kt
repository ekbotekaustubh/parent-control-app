package com.familyguard.parent.ui.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.EmptyState
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import com.familyguard.parent.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildListScreen(
    onAddChild: () -> Unit,
    onOpenChild: (childId: String) -> Unit,
    onOpenAccessRequests: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ChildListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your children") },
                actions = {
                    IconButton(onClick = onOpenAccessRequests) {
                        Icon(Icons.Outlined.Schedule, contentDescription = "Time requests")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddChild) {
                Icon(Icons.Filled.Add, contentDescription = "Add a child")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(message = state.message, onRetry = viewModel::refresh)
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        EmptyState(
                            title = "No children yet",
                            message = "Add your first child to start setting up healthy screen-time limits.",
                            actionLabel = "Add a child",
                            onAction = onAddChild,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.data, key = { it.id }) { child ->
                                ChildCard(name = child.name, onClick = { onOpenChild(child.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildCard(name: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.ChildCare, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Tap to view status and rules",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
