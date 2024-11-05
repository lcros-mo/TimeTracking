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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.timetracking.app.data.model.RecordType
import com.timetracking.app.data.repository.TimeRecordRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var repository: TimeRecordRepository
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isCheckedIn = false
    private lateinit var checkButton: MaterialButton
    private lateinit var lastCheckText: TextView
    private lateinit var todayTimeText: TextView
    private lateinit var userNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = TimeRecordRepository((application as TimeTrackingApp).database.timeRecordDao())

        initializeViews()
        setupGoogleSignIn()
        setupButtons()
        checkLastStatus()
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

    private fun checkLastStatus() {
        lifecycleScope.launch {
            val lastRecord = repository.getLastRecord()
            isCheckedIn = lastRecord?.type == RecordType.CHECK_IN
            updateUI()
            updateTodayTime()
        }
    }

    private fun handleCheckInOut() {
        val currentTime = Date()
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = formatter.format(currentTime)

        isCheckedIn = !isCheckedIn

        lifecycleScope.launch {
            if (isCheckedIn) {
                repository.insertRecord(currentTime, RecordType.CHECK_IN)
                showToast("Entrada registrada: $timeString")
                lastCheckText.text = "Último fichaje: Entrada a las $timeString"
            } else {
                repository.insertRecord(currentTime, RecordType.CHECK_OUT)
                showToast("Salida registrada: $timeString")
                lastCheckText.text = "Último fichaje: Salida a las $timeString"
            }
            updateTodayTime()
        }

        updateUI()
    }

    private fun updateTodayTime() {
        lifecycleScope.launch {
            val todayRecords = repository.getDayRecords(Date())
            var totalMinutes = 0L

            // Asegurar que los registros están ordenados por fecha
            val sortedRecords = todayRecords.sortedBy { it.date }

            var i = 0
            while (i < sortedRecords.size - 1) {
                val current = sortedRecords[i]
                val next = sortedRecords[i + 1]

                if (current.type == RecordType.CHECK_IN && next.type == RecordType.CHECK_OUT) {
                    val diffInMillis = next.date.time - current.date.time
                    totalMinutes += diffInMillis / (1000 * 60)
                    i += 2
                } else {
                    i++
                }
            }

            // Si el último registro es un CHECK_IN, añadir el tiempo hasta ahora
            if (sortedRecords.lastOrNull()?.type == RecordType.CHECK_IN) {
                val diffInMillis = Date().time - sortedRecords.last().date.time
                totalMinutes += diffInMillis / (1000 * 60)
            }

            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            todayTimeText.text = String.format("%dh %dm", hours, minutes)
        }
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