package com.timetracking.app.core.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
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

        /**
         * Actualiza recursos con el idioma seleccionado en tiempo de ejecución
         * y devuelve un nuevo context con la configuración actualizada
         */
        fun updateResources(context: Context, languageCode: String): Context {
            val locale = if (languageCode.isEmpty()) {
                Resources.getSystem().configuration.locales[0]
            } else {
                Locale(languageCode)
            }

            Locale.setDefault(locale)

            val configuration = Configuration(context.resources.configuration)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocales(LocaleList(locale))
                context.createConfigurationContext(configuration)
            } else {
                @Suppress("DEPRECATION")
                configuration.locale = locale
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
                context
            }
        }

        /**
         * Aplica el idioma a una actividad y la recrea para que los cambios surtan efecto
         */
        fun applyLanguageAndRecreate(activity: Activity, languageCode: String) {
            saveLanguage(activity, languageCode)

            // Si es una AppCompatActivity, usar recreate() que es más seguro
            if (activity is AppCompatActivity) {
                // Crear un intent para la misma actividad
                val intent = activity.intent
                activity.finish()
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0) // Sin animación
            } else {
                // Para otras actividades usar recreate() directamente
                activity.recreate()
            }
        }

        /**
         * Establece el idioma base para una actividad (debe llamarse en attachBaseContext)
         */
        fun getLocalizedContext(baseContext: Context): Context {
            val languageCode = getSelectedLanguage(baseContext)
            return updateResources(baseContext, languageCode)
        }

        /**
         * Reinicia completamente la aplicación y borra todas las actividades
         * Este es el método más seguro para aplicar cambios de idioma
         */
        fun restartApp(activity: Activity) {
            // Obtener el intent de inicio de la aplicación
            val packageManager = activity.packageManager
            val intent = packageManager.getLaunchIntentForPackage(activity.packageName)

            // Asegurarse de que se inicia como una nueva tarea
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // Iniciar la actividad de inicio
            activity.finishAffinity() // Cierra todas las actividades de la app
            activity.startActivity(intent)

            // Forzar un reinicio limpio después de un breve retraso
            Handler(Looper.getMainLooper()).postDelayed({
                Runtime.getRuntime().exit(0)
            }, 100)
        }
    }
}