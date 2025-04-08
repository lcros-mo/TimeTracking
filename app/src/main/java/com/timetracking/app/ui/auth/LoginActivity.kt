package com.timetracking.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.timetracking.app.R
import com.timetracking.app.databinding.ActivityLoginBinding
import com.timetracking.app.ui.home.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPref = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_email", null)

        if (savedEmail != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        configureGoogleSignIn()
        setupUI()
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupUI() {
        binding.signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            // Verificar que el email pertenece al dominio permitido
            val email = account?.email
            if (email != null && email.endsWith("@grecmallorca.org")) {
                // Guardar información del usuario en SharedPreferences
                val sharedPref = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("user_email", email)
                    putString("user_name", account.displayName ?: email.substringBefore('@'))
                    apply()
                }

                showToast("Sesión iniciada correctamente")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                showToast("Solo se permiten correos con dominio @grecmallorca.org")
                googleSignInClient.signOut()
            }
        } catch (e: ApiException) {
            showToast("Error al iniciar sesión: ${e.statusCode}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}