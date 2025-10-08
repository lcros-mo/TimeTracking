package com.timetracking.app.core.utils

/**
 * Constantes centralizadas de la aplicación
 */
object AppConstants {

    // SharedPreferences
    const val PREFS_NAME = "app_settings"
    const val PREF_LANGUAGE = "language_preference"
    const val PREF_LANGUAGE_CHANGED = "language_changed"
    const val PREF_THEME = "theme_preference"

    // Configuración de trabajo
    const val WEEKLY_BASELINE_MINUTES = 2250L // 37.5 horas semanales

    // Networking
    const val RETRY_ATTEMPTS = 3
    const val RETRY_BASE_DELAY_MS = 1000L
    const val SERVER_PROCESSING_DELAY_MS = 2000L

    // Timeouts (en milisegundos)
    const val NETWORK_TIMEOUT_MS = 30000L // 30 segundos
}
