package com.timetracking.app.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.timetracking.app.R
import com.timetracking.app.core.di.ServiceLocator
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

        // Añadir funcionalidad de reinicio manual con pulsación larga
        binding.mainContent.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reiniciar estado")
                .setMessage("¿Quieres reiniciar el estado de la aplicación? Esto es útil si el botón se ha quedado bloqueado.")
                .setPositiveButton("Reiniciar") { _, _ ->
                    viewModel.resetState()
                    showToast("Estado reiniciado")
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
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

        binding.checkButton.text = if (isCheckedIn) "SALIDA" else "ENTRADA"

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
        lifecycleScope.launch {
            getSharedPreferences("auth_prefs", MODE_PRIVATE).edit().clear().apply()
            showToast("Has cerrado sesión correctamente")
            val intent = Intent(this@MainActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}