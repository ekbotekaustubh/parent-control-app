package com.familyguard.child.ui.restriction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.child.data.repository.AccessRequestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RequestMoreTimeState {
    data object Idle : RequestMoreTimeState()
    data object Submitting : RequestMoreTimeState()
    data object Sent : RequestMoreTimeState()
    data class Failed(val message: String) : RequestMoreTimeState()
}

/**
 * Backs the "Ask for N more minutes" button on [RestrictionScreen] — see that file's KDoc
 * and `docs/roadmap.md`'s "Access requests" section for why this exists. A fixed increment
 * ([REQUESTED_MINUTES]) rather than a picker keeps this a one-tap action; the parent still
 * chooses how many minutes to actually grant when resolving it (`POST
 * /access-requests/{id}/resolve`, parent-app side).
 */
@HiltViewModel
class RestrictionViewModel @Inject constructor(
    private val accessRequestRepository: AccessRequestRepository,
) : ViewModel() {

    private val _requestState = MutableStateFlow<RequestMoreTimeState>(RequestMoreTimeState.Idle)
    val requestState: StateFlow<RequestMoreTimeState> = _requestState.asStateFlow()

    fun requestMoreTime(packageName: String) {
        if (_requestState.value == RequestMoreTimeState.Submitting) return
        _requestState.update { RequestMoreTimeState.Submitting }
        viewModelScope.launch {
            accessRequestRepository.requestMoreTime(packageName, REQUESTED_MINUTES)
                .onSuccess { _requestState.update { RequestMoreTimeState.Sent } }
                .onFailure { e ->
                    _requestState.update { RequestMoreTimeState.Failed(e.message ?: "Request failed.") }
                }
        }
    }

    companion object {
        const val REQUESTED_MINUTES = 15
    }
}
