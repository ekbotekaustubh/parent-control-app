package com.familyguard.parent.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    data object Loading : PairingUiState
    /**
     * Deliberately holds only the raw [code] string, not a rendered QR bitmap: QR rendering
     * (zxing -> android.graphics.Bitmap) is an Android-framework concern kept entirely in
     * PairingScreen (`remember(code) { QrCodeGenerator.generate(...) } `), so this
     * ViewModel — and its unit tests — never touch `android.graphics.Bitmap`.
     */
    data class CodeReady(val code: String, val expiresAt: String) : PairingUiState
    data object DeviceFound : PairingUiState
    data class Error(val message: String) : PairingUiState
}

/**
 * Generates a pairing code + QR and polls the backend for a device to reach
 * `pending_approval` (docs/pairing-security.md steps 1-3, from the parent's side). The
 * actual poll loop is driven by PairingScreen's `LaunchedEffect` (tight foreground poll,
 * auto-cancelled on leaving composition) calling [pollForDevice] repeatedly — this
 * ViewModel exposes the two building blocks ([generateCode], [pollForDevice]) rather than
 * owning the loop itself, so a test can call [pollForDevice] directly without needing to
 * fast-forward a `delay()`.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Loading)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun generateCode(childId: String) {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Loading
            val result = pairingRepository.generateCode(childId)
            _uiState.value = result.fold(
                onSuccess = { response -> PairingUiState.CodeReady(code = response.code, expiresAt = response.expiresAt) },
                onFailure = { PairingUiState.Error(it.message ?: "Couldn't generate a pairing code.") },
            )
        }
    }

    /** One poll cycle. No-op unless currently showing a code (avoids racing [generateCode]). */
    suspend fun pollForDevice(childId: String) {
        if (_uiState.value !is PairingUiState.CodeReady) return
        val device = pairingRepository.findPendingApprovalDevice(childId).getOrNull()
        if (device != null) {
            _uiState.value = PairingUiState.DeviceFound
        }
    }
}
