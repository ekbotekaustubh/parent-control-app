package com.familyguard.parent.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.AuthRepository
import com.familyguard.parent.data.repository.ChildRepository
import com.familyguard.parent.ui.common.UiState
import com.familyguard.shared.dto.ChildResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChildListViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<ChildResponse>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ChildResponse>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    init {
        // Instant paint from cache, then a network refresh below replaces it.
        viewModelScope.launch {
            childRepository.observeCachedChildren().collect { cached ->
                if (cached.isNotEmpty() && _uiState.value !is UiState.Success) {
                    _uiState.value = UiState.Success(cached)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = childRepository.refreshChildren()
            _uiState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { error ->
                    // Keep showing stale cached content instead of clobbering it with an
                    // error screen, if we have any.
                    val existing = _uiState.value
                    if (existing is UiState.Success && existing.data.isNotEmpty()) existing
                    else UiState.Error(error.message ?: "Couldn't load your children.")
                },
            )
            _isRefreshing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.value = true
        }
    }
}
