package com.timetracking.app.ui.history.model

import com.timetracking.app.data.model.RecordType
import com.timetracking.app.data.model.TimeRecord
import java.util.Date

data class TimeRecordBlock(
    val date: Date,
    val checkIn: TimeRecord,
    val checkOut: TimeRecord?,
    val duration: Long = calculateDuration(checkIn, checkOut)
) {
    companion object {
        fun calculateDuration(checkIn: TimeRecord, checkOut: TimeRecord?): Long {
            return if (checkOut != null) {
                (checkOut.date.time - checkIn.date.time) / (1000 * 60)
            } else {
                0
            }
        }

        fun createBlocks(records: List<TimeRecord>): List<TimeRecordBlock> {
            val sortedRecords = records.sortedBy { it.date }
            val blocks = mutableListOf<TimeRecordBlock>()

            var i = 0
            while (i < sortedRecords.size) {
                val current = sortedRecords[i]

                if (current.type == RecordType.CHECK_IN) {
                    // Buscar la siguiente salida
                    val checkOut = if (i + 1 < sortedRecords.size &&
                        sortedRecords[i + 1].type == RecordType.CHECK_OUT) {
                        sortedRecords[i + 1]
                    } else null

                    blocks.add(TimeRecordBlock(
                        date = current.date,
                        checkIn = current,
                        checkOut = checkOut
                    ))

                    i += if (checkOut != null) 2 else 1
                } else {
                    i++
                }
            }

            return blocks
        }
    }

}