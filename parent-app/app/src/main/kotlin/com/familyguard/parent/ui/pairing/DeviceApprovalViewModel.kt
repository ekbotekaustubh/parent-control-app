package com.familyguard.parent.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.PairingRepository
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.enums.DeviceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DeviceApprovalUiState {
    data object Loading : DeviceApprovalUiState
    data class Content(val pendingDevices: List<DeviceResponse>, val approvingDeviceId: String? = null) : DeviceApprovalUiState
    data object Empty : DeviceApprovalUiState
    data class Error(val message: String) : DeviceApprovalUiState
}

@HiltViewModel
class DeviceApprovalViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceApprovalUiState>(DeviceApprovalUiState.Loading)
    val uiState: StateFlow<DeviceApprovalUiState> = _uiState.asStateFlow()

    private val _approved = MutableStateFlow(false)
    val approved: StateFlow<Boolean> = _approved.asStateFlow()

    fun load(childId: String) {
        viewModelScope.launch {
            _uiState.value = DeviceApprovalUiState.Loading
            val result = pairingRepository.getDevices(childId)
            _uiState.value = result.fold(
                onSuccess = { devices ->
                    val pending = devices.filter { it.status == DeviceStatus.PENDING_APPROVAL }
                    if (pending.isEmpty()) DeviceApprovalUiState.Empty else DeviceApprovalUiState.Content(pending)
                },
                onFailure = { DeviceApprovalUiState.Error(it.message ?: "Couldn't load devices waiting for approval.") },
            )
        }
    }

    fun approve(childId: String, deviceId: String) {
        val current = _uiState.value
        if (current !is DeviceApprovalUiState.Content) return
        viewModelScope.launch {
            _uiState.value = current.copy(approvingDeviceId = deviceId)
            val result = pairingRepository.approveDevice(deviceId)
            result.fold(
                onSuccess = { _approved.value = true },
                onFailure = { error ->
                    _uiState.value = DeviceApprovalUiState.Error(error.message ?: "Couldn't approve this device.")
                },
            )
        }
    }
}
