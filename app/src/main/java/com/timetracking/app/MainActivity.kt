package com.timetracking.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private var isCheckedIn = false
    private var checkInTime: Date? = null
    private var todayTotalMinutes = 0L
    private lateinit var checkButton: MaterialButton
    private lateinit var lastCheckText: TextView
    private lateinit var todayTimeText: TextView
    private lateinit var userNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupGoogleSignIn()
        setupButtons()
        updateUI()
    }

    private fun initializeViews() {
        checkButton = findViewById(R.id.checkButton)
        lastCheckText = findViewById(R.id.lastCheckText)
        todayTimeText = findViewById(R.id.todayTimeText)
        userNameText = findViewById(R.id.userNameText)
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Obtener el nombre del usuario
        GoogleSignIn.getLastSignedInAccount(this)?.let { account ->
            userNameText.text = "Bienvenido/a, ${account.givenName}"
        }
    }

    private fun setupButtons() {
        // Botón de fichaje
        checkButton.setOnClickListener {
            handleCheckInOut()
        }

        // Botón de logout
        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                showToast("Has cerrado sesión correctamente")
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

        // Botón de historial
        findViewById<MaterialButton>(R.id.historyButton).setOnClickListener {
            showToast("Próximamente: Historial de fichajes")
        }
    }

    private fun handleCheckInOut() {
        val currentTime = Date()
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = formatter.format(currentTime)

        isCheckedIn = !isCheckedIn

        if (isCheckedIn) {
            checkInTime = currentTime
            showToast("Entrada registrada: $timeString")
            lastCheckText.text = "Último fichaje: Entrada a las $timeString"
        } else {
            checkInTime?.let { startTime ->
                // Calcular tiempo transcurrido
                val diffInMillis = currentTime.time - startTime.time
                val diffInMinutes = diffInMillis / (1000 * 60)
                todayTotalMinutes += diffInMinutes

                updateTodayTime()
            }
            showToast("Salida registrada: $timeString")
            lastCheckText.text = "Último fichaje: Salida a las $timeString"
            checkInTime = null
        }

        updateUI()
    }

    private fun updateTodayTime() {
        val hours = todayTotalMinutes / 60
        val minutes = todayTotalMinutes % 60
        todayTimeText.text = String.format("%dh %dm", hours, minutes)
    }

    private fun updateUI() {
        val anim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)

        checkButton.startAnimation(anim)
        checkButton.text = if (isCheckedIn) "SALIDA" else "ENTRADA"

        val colorFrom = if (isCheckedIn)
            getColor(R.color.button_entry)
        else
            getColor(R.color.button_exit)

        val colorTo = if (isCheckedIn)
            getColor(R.color.button_exit)
        else
            getColor(R.color.button_entry)

        val colorAnimation = ValueAnimator.ofObject(
            ArgbEvaluator(),
            colorFrom,
            colorTo
        )

        colorAnimation.duration = 300 // milisegundos
        colorAnimation.addUpdateListener { animator ->
            checkButton.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }

    private fun showToast(message: String) {
        Toast.makeText(
            applicationContext,
            message,
            Toast.LENGTH_LONG
        ).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}