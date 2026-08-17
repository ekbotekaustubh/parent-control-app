package com.familyguard.parent.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.parent.ui.common.ErrorState
import com.familyguard.parent.ui.common.LoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val POLL_INTERVAL_MS = 4000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    childId: String,
    onDevicePendingApproval: () -> Unit,
    onCancel: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Generates the code, then tight-polls (~4s) for the child device to claim it — the
    // loop is scoped to this composable's lifecycle so it's automatically cancelled the
    // moment the parent navigates away (e.g. backs out of pairing).
    LaunchedEffect(childId) {
        viewModel.generateCode(childId)
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            viewModel.pollForDevice(childId)
            if (viewModel.uiState.value is PairingUiState.DeviceFound) break
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is PairingUiState.DeviceFound) onDevicePendingApproval()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair a device") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is PairingUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is PairingUiState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.generateCode(childId) },
                modifier = Modifier.padding(padding),
            )
            is PairingUiState.DeviceFound -> LoadingState(modifier = Modifier.padding(padding)) // brief, navigates away immediately
            is PairingUiState.CodeReady -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    text = "On the child's device, open Family Guard and scan this code",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                val qrBitmap = remember(state.code) {
                    QrCodeGenerator.generate(QrCodeGenerator.pairingUri(state.code))
                }
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Pairing QR code",
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .size(240.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
                Text(
                    text = "No camera? Enter this code instead:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = state.code,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = "This code expires in 10 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Waiting for the device to connect…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
