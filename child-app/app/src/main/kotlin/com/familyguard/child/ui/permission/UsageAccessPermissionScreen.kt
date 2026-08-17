package com.familyguard.child.ui.permission

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familyguard.child.R
import com.familyguard.child.service.MonitorForegroundService

/**
 * Explains why Usage Access is needed, launches `Settings.ACTION_USAGE_ACCESS_SETTINGS`
 * (there is no in-app runtime-permission dialog for this special-access permission — see
 * AndroidManifest.xml's comment on `PACKAGE_USAGE_STATS`), and detects the grant when the
 * user returns to the app via `ON_RESUME`, using `UsageStatsPermissionChecker`.
 */
@Composable
fun UsageAccessPermissionScreen(
    onGranted: () -> Unit,
    viewModel: UsageAccessPermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(viewModel.isGranted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = viewModel.isGranted()
                if (granted) {
                    MonitorForegroundService.start(context)
                    onGranted()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            Text(stringResource(R.string.usage_access_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.usage_access_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Text(stringResource(R.string.usage_access_open_settings))
            }
        }
    }
}
