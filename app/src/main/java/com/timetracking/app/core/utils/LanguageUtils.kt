package com.timetracking.app.core.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.os.Process
import java.util.*

/**
 * Clase de utilidades para gestionar el idioma de la aplicación
 */
class LanguageUtils {
    companion object {
        private const val LANGUAGE_PREFERENCE = "language_preference"
        private const val DEFAULT_LANGUAGE = ""  // Idioma del sistema

        /**
         * Guarda el idioma seleccionado en las preferencias
         */
        fun saveLanguage(context: Context, languageCode: String) {
            val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            preferences.edit().putString(LANGUAGE_PREFERENCE, languageCode).apply()
        }

        /**
         * Obtiene el idioma guardado en las preferencias
         */
        fun getSelectedLanguage(context: Context): String {
            val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            return preferences.getString(LANGUAGE_PREFERENCE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        }

        /**
         * Configura el idioma en la configuración especificada
         */
        fun setLocale(config: Configuration, languageCode: String): Configuration {
            val locale = if (languageCode.isEmpty()) {
                Resources.getSystem().configuration.locales[0]
            } else {
                Locale(languageCode)
            }

            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }

            return config
        }

        fun restartApp(activity: Activity) {
            // Obtener el intent de inicio de la aplicación
            val packageManager = activity.packageManager
            val intent = packageManager.getLaunchIntentForPackage(activity.packageName)

            // Asegurarse de que se inicia como una nueva tarea
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK)

            // Finalizar la actividad actual
            activity.finish()

            // Iniciar la actividad de inicio
            activity.startActivity(intent)

            // Terminar el proceso para asegurar un reinicio limpio
            Runtime.getRuntime().exit(0)
        }
    }
}