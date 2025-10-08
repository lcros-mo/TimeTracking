package com.timetracking.app.core.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Clase de utilidades para gestionar el tema de la aplicación
 */
class ThemeUtils {
    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
        private const val DEFAULT_THEME = THEME_SYSTEM

        private fun getPreferences(context: Context) =
            context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Guarda el tema seleccionado en las preferencias
         */
        fun saveTheme(context: Context, theme: String) {
            getPreferences(context).edit()
                .putString(AppConstants.PREF_THEME, theme)
                .apply()
        }

        /**
         * Obtiene el tema guardado en las preferencias
         */
        fun getSelectedTheme(context: Context): String {
            return getPreferences(context)
                .getString(AppConstants.PREF_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        }

        /**
         * Aplica el tema seleccionado
         */
        fun applyTheme(theme: String) {
            when (theme) {
                THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        /**
         * Aplica el tema guardado
         */
        fun applySavedTheme(context: Context) {
            val theme = getSelectedTheme(context)
            applyTheme(theme)
        }
    }
}
