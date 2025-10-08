package com.timetracking.app.core.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.model.OvertimeBalance

@Database(
    entities = [TimeRecord::class, OvertimeBalance::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timeRecordDao(): TimeRecordDao
    abstract fun overtimeBalanceDao(): OvertimeBalanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Añadir la columna 'exported' que falta
                db.execSQL(
                    "ALTER TABLE time_records ADD COLUMN exported INTEGER NOT NULL DEFAULT 0"
                )
                Log.i("DATABASE", "✅ MIGRACIÓN 1->2 COMPLETADA - DATOS PRESERVADOS")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Crear tabla para balance de horas extras
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `overtime_balance` (`id` INTEGER NOT NULL, `totalMinutes` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                // Insertar registro inicial con balance 0
                db.execSQL("INSERT OR IGNORE INTO overtime_balance (id, totalMinutes) VALUES (1, 0)")
                Log.i("DATABASE", "✅ MIGRACIÓN 2->3 COMPLETADA - Tabla overtime_balance creada")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetracking_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}