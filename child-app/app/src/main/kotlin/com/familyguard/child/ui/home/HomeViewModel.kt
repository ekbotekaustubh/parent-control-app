package com.familyguard.child.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.child.data.local.SyncMetadataStore
import com.familyguard.child.data.local.TokenStore
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.child.service.UsageStatsPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class HomeUiState(
    val deviceId: String? = null,
    val monitoringActive: Boolean = false,
    val lastSyncAt: String? = null,
)

/**
 * Deliberately minimal — per the spec, this app has no dashboard (rules editing, usage
 * charts, etc. are the parent app's job; see `docs/architecture.md`). This screen only
 * shows enough for the child (or a parent glancing at the child's phone) to confirm
 * pairing + monitoring are healthy.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val permissionChecker: UsageStatsPermissionChecker,
    private val syncMetadataStore: SyncMetadataStore,
    private val api: ChildApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Re-reads local status (permission, last-sync) and pings the backend so "online" reflects reality. */
    fun refresh() {
        _uiState.update { buildState() }
        viewModelScope.launch {
            // Heartbeat is fire-and-forget here: it updates devices.last_seen_at server-side
            // for the parent's dashboard, but this screen's own state comes from local data
            // (permission check + last successful usage-sync), matching hard requirement #2
            // that enforcement/monitoring status never depends on a live network round trip.
            runCatching { api.heartbeat() }
        }
    }

    private fun buildState(): HomeUiState = HomeUiState(
        deviceId = tokenStore.deviceId,
        monitoringActive = permissionChecker.isGranted(),
        lastSyncAt = syncMetadataStore.lastUsageSyncAtMillis.takeIf { it > 0 }?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        },
    )
}
