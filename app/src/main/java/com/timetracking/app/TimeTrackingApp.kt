package com.timetracking.app

import android.app.Application
import com.timetracking.app.data.database.AppDatabase

class TimeTrackingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
    }
}