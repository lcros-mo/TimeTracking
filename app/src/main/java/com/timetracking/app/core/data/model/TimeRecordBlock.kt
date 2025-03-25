package com.timetracking.app.core.data.model

import android.os.Parcelable
import com.timetracking.app.core.utils.DateTimeUtils
import kotlinx.parcelize.Parcelize
import java.util.Date

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

            // Primera pasada: emparejar entradas y salidas por pares lógicos
            var i = 0
            while (i < sortedRecords.size) {
                val current = sortedRecords[i]

                if (current.type == RecordType.CHECK_IN) {
                    // Buscar la siguiente salida
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
                            date = DateTimeUtils.truncateToDay(current.date),
                            checkIn = current,
                            checkOut = matchingCheckOut
                        ))
                        i = j + 1 // Avanzar a después del checkout encontrado
                    } else {
                        // No hay checkout - registro pendiente
                        blocks.add(TimeRecordBlock(
                            date = DateTimeUtils.truncateToDay(current.date),
                            checkIn = current,
                            checkOut = null
                        ))
                        i++
                    }
                } else {
                    // Si encontramos una salida sin entrada, la añadimos como entrada especial
                    blocks.add(TimeRecordBlock(
                        date = DateTimeUtils.truncateToDay(current.date),
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