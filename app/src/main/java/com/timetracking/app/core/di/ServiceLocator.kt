package com.timetracking.app.core.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.db.AppDatabase
import com.timetracking.app.core.data.repository.TimeRecordRepository
import com.timetracking.app.core.utils.PDFManager
import com.timetracking.app.ui.history.HistoryViewModel
import com.timetracking.app.ui.home.MainViewModel

object ServiceLocator {

    // Dependencias internas
    private var database: AppDatabase? = null
    private var timeRecordRepository: TimeRecordRepository? = null
    private var pdfManager: PDFManager? = null

    // ViewModels Factories
    private var mainViewModelFactory: ViewModelProvider.Factory? = null
    private var historyViewModelFactory: ViewModelProvider.Factory? = null

    /**
     * Inicializar la base de datos
     */
    fun initialize(app: TimeTrackingApp) {
        database = AppDatabase.getDatabase(app)
    }

    /**
     * Factory para MainViewModel
     */
    fun provideMainViewModelFactory(context: Context): ViewModelProvider.Factory {
        return mainViewModelFactory ?: synchronized(this) {
            mainViewModelFactory ?: object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(getRepository(context)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }.also { mainViewModelFactory = it }
        }
    }

    /**
     * Factory para HistoryViewModel
     */
    fun provideHistoryViewModelFactory(context: Context): ViewModelProvider.Factory {
        return historyViewModelFactory ?: synchronized(this) {
            historyViewModelFactory ?: object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return HistoryViewModel(
                            repository = getRepository(context),
                            pdfManager = getPDFManager(context)
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }.also { historyViewModelFactory = it }
        }
    }

    // Métodos privados para obtener dependencias internas
    private fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(context.applicationContext).also { database = it }
        }
    }

    private fun getRepository(context: Context): TimeRecordRepository {
        return timeRecordRepository ?: synchronized(this) {
            timeRecordRepository ?: TimeRecordRepository(
                getDatabase(context).timeRecordDao()
            ).also { timeRecordRepository = it }
        }
    }

    private fun getPDFManager(context: Context): PDFManager {
        return pdfManager ?: synchronized(this) {
            pdfManager ?: PDFManager(context.applicationContext).also { pdfManager = it }
        }
    }
}