package com.timetracking.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.timetracking.app.BuildConfig
import com.timetracking.app.R
import com.timetracking.app.core.di.ServiceLocator
import com.timetracking.app.databinding.ActivityLoginBinding
import com.timetracking.app.databinding.DialogLoginBinding
import com.timetracking.app.ui.home.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels {
        ServiceLocator.provideLoginViewModelFactory()
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

        setupObservers()
        setupUI()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.signInButton.isEnabled = !state.isLoading

                if (state.isLoggedIn && state.user != null) {
                    val sharedPref = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("user_email", state.user.email)
                        putString("user_name", state.user.name)
                        apply()
                    }

                    showToast("Sesión iniciada correctamente")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }

                state.error?.let {
                    showToast(it)
                    viewModel.clearError()
                }
            }
        }
    }

    private fun setupUI() {
        binding.signInButton.setOnClickListener {
            showLoginDialog()
        }

        //binding.versionText.text = "Versión ${BuildConfig.VERSION_NAME}"
    }

    private fun showLoginDialog() {
        val dialogBinding = DialogLoginBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.loginButton.setOnClickListener {
            val email = dialogBinding.emailInput.text.toString().trim()
            viewModel.login(email)
            dialog.dismiss()
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}