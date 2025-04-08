package com.timetracking.app.core.auth

import android.content.Context
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Base64

class AuthManager(private val context: Context) {

    private val credentialManager by lazy { androidx.credentials.CredentialManager.create(context) }
    private val auth = FirebaseAuth.getInstance()

    private val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(com.timetracking.app.R.string.default_web_client_id))
        .build()

    suspend fun signIn(): AuthResult = withContext(Dispatchers.IO) {
        try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = context
            )

            processCredentialResponse(response)
        } catch (e: GetCredentialException) {
            AuthResult.Error("Error de autenticación: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error inesperado: ${e.message}")
        }
    }

    suspend fun signIn(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()

            val user = result.user
            if (user != null) {
                AuthResult.Success(UserData(
                    email = user.email ?: "",
                    displayName = user.displayName ?: user.email?.substringBefore('@') ?: ""
                ))
            } else {
                AuthResult.Error("No se pudo obtener información del usuario")
            }
        } catch (e: Exception) {
            AuthResult.Error("Error de autenticación: ${e.message}")
        }
    }

    private fun processCredentialResponse(response: GetCredentialResponse): AuthResult {
        val credential = response.credential

        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            // Decodificar manualmente el payload del JWT
            val parts = idToken.split(".")
            if (parts.size != 3) {
                return AuthResult.Error("Token inválido")
            }

            // Decodificar la parte del payload
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val decodedPayload = String(decodedBytes)

            // Parsear JSON
            val jsonObject = JSONObject(decodedPayload)
            val email = jsonObject.optString("email")
            val displayName = jsonObject.optString("name", email.substringBefore('@'))

            if (email.isNotEmpty()) {
                AuthResult.Success(UserData(email, displayName))
            } else {
                AuthResult.Error("No se pudo obtener el correo electrónico")
            }
        } catch (e: GoogleIdTokenParsingException) {
            AuthResult.Error("Error al procesar la credencial: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error al procesar el token: ${e.message}")
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        auth.signOut()
    }

    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }
}

data class UserData(
    val email: String,
    val displayName: String
)

sealed class AuthResult {
    data class Success(val userData: UserData) : AuthResult()
    data class Error(val message: String) : AuthResult()
}