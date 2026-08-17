package com.familyguard.child.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.familyguard.child.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR scan (CameraX + ML Kit barcode scanning) of the `familyguard://pair?code=XXXXXXXX`
 * URI, plus a manual text-entry fallback field for devices without a working camera —
 * per `docs/pairing-security.md` and per `docs/testing-plan.md`'s note that emulator camera
 * feeds are unreliable, so manual entry is the practical path there too.
 */
@Composable
fun PairingEntryScreen(
    viewModel: PairingViewModel,
    onClaimed: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var manualCode by remember { mutableStateOf("") }
    var scanningEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(uiState) {
        if (uiState is PairingUiState.WaitingForApproval) onClaimed()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.pairing_subtitle), style = MaterialTheme.typography.bodyLarge)

            QrScannerView(
                enabled = scanningEnabled && uiState !is PairingUiState.Claiming,
                onCodeScanned = { value ->
                    scanningEnabled = false
                    viewModel.submitCode(value)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Text(stringResource(R.string.pairing_manual_label), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = manualCode,
                onValueChange = { manualCode = it },
                placeholder = { Text(stringResource(R.string.pairing_manual_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.submitCode(manualCode) },
                enabled = manualCode.isNotBlank() && uiState !is PairingUiState.Claiming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pairing_manual_submit))
            }

            when (val state = uiState) {
                is PairingUiState.Claiming -> CircularProgressIndicator()
                is PairingUiState.ClaimFailed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    LaunchedEffect(state) { scanningEnabled = true }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun QrScannerView(
    enabled: Boolean,
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    if (!hasCameraPermission) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pairing_camera_permission_rationale))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.pairing_scan_button))
            }
        }
        return
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider, previewView, lifecycleOwner, cameraExecutor, enabled, onCodeScanned)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    enabled: Boolean,
    onCodeScanned: (String) -> Unit,
) {
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val scannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = BarcodeScanning.getClient(scannerOptions)

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    analysis.setAnalyzer(executor) { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage == null || !enabled) {
            imageProxy.close()
            return@setAnalyzer
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue?.let(onCodeScanned)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    runCatching {
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }
}
