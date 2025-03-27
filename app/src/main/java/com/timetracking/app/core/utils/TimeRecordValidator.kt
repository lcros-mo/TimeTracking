package com.timetracking.app.core.utils

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
        return if (checkOutTime.before(checkInTime)) {
            Pair(false, "La hora de salida no puede ser anterior a la de entrada")
        } else {
            Pair(true, "")
        }
    }
}