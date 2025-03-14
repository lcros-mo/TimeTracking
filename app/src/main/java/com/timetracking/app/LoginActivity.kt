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
import com.timetracking.app.network.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {
    private val ALLOWED_DOMAIN = "@grecmallorca.org"

    private lateinit var authApi: AuthApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Configurar Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://v0-simple-api-for-whitelist.vercel.app/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        authApi = retrofit.create(AuthApi::class.java)

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
        val apiKey = BuildConfig.API_KEY.trim() // Eliminar espacios en la API Key

        // 🔹 Log para verificar qué email y API Key se están enviando

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    authApi.checkAuthorization(trimmedEmail, BuildConfig.API_KEY)
                }

                Log.d("API_RESPONSE", "Código de respuesta: ${response.code()}")
                Log.d("API_RESPONSE", "Cuerpo de la respuesta: ${response.body()?.toString()}")

                if (response.isSuccessful && response.body()?.authorized == true) {
                    val user = response.body()?.user

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
                    showToast("El correo no está autorizado")
                    Log.e("API_ERROR", "Error en la respuesta: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Error en la validación", e)
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
