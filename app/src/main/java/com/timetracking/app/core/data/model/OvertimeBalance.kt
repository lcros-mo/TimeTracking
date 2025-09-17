package com.timetracking.app.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overtime_balance")
data class OvertimeBalance(
    @PrimaryKey
    val id: Int = 1, // Solo un registro para el balance total
    val totalMinutes: Long = 0 // Balance total en minutos (puede ser negativo)
)