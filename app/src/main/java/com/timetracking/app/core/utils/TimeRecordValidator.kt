package com.timetracking.app.core.utils

import android.util.Log
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import java.util.Calendar
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import java.util.Date

object TimeRecordValidator {
    private const val TAG = "TimeRecordValidator"

    /**
     * Verifica el estado actual y determina qué tipo de registro debe hacerse a continuación
     * MEJORADO: Detecta estados inconsistentes (CHECK_OUT huérfanos)
     */
    fun validateNextAction(records: List<TimeRecord>): Triple<Boolean, RecordType?, String> {
        // Si no hay registros, debe ser una entrada
        if (records.isEmpty()) {
            Log.d(TAG, "No hay registros, siguiente acción: CHECK_IN")
            return Triple(true, RecordType.CHECK_IN, "")
        }

        // Ordenar por fecha
        val sortedRecords = records.sortedBy { it.date }

        // NUEVA VALIDACIÓN: Verificar que los registros sean consistentes
        val inconsistency = detectInconsistency(sortedRecords)
        if (inconsistency != null) {
            Log.w(TAG, "Estado inconsistente detectado: $inconsistency")
            return Triple(false, null, inconsistency)
        }

        val lastRecord = sortedRecords.last()

        Log.d(TAG, "Último registro: ${lastRecord.type} a las ${lastRecord.date}")

        // Determinar acción basada en el último registro
        return when (lastRecord.type) {
            RecordType.CHECK_IN -> {
                Log.d(TAG, "Siguiente acción: CHECK_OUT")
                Triple(true, RecordType.CHECK_OUT, "")
            }
            RecordType.CHECK_OUT -> {
                Log.d(TAG, "Siguiente acción: CHECK_IN")
                Triple(true, RecordType.CHECK_IN, "")
            }
        }
    }

    /**
     * Detecta inconsistencias en los registros
     * Retorna mensaje de error si encuentra problema, null si todo OK
     */
    private fun detectInconsistency(sortedRecords: List<TimeRecord>): String? {
        var expectedType = RecordType.CHECK_IN

        for ((index, record) in sortedRecords.withIndex()) {
            if (record.type != expectedType) {
                Log.e(TAG, "Inconsistencia en índice $index: esperado $expectedType, encontrado ${record.type}")
                return TimeTrackingApp.appContext.getString(
                    R.string.error_inconsistent_records,
                    index + 1
                )
            }
            // Alternar tipo esperado
            expectedType = when (expectedType) {
                RecordType.CHECK_IN -> RecordType.CHECK_OUT
                RecordType.CHECK_OUT -> RecordType.CHECK_IN
            }
        }

        return null // Todo correcto
    }

    // Valida que la hora de salida sea posterior a la entrada
    fun validateCheckOutTime(checkInTime: Date, checkOutTime: Date): Pair<Boolean, String> {
        // Extraer horas y minutos para comparación
        val calIn = Calendar.getInstance().apply { time = checkInTime }
        val calOut = Calendar.getInstance().apply { time = checkOutTime }

        // Obtener los valores en minutos desde el inicio del día para comparar
        val inMinutes = calIn.get(Calendar.HOUR_OF_DAY) * 60 + calIn.get(Calendar.MINUTE)
        val outMinutes = calOut.get(Calendar.HOUR_OF_DAY) * 60 + calOut.get(Calendar.MINUTE)

        return if (outMinutes <= inMinutes) {
            Pair(false, TimeTrackingApp.appContext.getString(R.string.error_exit_after_entry))
        } else {
            Pair(true, "")
        }
    }
}