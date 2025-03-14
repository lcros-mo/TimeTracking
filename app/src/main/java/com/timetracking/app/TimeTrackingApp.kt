package com.timetracking.app

import android.app.Application
import com.timetracking.app.data.database.AppDatabase

class TimeTrackingApp : Application() {
    // Haciendo la propiedad database accesible como lazy
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Puedes añadir inicialización adicional aquí si es necesario
    }
}