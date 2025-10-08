package com.timetracking.app.ui.auth

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.timetracking.app.R
import com.timetracking.app.core.utils.LanguageUtils
import com.timetracking.app.ui.home.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguageUtils.getSelectedLanguage(newBase)
        val config = Configuration(newBase.resources.configuration)
        LanguageUtils.setLocale(config, languageCode)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkLanguageChange()

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Registrar el ActivityResultLauncher antes de cualquier uso
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null) {
                        firebaseAuthWithGoogle(account.idToken!!)
                    } else {
                        Toast.makeText(this, getString(R.string.error_loading_records, "No se pudo obtener la cuenta de Google"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: ApiException) {
                    Log.e("LoginActivity", "Google sign in failed", e)
                    Toast.makeText(this, getString(R.string.error_loading_records, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }

        if (auth.currentUser != null) {
            Log.d("LoginActivity", "Usuario ya autenticado: ${auth.currentUser?.email}")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.googleSignInButton).setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d("LoginActivity", "Login con Firebase OK: ${user?.email}")
                    Toast.makeText(this, getString(R.string.welcome) + " " + user?.displayName, Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.e("LoginActivity", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, getString(R.string.error_loading_records, "Fallo al autenticar con Firebase"), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkLanguageChange() {
        val prefs = getSharedPreferences(com.timetracking.app.core.utils.AppConstants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(com.timetracking.app.core.utils.AppConstants.PREF_LANGUAGE_CHANGED, false)) {
            // Limpiar el flag
            prefs.edit().putBoolean(com.timetracking.app.core.utils.AppConstants.PREF_LANGUAGE_CHANGED, false).apply()

            // Mostrar mensaje de confirmación
            Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
        }
    }
}