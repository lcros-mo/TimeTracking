package com.timetracking.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "AuthManager"
private const val ALLOWED_DOMAIN = "@grecmallorca.org"

class AuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val sharedPreferences by lazy {
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    }

    suspend fun signIn(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()

            val user = result.user ?: return@withContext AuthResult.Error("Error de autenticación")

            // Verificar dominio
            if (!user.email?.endsWith("@grecmallorca.org")!!) {
                return@withContext AuthResult.Error("Solo se permiten correos con dominio @grecmallorca.org")
            }

            // Obtener token
            val tokenResult = user.getIdToken(true).await()
            val token = tokenResult.token ?: ""
            val expirationTime = System.currentTimeMillis() + 3600000

            // Guardar en SharedPreferences
            saveUserData(user.email ?: "", user.displayName ?: "", token, expirationTime)

            return@withContext AuthResult.Success(UserData(
                email = user.email ?: "",
                displayName = user.displayName ?: user.email?.substringBefore('@') ?: "",
                accessToken = token
            ))
        } catch (e: Exception) {
            return@withContext AuthResult.Error("Error: ${e.message}")
        }
    }

    private fun saveUserData(email: String, displayName: String, token: String, expirationTime: Long) {
        sharedPreferences.edit().apply {
            putString("user_email", email)
            putString("user_name", displayName)
            putString("access_token", token)
            putLong("token_expiry", expirationTime)
            apply()
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        auth.signOut()
        sharedPreferences.edit().clear().apply()
    }

    fun isUserSignedIn(): Boolean {
        val currentUser = auth.currentUser
        val tokenExpiry = sharedPreferences.getLong("token_expiry", 0)
        return currentUser != null && System.currentTimeMillis() < tokenExpiry
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    fun getCurrentUserDisplayName(): String? {
        val user = auth.currentUser
        return user?.displayName ?: user?.email?.substringBefore('@')
    }
}

data class UserData(
    val email: String,
    val displayName: String,
    val accessToken: String = ""
)

sealed class AuthResult {
    data class Success(val userData: UserData) : AuthResult()
    data class Error(val message: String) : AuthResult()
}