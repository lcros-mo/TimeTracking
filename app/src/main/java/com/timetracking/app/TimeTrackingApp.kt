package com.timetracking.app

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.timetracking.app.core.data.db.AppDatabase
import com.timetracking.app.core.di.ServiceLocator

class TimeTrackingApp : Application() {

    // La base de datos se inicializa de forma perezosa (lazy)
    val database by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Inicializar el inyector de dependencias
        ServiceLocator.initialize(this)
    }
}