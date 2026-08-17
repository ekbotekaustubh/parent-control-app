package com.familyguard.child.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.child.data.repository.PairingRepository
import com.familyguard.child.work.WorkScheduler
import com.familyguard.shared.enums.DeviceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PairingUiState {
    data object EnteringCode : PairingUiState()
    data object Claiming : PairingUiState()
    data class ClaimFailed(val message: String) : PairingUiState()
    data object WaitingForApproval : PairingUiState()
    data object Revoked : PairingUiState()
    data object ExchangingTokens : PairingUiState()
    data object Paired : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairingUiState>(
        if (pairingRepository.isPaired()) PairingUiState.Paired else PairingUiState.EnteringCode,
    )
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    /** Accepts either a scanned `familyguard://pair?code=XXXXXXXX` URI or a manually typed 8-char code. */
    fun submitCode(rawValue: String) {
        val code = pairingRepository.extractCode(rawValue)
        if (code == null) {
            _uiState.update { PairingUiState.ClaimFailed("Enter a valid 8-character code, or scan the QR code again.") }
            return
        }
        claim(code)
    }

    private fun claim(code: String) {
        _uiState.update { PairingUiState.Claiming }
        viewModelScope.launch {
            pairingRepository.claim(code)
                .onSuccess { _uiState.update { PairingUiState.WaitingForApproval } }
                .onFailure { e ->
                    _uiState.update { PairingUiState.ClaimFailed(e.message ?: "Couldn't claim that code.") }
                }
        }
    }

    /**
     * Tight foreground poll loop per docs/pairing-security.md (~4s interval). Launched from
     * a `LaunchedEffect` in `WaitingForApprovalScreen`, which cancels this coroutine
     * automatically when the composable leaves composition (screen navigated away from) —
     * this function itself just loops until a terminal state is reached or the
     * calling coroutine is cancelled.
     */
    suspend fun pollUntilResolved(onTokensReady: () -> Unit) {
        while (true) {
            val result = pairingRepository.pollStatus()
            result.onSuccess { status ->
                when (status) {
                    DeviceStatus.PENDING_APPROVAL -> _uiState.update { PairingUiState.WaitingForApproval }
                    DeviceStatus.APPROVED -> {
                        _uiState.update { PairingUiState.ExchangingTokens }
                        exchangeAndFinish(onTokensReady)
                        return
                    }
                    DeviceStatus.REVOKED -> {
                        _uiState.update { PairingUiState.Revoked }
                        return
                    }
                }
            }.onFailure {
                // Transient network hiccup while polling — stay in WaitingForApproval and
                // just try again next tick rather than surfacing a scary error for a blip.
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun exchangeAndFinish(onTokensReady: () -> Unit) {
        pairingRepository.exchangeToken()
            .onSuccess {
                workScheduler.scheduleAll(expediteRuleSync = true)
                _uiState.update { PairingUiState.Paired }
                onTokensReady()
            }
            .onFailure { e ->
                _uiState.update { PairingUiState.Error(e.message ?: "Couldn't finish pairing. Please try again.") }
            }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 4_000L
    }
}
