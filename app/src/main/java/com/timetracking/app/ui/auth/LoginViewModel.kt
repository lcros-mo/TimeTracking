// Archivo: app/src/main/java/com/timetracking/app/ui/auth/LoginViewModel.kt

package com.timetracking.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.timetracking.app.BuildConfig
import com.timetracking.app.core.network.AuthApi
import com.timetracking.app.core.network.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val error: String? = null
)

class LoginViewModel(private val authApi: AuthApi) : ViewModel() {

    private val ALLOWED_DOMAIN = "@grecmallorca.org"
    private val auth = FirebaseAuth.getInstance()

    // Estado de la UI
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Método para login con email (tu método existente)
    fun login(email: String) {
        _uiState.value = LoginUiState(isLoading = true)

        if (email.isEmpty()) {
            _uiState.value = LoginUiState(error = "Por favor, introduce tu correo")
            return
        }

        if (!email.endsWith(ALLOWED_DOMAIN)) {
            _uiState.value = LoginUiState(error = "Solo se permiten correos con dominio $ALLOWED_DOMAIN")
            return
        }

        val trimmedEmail = email.trim().lowercase()

        viewModelScope.launch {
            try {
                val response = authApi.checkAuthorization(trimmedEmail, BuildConfig.API_KEY)

                if (response.isSuccessful) {
                    val body = response.body()

                    if (body?.authorized == true) {
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            isLoggedIn = true,
                            user = body.user
                        )
                    } else {
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            error = "El correo no está autorizado"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    _uiState.value = LoginUiState(
                        isLoading = false,
                        error = "Error en respuesta HTTP ${response.code()}: $errorBody"
                    )
                }
            } catch (e: IOException) {
                _uiState.value = LoginUiState(
                    isLoading = false,
                    error = "Error de conexión: ${e.message}"
                )
            } catch (e: Exception) {
                _uiState.value = LoginUiState(
                    isLoading = false,
                    error = "Error al iniciar sesión: ${e.message}"
                )
            }
        }
    }

    // Nuevo método para login con Google
    fun signInWithGoogleCredential(credential: AuthCredential) {
        _uiState.value = LoginUiState(isLoading = true)

        viewModelScope.launch {
            try {
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    val email = firebaseUser.email

                    if (email != null && email.endsWith(ALLOWED_DOMAIN)) {
                        // Usuario autorizado con dominio correcto
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            isLoggedIn = true,
                            user = User(
                                email = email,
                                name = firebaseUser.displayName ?: email.substringBefore('@')
                            )
                        )
                    } else {
                        // No tiene dominio autorizado
                        auth.signOut()
                        _uiState.value = LoginUiState(
                            isLoading = false,
                            error = "Solo se permiten correos con dominio $ALLOWED_DOMAIN"
                        )
                    }
                } else {
                    _uiState.value = LoginUiState(
                        isLoading = false,
                        error = "No se pudo obtener información del usuario"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState(
                    isLoading = false,
                    error = "Error de autenticación con Google: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}