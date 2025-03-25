package com.timetracking.app

import android.app.Application
import com.timetracking.app.core.data.db.AppDatabase
import com.timetracking.app.core.di.ServiceLocator

class TimeTrackingApp : Application() {

    // La base de datos se inicializa de forma perezosa (lazy)
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        // Inicializar el inyector de dependencias
        ServiceLocator.initialize(this)
    }
}