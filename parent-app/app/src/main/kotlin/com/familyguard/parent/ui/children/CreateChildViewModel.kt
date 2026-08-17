package com.familyguard.parent.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class CreateChildViewModel @Inject constructor(
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChildResponse>?>(null)
    val uiState: StateFlow<UiState<ChildResponse>?> = _uiState.asStateFlow()

    fun createChild(name: String, birthYear: Int?) {
        if (name.isBlank()) {
            _uiState.value = UiState.Error("Please enter a name.")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = childRepository.createChild(name.trim(), birthYear)
            _uiState.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Couldn't create this child profile.") },
            )
        }
    }
}
