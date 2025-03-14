package com.timetracking.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.GsonBuilder
import com.timetracking.app.network.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {
    private val ALLOWED_DOMAIN = "@grecmallorca.org"

    private lateinit var authApi: AuthApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Configurar Retrofit con cliente HTTP mejorado
        setupRetrofit()

        // Verificar si hay sesión guardada
        val sharedPref = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_email", null)

        if (savedEmail != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Configurar el botón de inicio de sesión
        val signInButton = findViewById<SignInButton>(R.id.signInButton)
        signInButton.setOnClickListener {
            showLoginDialog()
        }
    }

    private fun setupRetrofit() {
        // Cliente HTTP con timeouts configurados
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Configuración de Gson más tolerante
        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://v0-simple-api-for-whitelist.vercel.app/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
    }

    private fun showLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_login, null)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.emailInput)

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Configurar botones
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            validateAndLogin(email)
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun validateAndLogin(email: String) {
        if (email.isEmpty()) {
            showToast("Por favor, introduce tu correo")
            return
        }

        if (!email.endsWith(ALLOWED_DOMAIN)) {
            showToast("Solo se permiten correos con dominio $ALLOWED_DOMAIN")
            return
        }

        val trimmedEmail = email.trim().lowercase() // Asegurar que no haya espacios ni mayúsculas

        // Logs de depuración mejorados
        Log.d("AUTH_DEBUG", "Email: $trimmedEmail")
        Log.d("AUTH_DEBUG", "API Key: ${BuildConfig.API_KEY}")

        lifecycleScope.launch {
            try {
                Log.d("AUTH_DEBUG", "Iniciando petición de autorización...")

                val response = withContext(Dispatchers.IO) {
                    try {
                        authApi.checkAuthorization(trimmedEmail, BuildConfig.API_KEY)
                    } catch (e: Exception) {
                        Log.e("AUTH_ERROR", "Error en la llamada API: ${e.javaClass.simpleName}: ${e.message}", e)
                        throw e
                    }
                }

                val statusCode = response.code()
                Log.d("AUTH_DEBUG", "Código de respuesta: $statusCode")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("AUTH_DEBUG", "Respuesta: $body")

                    if (body?.authorized == true) {
                        val user = body.user

                        // Guardar sesión del usuario
                        val sharedPref = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("user_email", user?.email)
                            putString("user_name", user?.name)
                            apply()
                        }

                        showToast("Sesión iniciada correctamente")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Log.e("AUTH_ERROR", "Autorización denegada: ${body?.message}")
                        showToast("El correo no está autorizado")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AUTH_ERROR", "Error en respuesta HTTP $statusCode: $errorBody")
                    showToast("El correo no está autorizado")
                }
            } catch (e: Exception) {
                Log.e("AUTH_ERROR", "Excepción general: ${e.javaClass.simpleName}", e)
                e.printStackTrace()
                showToast("Error al conectar con el servidor")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}
