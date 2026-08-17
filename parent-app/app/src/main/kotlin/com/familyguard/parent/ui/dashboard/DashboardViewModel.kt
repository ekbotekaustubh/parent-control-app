package com.familyguard.parent.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.local.db.DashboardCacheEntity
import com.familyguard.parent.data.local.db.decodeRules
import com.familyguard.parent.data.local.db.decodeUsageToday
import com.familyguard.parent.data.repository.DashboardRepository
import com.familyguard.parent.data.repository.PairingRepository
import com.familyguard.parent.domain.OnlineStatus
import com.familyguard.parent.domain.OnlineStatusCalculator
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.DashboardResponse
import com.familyguard.shared.dto.UsageTodayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    /** Child exists but has never had a device paired to it (`GET /devices` is empty). */
    data object NoDevicePaired : DashboardUiState
    data class Content(
        val deviceId: String?,
        val deviceStatusLabel: String,
        val online: OnlineStatus,
        val lastSeenAt: String?,
        val lastSyncAt: String?,
        val rules: List<AppRuleResponse>,
        val usageToday: List<UsageTodayItem>,
        /** True while showing a cached snapshot that hasn't been confirmed by a fresh fetch yet. */
        val isStale: Boolean,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val pairingRepository: PairingRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _revoked = MutableStateFlow(false)
    val revoked: StateFlow<Boolean> = _revoked.asStateFlow()

    fun load(childId: String) {
        viewModelScope.launch {
            // Instant paint from the local cache (if any) while a fresh fetch is in flight.
            val cached = dashboardRepository.getCachedDashboard(childId)
            if (cached != null) {
                _uiState.value = contentFromCache(cached)
            }
            refresh(childId)
        }
    }

    fun refresh(childId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true

            val devices = pairingRepository.getDevices(childId).getOrNull()
            if (devices != null && devices.isEmpty()) {
                _uiState.value = DashboardUiState.NoDevicePaired
                _isRefreshing.value = false
                return@launch
            }
            // Most relevant device for the revoke action: prefer the currently approved
            // one, else the most recently paired.
            val primaryDeviceId = devices
                ?.sortedWith(compareByDescending { it.approvedAt ?: it.pairedAt ?: "" })
                ?.firstOrNull()
                ?.id

            val result = dashboardRepository.refreshDashboard(childId)
            _uiState.value = result.fold(
                onSuccess = { dashboard -> contentFromResponse(dashboard, primaryDeviceId) },
                onFailure = { error ->
                    val existing = _uiState.value
                    if (existing is DashboardUiState.Content) {
                        existing.copy(isStale = true) // keep showing last-known data
                    } else {
                        DashboardUiState.Error(error.message ?: "Couldn't load the dashboard.")
                    }
                },
            )
            _isRefreshing.value = false
        }
    }

    fun revokeDevice(childId: String, deviceId: String) {
        viewModelScope.launch {
            val result = pairingRepository.revokeDevice(deviceId)
            result.onSuccess {
                _revoked.value = true
                refresh(childId)
            }.onFailure { error ->
                _uiState.value = DashboardUiState.Error(error.message ?: "Couldn't revoke this device.")
            }
        }
    }

    private fun contentFromResponse(dashboard: DashboardResponse, deviceId: String?): DashboardUiState.Content {
        val now = Clock.System.now()
        val lastSeen = dashboard.device.lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return DashboardUiState.Content(
            deviceId = deviceId,
            deviceStatusLabel = dashboard.device.status,
            online = OnlineStatusCalculator.compute(lastSeen, now),
            lastSeenAt = dashboard.device.lastSeenAt,
            lastSyncAt = dashboard.device.lastSyncAt,
            rules = dashboard.rules,
            usageToday = dashboard.usageToday,
            isStale = false,
        )
    }

    private fun contentFromCache(entity: DashboardCacheEntity): DashboardUiState.Content {
        val now = Clock.System.now()
        val lastSeen = entity.lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return DashboardUiState.Content(
            deviceId = null, // resolved by the subsequent refresh(); cache doesn't carry it
            deviceStatusLabel = entity.deviceStatus,
            online = OnlineStatusCalculator.compute(lastSeen, now),
            lastSeenAt = entity.lastSeenAt,
            lastSyncAt = entity.lastSyncAt,
            rules = entity.decodeRules(json),
            usageToday = entity.decodeUsageToday(json),
            isStale = true,
        )
    }
}
