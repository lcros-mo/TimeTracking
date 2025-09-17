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
                val duration = (checkOut.date.time - checkIn.date.time) / (1000 * 60)
                maxOf(0L, duration) // Evitar duraciones negativas
            } else {
                0
            }
        }

        fun createBlocks(records: List<TimeRecord>): List<TimeRecordBlock> {
            // Agrupar por día para evitar confusiones entre días
            val recordsByDay = records
                .sortedBy { it.date }
                .groupBy { DateTimeUtils.truncateToDay(it.date) }

            val blocks = mutableListOf<TimeRecordBlock>()

            recordsByDay.forEach { (day, dayRecords) ->
                blocks.addAll(createBlocksForDay(day, dayRecords))
            }

            return blocks.sortedByDescending { it.date }
        }

        /**
         * Crea bloques para un día específico - ALGORITMO SIMPLE
         */
        private fun createBlocksForDay(day: Date, dayRecords: List<TimeRecord>): List<TimeRecordBlock> {
            val sortedRecords = dayRecords.sortedBy { it.date }
            val blocks = mutableListOf<TimeRecordBlock>()
            var i = 0

            while (i < sortedRecords.size) {
                val current = sortedRecords[i]

                when (current.type) {
                    RecordType.CHECK_IN -> {
                        // Buscar salida correspondiente
                        val checkOutIndex = findNextCheckOut(sortedRecords, i + 1)

                        val checkOut = if (checkOutIndex != -1) {
                            sortedRecords[checkOutIndex]
                        } else null

                        blocks.add(TimeRecordBlock(
                            date = day,
                            checkIn = current,
                            checkOut = checkOut
                        ))

                        // Avanzar a después de la salida (si existe)
                        i = if (checkOutIndex != -1) checkOutIndex + 1 else i + 1
                    }
                    RecordType.CHECK_OUT -> {
                        // Salida huérfana - crear bloque con entrada ficticia
                        // (Para mantener compatibilidad con UI existente)
                        blocks.add(TimeRecordBlock(
                            date = day,
                            checkIn = current, // UI existente espera esto
                            checkOut = null
                        ))
                        i++
                    }
                }
            }

            return blocks
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
    }
}