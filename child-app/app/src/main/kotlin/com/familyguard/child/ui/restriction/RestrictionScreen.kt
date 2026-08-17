package com.familyguard.child.ui.restriction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.child.R

/**
 * "App Restricted" cover screen. Deliberately friendly, not surveillance-coded: no red/
 * black alarm styling, no "you got caught" tone — just a calm explanation, matching the
 * spec's UX-tone requirement (see `ui/theme/Color.kt`).
 *
 * Includes an "Ask for more minutes" action (`docs/roadmap.md`'s "Access requests" section)
 * that files a request via [RestrictionViewModel] for the owning parent to approve/deny in
 * the parent app; the child never sees whether/when it's resolved from this screen (there's
 * no live status here) — the app just becomes usable again once approved and the next
 * rule-sync picks up the change, matching this slice's honest "not real-time" design.
 */
@Composable
fun RestrictionScreen(
    isLimitExceeded: Boolean,
    reason: String,
    dailyLimitMinutes: Int,
    usedMinutes: Int,
    packageName: String,
    onGoHome: () -> Unit,
    viewModel: RestrictionViewModel = hiltViewModel(),
) {
    val requestState by viewModel.requestState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(stringResource(R.string.restriction_title), style = MaterialTheme.typography.headlineMedium)

            val message = reason.ifBlank {
                if (isLimitExceeded) {
                    stringResource(R.string.restriction_limit_message)
                } else {
                    stringResource(R.string.restriction_blocked_message)
                }
            }
            Text(message, style = MaterialTheme.typography.bodyLarge)

            if (isLimitExceeded && dailyLimitMinutes > 0) {
                Text(
                    "Today's limit: $dailyLimitMinutes min · Used: $usedMinutes min",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.restriction_time_remaining), style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = onGoHome, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.restriction_go_home))
            }

            if (packageName.isNotBlank()) {
                RequestMoreTimeAction(
                    state = requestState,
                    onRequest = { viewModel.requestMoreTime(packageName) },
                )
            }
        }
    }
}

@Composable
private fun RequestMoreTimeAction(state: RequestMoreTimeState, onRequest: () -> Unit) {
    when (state) {
        is RequestMoreTimeState.Idle -> {
            TextButton(onClick = onRequest, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(
                        R.string.restriction_request_more_time,
                        RestrictionViewModel.REQUESTED_MINUTES,
                    ),
                )
            }
        }
        is RequestMoreTimeState.Submitting -> {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
        is RequestMoreTimeState.Sent -> {
            Text(
                stringResource(R.string.restriction_request_sent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        is RequestMoreTimeState.Failed -> {
            Text(
                stringResource(R.string.restriction_request_failed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            TextButton(onClick = onRequest) {
                Text(
                    stringResource(
                        R.string.restriction_request_more_time,
                        RestrictionViewModel.REQUESTED_MINUTES,
                    ),
                )
            }
        }
    }
}
