package com.familyguard.child.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyguard.child.R

/**
 * "Waiting for parent approval" per docs/pairing-security.md's confirmation-UX
 * requirement. The `LaunchedEffect(Unit)` poll loop is automatically cancelled by Compose
 * when this composable leaves composition (e.g. back navigation, or process death) — no
 * manual cleanup needed, unlike a raw Service/Thread-based poll would require.
 */
@Composable
fun WaitingForApprovalScreen(
    viewModel: PairingViewModel,
    onApprovedAndTokensExchanged: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.pollUntilResolved(onTokensReady = onApprovedAndTokensExchanged)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val state = uiState) {
                is PairingUiState.Revoked -> {
                    Text(stringResource(R.string.pairing_error_generic), style = MaterialTheme.typography.headlineMedium)
                }
                is PairingUiState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.waiting_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.waiting_subtitle), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
