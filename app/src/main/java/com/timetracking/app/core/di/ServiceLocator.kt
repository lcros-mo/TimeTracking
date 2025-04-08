package com.timetracking.app.core.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.google.gson.GsonBuilder
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.auth.AuthManager
import com.timetracking.app.core.data.db.AppDatabase
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.network.AuthApi
import com.timetracking.app.ui.auth.LoginViewModel
import com.timetracking.app.ui.history.HistoryViewModel
import com.timetracking.app.core.utils.PDFManager
import com.timetracking.app.ui.main.MainViewModel
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ServiceLocator {

    // Clientes y APIs
    private var database: AppDatabase? = null
    private var timeRecordRepository: TimeRecordRepository? = null
    private var pdfManager: PDFManager? = null
    private var authApi: AuthApi? = null
    private var authManager: AuthManager? = null

    // ViewModels Factories
    private var mainViewModelFactory: ViewModelProvider.Factory? = null
    private var historyViewModelFactory: ViewModelProvider.Factory? = null
    private var loginViewModelFactory: ViewModelProvider.Factory? = null

    // Inicializar dependencias con el contexto de la aplicación
    fun initialize(app: TimeTrackingApp) {
        database = AppDatabase.getDatabase(app)
    }

    // Obtener la base de datos
    fun provideDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context.applicationContext).also { database = it }
        }
    }

    // Obtener el repositorio de registros de tiempo
    fun provideTimeRecordRepository(context: Context): TimeRecordRepository {
        return timeRecordRepository ?: synchronized(this) {
            timeRecordRepository ?: TimeRecordRepository(
                provideDatabase(context).timeRecordDao()
            ).also { timeRecordRepository = it }
        }
    }

    // Obtener el gestor de PDF
    fun providePDFManager(context: Context): PDFManager {
        return pdfManager ?: synchronized(this) {
            pdfManager ?: PDFManager(context.applicationContext).also { pdfManager = it }
        }
    }

    // Crear cliente HTTP
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .hostnameVerifier { _, _ -> true }
            .sslSocketFactory(
                TrustAllCerts.createSSLSocketFactory(),
                TrustAllCerts.trustManager
            )
            .build()
    }

    // Proveer la API de autenticación
    fun provideAuthApi(): AuthApi {
        return authApi ?: synchronized(this) {
            authApi ?: createAuthApi().also { authApi = it }
        }
    }

    fun provideAuthManager(context: Context): AuthManager {
        return authManager ?: synchronized(this) {
            authManager ?: AuthManager(context.applicationContext).also { authManager = it }
        }
    }

    // Crear la API de autenticación
    private fun createAuthApi(): AuthApi {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://v0-simple-api-for-whitelist.vercel.app/")
            .client(provideOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(AuthApi::class.java)
    }

    // Factory para MainViewModel
    fun provideMainViewModelFactory(context: Context): ViewModelProvider.Factory {
        return mainViewModelFactory ?: synchronized(this) {
            mainViewModelFactory ?: object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(provideTimeRecordRepository(context)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }.also { mainViewModelFactory = it }
        }
    }

    // Factory para HistoryViewModel
    fun provideHistoryViewModelFactory(context: Context): ViewModelProvider.Factory {
        return historyViewModelFactory ?: synchronized(this) {
            historyViewModelFactory ?: object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return HistoryViewModel(provideTimeRecordRepository(context)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }.also { historyViewModelFactory = it }
        }
    }

    // Factory para LoginViewModel
    // Modificar el método provideLoginViewModelFactory
    fun provideLoginViewModelFactory(context: Context): ViewModelProvider.Factory {
        return loginViewModelFactory ?: synchronized(this) {
            loginViewModelFactory ?: object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return LoginViewModel(provideAuthManager(context)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }.also { loginViewModelFactory = it }
        }
    }
}