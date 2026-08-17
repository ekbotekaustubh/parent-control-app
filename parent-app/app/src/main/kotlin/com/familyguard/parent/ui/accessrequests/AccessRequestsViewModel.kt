package com.familyguard.parent.ui.accessrequests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.AccessRequestRepository
import com.familyguard.shared.dto.AccessRequestResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Not childId-scoped — mirrors `GET /access-requests` (api-spec.md), which spans every child owned by the parent. */
sealed interface AccessRequestsUiState {
    data object Loading : AccessRequestsUiState
    data class Content(val requests: List<AccessRequestResponse>, val resolvingRequestId: String? = null) : AccessRequestsUiState
    data object Empty : AccessRequestsUiState
    data class Error(val message: String) : AccessRequestsUiState
}

@HiltViewModel
class AccessRequestsViewModel @Inject constructor(
    private val accessRequestRepository: AccessRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccessRequestsUiState>(AccessRequestsUiState.Loading)
    val uiState: StateFlow<AccessRequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AccessRequestsUiState.Loading
            val result = accessRequestRepository.getPendingRequests()
            _uiState.value = result.fold(
                onSuccess = { requests ->
                    if (requests.isEmpty()) AccessRequestsUiState.Empty else AccessRequestsUiState.Content(requests)
                },
                onFailure = { AccessRequestsUiState.Error(it.message ?: "Couldn't load pending requests.") },
            )
        }
    }

    /** Grants exactly what was asked for; a parent who wants to grant a different amount can deny and set a fresh rule instead — keeps this one-tap. */
    fun approve(requestId: String) = resolve(requestId, approve = true)

    fun deny(requestId: String) = resolve(requestId, approve = false)

    private fun resolve(requestId: String, approve: Boolean) {
        val current = _uiState.value
        if (current !is AccessRequestsUiState.Content) return
        viewModelScope.launch {
            _uiState.value = current.copy(resolvingRequestId = requestId)
            val result = accessRequestRepository.resolve(requestId, approve)
            _uiState.value = result.fold(
                onSuccess = {
                    val remaining = current.requests.filterNot { it.id == requestId }
                    if (remaining.isEmpty()) AccessRequestsUiState.Empty else AccessRequestsUiState.Content(remaining)
                },
                onFailure = { error ->
                    AccessRequestsUiState.Error(error.message ?: "Couldn't resolve this request.")
                },
            )
        }
    }
}
