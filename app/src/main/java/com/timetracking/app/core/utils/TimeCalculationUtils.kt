package com.timetracking.app.core.utils

import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import java.util.*

/**
 * Utilidades centralizadas para cálculo de tiempo
 */
object TimeCalculationUtils {

    /**
     * Calcula tiempo trabajado para una lista de registros
     */
    fun calculateWorkingMinutes(
        records: List<TimeRecord>,
        includeInProgressToday: Boolean = false
    ): Long {
        // Solo registros no exportados
        val activeRecords = records.filter { !it.exported }

        // Agrupar por día para procesar cada día por separado
        val recordsByDay = activeRecords.groupBy {
            DateTimeUtils.truncateToDay(it.date)
        }

        var totalMinutes = 0L

        recordsByDay.forEach { (day, dayRecords) ->
            totalMinutes += calculateDayMinutes(dayRecords, day, includeInProgressToday)
        }

        return totalMinutes
    }

    /**
     * Calcula tiempo trabajado para un día específico
     */
    private fun calculateDayMinutes(
        dayRecords: List<TimeRecord>,
        day: Date,
        includeInProgressToday: Boolean
    ): Long {
        val sortedRecords = dayRecords.sortedBy { it.date }
        var totalMinutes = 0L
        var i = 0

        // Emparejar entradas y salidas de forma SIMPLE
        while (i < sortedRecords.size) {
            val current = sortedRecords[i]

            when (current.type) {
                RecordType.CHECK_IN -> {
                    // Buscar la siguiente salida
                    val checkOutIndex = findNextCheckOut(sortedRecords, i + 1)

                    if (checkOutIndex != -1) {
                        // Par completo entrada-salida
                        val checkOut = sortedRecords[checkOutIndex]
                        val duration = (checkOut.date.time - current.date.time) / (1000 * 60)

                        if (duration > 0) { // Evitar duraciones negativas
                            totalMinutes += duration
                        }

                        i = checkOutIndex + 1 // Saltar a después de la salida
                    } else if (includeInProgressToday && isToday(day)) {
                        // Entrada sin salida, solo contar si es HOY
                        val duration = (Date().time - current.date.time) / (1000 * 60)
                        if (duration > 0 && duration < 24 * 60) { // Máximo 24h por seguridad
                            totalMinutes += duration
                        }
                        i++
                    } else {
                        i++
                    }
                }
                RecordType.CHECK_OUT -> {
                    // Salida huérfana, ignorar
                    i++
                }
            }
        }

        return totalMinutes
    }

    /**
     * Encuentra el índice de la siguiente salida
     */
    private fun findNextCheckOut(records: List<TimeRecord>, startIndex: Int): Int {
        for (i in startIndex until records.size) {
            if (records[i].type == RecordType.CHECK_OUT) {
                return i
            }
        }
        return -1
    }

    /**
     * Verifica si una fecha es hoy
     */
    private fun isToday(date: Date): Boolean {
        val today = DateTimeUtils.truncateToDay(Date())
        return DateTimeUtils.truncateToDay(date) == today
    }
}