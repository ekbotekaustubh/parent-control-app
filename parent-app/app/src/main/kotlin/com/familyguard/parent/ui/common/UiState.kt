package com.familyguard.parent.ui.common

/**
 * Generic explicit UI state used by every ViewModel in this app (spec section 18 requires
 * explicit loading/error/empty states rather than ad-hoc nullable/boolean flags).
 * `T` is the screen's success payload; screens with a richer internal state machine (e.g.
 * ui/pairing/PairingViewModel.kt) define their own sealed hierarchy instead but keep the
 * same Loading/Success/Error shape as the outermost variants.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
