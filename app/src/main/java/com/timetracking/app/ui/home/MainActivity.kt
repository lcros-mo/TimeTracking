package com.timetracking.app.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.timetracking.app.R
import com.timetracking.app.core.di.ServiceLocator
import com.timetracking.app.core.utils.LanguageUtils
import com.timetracking.app.databinding.ActivityMainBinding
import com.timetracking.app.ui.auth.LoginActivity
import com.timetracking.app.ui.history.HistoryFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isProcessingClick = false

    private val viewModel: MainViewModel by viewModels {
        ServiceLocator.provideMainViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLastState()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.lastCheckText.text = state.lastCheckText

                updateButtonState(state.isCheckedIn)

                state.error?.let {
                    showToast(it)
                    viewModel.clearError()
                }
            }
        }

        viewModel.todayTime.observe(this) { timeStats ->
            binding.todayTimeText.text = timeStats.toString()
        }

        viewModel.weeklyTime.observe(this) { timeStats ->
            binding.weeklyTimeText.text = timeStats.toString()
        }
    }

    private fun setupButtons() {
        binding.checkButton.setOnClickListener {
            if (!isProcessingClick) {
                isProcessingClick = true
                viewModel.handleCheckInOut()

                // Desbloquear después de un tiempo
                binding.checkButton.postDelayed({
                    isProcessingClick = false
                }, 1000) // 1 segundo de bloqueo
            }
        }

        binding.logoutButton.setOnClickListener {
            logout()
        }

        binding.historyButton.setOnClickListener {
            navigateToHistory()
        }

        // Añadir listener para el botón de idioma
        //binding.languageButton.setOnClickListener {
       //     showLanguageDialog()
        //}
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            binding.mainContent.visibility = View.VISIBLE
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateButtonState(isCheckedIn: Boolean) {
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.checkButton.startAnimation(anim)

        binding.checkButton.text = if (isCheckedIn) getString(R.string.check_out) else getString(R.string.check_in)

        val colorFrom = getColor(if (isCheckedIn) R.color.button_entry else R.color.button_exit)
        val colorTo = getColor(if (isCheckedIn) R.color.button_exit else R.color.button_entry)

        val colorAnimation = ValueAnimator.ofObject(
            ArgbEvaluator(),
            colorFrom,
            colorTo
        )

        colorAnimation.duration = 300
        colorAnimation.addUpdateListener { animator ->
            binding.checkButton.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }

    private fun navigateToHistory() {
        binding.mainContent.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, HistoryFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun logout() {
        // Cerrar sesión de Firebase
        FirebaseAuth.getInstance().signOut()

        // Limpiar preferencias de autenticación
        getSharedPreferences("auth_prefs", MODE_PRIVATE).edit().clear().apply()

        showToast(getString(R.string.logout_success))

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }

    /**
     * Muestra el diálogo de selección de idioma
     */
    private fun showLanguageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_selector, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.languageRadioGroup)
        val spanishRadio = dialogView.findViewById<RadioButton>(R.id.spanishRadioButton)
        val catalanRadio = dialogView.findViewById<RadioButton>(R.id.catalanRadioButton)

        // Seleccionar el idioma actual
        val currentLanguage = LanguageUtils.getSelectedLanguage(this)
        when (currentLanguage) {
            "es" -> spanishRadio.isChecked = true
            "ca" -> catalanRadio.isChecked = true
            else -> {
                // Si no hay idioma seleccionado o es el del sistema, comprobar el idioma del sistema
                when (resources.configuration.locales[0].language) {
                    "ca" -> catalanRadio.isChecked = true
                    else -> spanishRadio.isChecked = true // Por defecto español
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Guardar el idioma seleccionado
                val languageCode = when (radioGroup.checkedRadioButtonId) {
                    R.id.spanishRadioButton -> "es"
                    R.id.catalanRadioButton -> "ca"
                    else -> "" // Idioma del sistema
                }

                // Si el idioma ha cambiado, guardarlo y reiniciar la app completamente
                if (languageCode != currentLanguage) {
                    LanguageUtils.saveLanguage(this, languageCode)
                    showConfirmRestartDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()
    }

    /**
     * Muestra un diálogo de confirmación para reiniciar la app
     */
    private fun showConfirmRestartDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.language_changed)
            .setMessage(R.string.restart_required)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                // Reiniciar completamente la aplicación
                LanguageUtils.restartApp(this)
            }
            .setCancelable(false)
            .show()
    }
}