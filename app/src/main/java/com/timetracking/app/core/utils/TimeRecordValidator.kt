package com.timetracking.app.core.utils

import android.icu.util.Calendar
import com.timetracking.app.R
import com.timetracking.app.TimeTrackingApp
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.data.model.TimeRecord
import java.util.Date

object TimeRecordValidator {
    //Verifica el estado actual y determina qué tipo de registro debe hacerse a continuación

    fun validateNextAction(records: List<TimeRecord>): Triple<Boolean, RecordType?, String> {
        // Si no hay registros, debe ser una entrada
        if (records.isEmpty()) {
            return Triple(true, RecordType.CHECK_IN, "")
        }

        // Ordenar por fecha
        val sortedRecords = records.sortedBy { it.date }
        val lastRecord = sortedRecords.last()

        // Determinar acción basada en el último registro
        return when (lastRecord.type) {
            RecordType.CHECK_IN -> Triple(true, RecordType.CHECK_OUT, "")
            RecordType.CHECK_OUT -> Triple(true, RecordType.CHECK_IN, "")
        }
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