package com.timetracking.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.google.firebase.FirebaseApp
import com.timetracking.app.core.data.db.AppDatabase
import com.timetracking.app.core.di.ServiceLocator
import com.timetracking.app.core.utils.LanguageUtils

class TimeTrackingApp : Application() {

    // La base de datos se inicializa de forma perezosa (lazy)
    val database by lazy { AppDatabase.getDatabase(this) }

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Aplicar el idioma seleccionado al contexto de la aplicación
        val languageCode = LanguageUtils.getSelectedLanguage(this)
        appContext = LanguageUtils.updateResources(this, languageCode)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Inicializar el inyector de dependencias
        ServiceLocator.initialize(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageUtils.getLocalizedContext(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Aplicar el idioma guardado a la nueva configuración
        val languageCode = LanguageUtils.getSelectedLanguage(this)
        LanguageUtils.updateResources(this, languageCode)
    }
}