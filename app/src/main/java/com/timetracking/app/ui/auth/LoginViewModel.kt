package com.timetracking.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetracking.app.core.auth.AuthManager
import com.timetracking.app.core.auth.AuthResult
import com.timetracking.app.core.network.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val error: String? = null
)

class LoginViewModel(private val authManager: AuthManager) : ViewModel() {

    private val ALLOWED_DOMAIN = "@grecmallorca.org"

    // Estado de la UI
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn() {
        _uiState.value = LoginUiState(isLoading = true)

        viewModelScope.launch {
            when (val result = authManager.signIn()) {
                is AuthResult.Success -> {
                    if (result.userData.email.endsWith(ALLOWED_DOMAIN)) {
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            isLoggedIn = true,
                            user = User(
                                email = result.userData.email,
                                name = result.userData.displayName
                            )
                        )
                    } else {
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            error = "Solo se permiten correos con dominio $ALLOWED_DOMAIN"
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.value = LoginUiState(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}