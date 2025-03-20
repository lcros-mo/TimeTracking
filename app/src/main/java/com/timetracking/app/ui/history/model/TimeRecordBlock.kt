package com.timetracking.app.ui.history.model

import android.os.Parcelable
import com.timetracking.app.data.model.RecordType
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.utils.DateUtils
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.Calendar

@Parcelize
data class TimeRecordBlock(
    val date: Date,
    val checkIn: TimeRecord,
    val checkOut: TimeRecord?,
    val duration: Long = calculateDuration(checkIn, checkOut)
) : Parcelable {
    companion object {
        private fun calculateDuration(checkIn: TimeRecord, checkOut: TimeRecord?): Long {
            return if (checkOut != null) {
                (checkOut.date.time - checkIn.date.time) / (1000 * 60)
            } else {
                0
            }
        }

        fun createBlocks(records: List<TimeRecord>): List<TimeRecordBlock> {
            val sortedRecords = records.sortedBy { it.date }
            val blocks = mutableListOf<TimeRecordBlock>()

            // Mapa para almacenar entradas sin salida correspondiente
            val pendingCheckIns = mutableMapOf<Long, TimeRecord>()

            // Primera pasada: emparejar entradas y salidas por pares lógicos, no por día
            var i = 0
            while (i < sortedRecords.size) {
                val current = sortedRecords[i]

                if (current.type == RecordType.CHECK_IN) {
                    // Buscar la siguiente salida (incluso si es de otro día)
                    var matchingCheckOut: TimeRecord? = null
                    var j = i + 1

                    while (j < sortedRecords.size) {
                        if (sortedRecords[j].type == RecordType.CHECK_OUT) {
                            matchingCheckOut = sortedRecords[j]
                            break
                        }
                        j++
                    }

                    if (matchingCheckOut != null) {
                        blocks.add(TimeRecordBlock(
                            date = DateUtils.truncateToDay(current.date),
                            checkIn = current,
                            checkOut = matchingCheckOut
                        ))
                        i = j + 1 // Avanzar a después del checkout encontrado
                    } else {
                        // No hay checkout - registro pendiente
                        blocks.add(TimeRecordBlock(
                            date = DateUtils.truncateToDay(current.date),
                            checkIn = current,
                            checkOut = null
                        ))
                        i++
                    }
                } else {
                    // Si encontramos una salida sin entrada, podría ser un error o una salida huérfana
                    // La añadimos como entrada especial para que sea visible
                    blocks.add(TimeRecordBlock(
                        date = DateUtils.truncateToDay(current.date),
                        checkIn = current, // Usamos la salida como entrada para que sea visible
                        checkOut = null
                    ))
                    i++
                }
            }

            return blocks.sortedByDescending { it.date }
        }
    }
}