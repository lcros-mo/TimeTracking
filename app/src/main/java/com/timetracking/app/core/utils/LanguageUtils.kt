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
        private const val DEFAULT_LANGUAGE = "ca"  // Catalán por defecto

        private fun getPreferences(context: Context) =
            context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Guarda el idioma seleccionado en las preferencias
         */
        fun saveLanguage(context: Context, languageCode: String) {
            getPreferences(context).edit()
                .putString(AppConstants.PREF_LANGUAGE, languageCode)
                .apply()
        }

        /**
         * Obtiene el idioma guardado en las preferencias
         */
        fun getSelectedLanguage(context: Context): String {
            return getPreferences(context)
                .getString(AppConstants.PREF_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
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
            // Método más confiable para reiniciar la app
            val intent = Intent(activity, com.timetracking.app.ui.auth.LoginActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            // Guardar que necesitamos aplicar el idioma
            getPreferences(activity)
                .edit()
                .putBoolean(AppConstants.PREF_LANGUAGE_CHANGED, true)
                .apply()

            activity.startActivity(intent)
            activity.finishAffinity() // Mejor que Runtime.exit(0)
        }
    }
}