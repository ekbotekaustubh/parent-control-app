package com.familyguard.parent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGNUP }

data class AuthFormState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
)

/** Explicit Loading/Success/Error states (no Idle-vs-nullable ambiguity in the UI layer). */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Synchronous local-only check (EncryptedSharedPreferences read) used to pick the nav graph's start destination. */
    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun toggleMode() {
        _form.update { it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN) }
        _uiState.value = AuthUiState.Idle
    }

    fun onEmailChange(value: String) = _form.update { it.copy(email = value) }
    fun onPasswordChange(value: String) = _form.update { it.copy(password = value) }
    fun onDisplayNameChange(value: String) = _form.update { it.copy(displayName = value) }

    fun submit() {
        val current = _form.value
        if (current.email.isBlank() || current.password.isBlank() ||
            (current.mode == AuthMode.SIGNUP && current.displayName.isBlank())
        ) {
            _uiState.value = AuthUiState.Error("Please fill in all fields.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = if (current.mode == AuthMode.LOGIN) {
                authRepository.login(current.email.trim(), current.password)
            } else {
                authRepository.signup(current.email.trim(), current.password, current.displayName.trim())
            }
            _uiState.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { AuthUiState.Error(it.message ?: "Something went wrong. Please try again.") },
            )
        }
    }
}
