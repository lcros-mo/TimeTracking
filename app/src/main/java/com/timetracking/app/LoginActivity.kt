package com.timetracking.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.SignInButton

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Configurar Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Verificar si hay una sesión activa
        val account = GoogleSignIn.getLastSignedInAccount(this)
        account?.let {
            if (it.email?.endsWith("@grecmallorca.org") == true) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        // Configurar el launcher
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                handleSignInResult(account)
            } catch (e: ApiException) {
                when (e.statusCode) {
                    12500 -> showCustomToast("Solo cuentas del GREC porfavor :)")
                    7 -> showCustomToast("No hay conexión a internet")
                    else -> showCustomToast("Error al iniciar sesión")
                }
            }
        }

        // Configurar el botón
        findViewById<SignInButton>(R.id.signInButton).apply {
            setSize(SignInButton.SIZE_WIDE)
            setOnClickListener { signIn() }
        }
    }

    private fun signIn() {
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount) {
        account.email?.let { email ->
            if (email.endsWith("@grecmallorca.org")) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                googleSignInClient.signOut().addOnCompleteListener {
                    showCustomToast("Solo se permiten correos con dominio @grecmallorca.org")
                }
            }
        }
    }

    private fun showCustomToast(message: String) {
        runOnUiThread {
            val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
    }
}