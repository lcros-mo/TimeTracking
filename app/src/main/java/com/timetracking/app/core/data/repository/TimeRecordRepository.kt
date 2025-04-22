package com.timetracking.app.core.data.repository

import com.timetracking.app.core.data.db.TimeRecordDao
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.utils.DateTimeUtils
import java.util.*

class TimeRecordRepository(private val timeRecordDao: TimeRecordDao) {
    suspend fun insertRecord(date: Date, type: RecordType, note: String? = null): Long {
        val record = TimeRecord(
            date = DateTimeUtils.clearSeconds(date),
            type = type,
            note = note
        )
        return timeRecordDao.insert(record)
    }

    suspend fun getLastRecord(): TimeRecord? {
        return timeRecordDao.getLastRecord()
    }

    suspend fun getDayRecords(date: Date): List<TimeRecord> {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.time

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.time

        return timeRecordDao.getDayRecords(startOfDay, endOfDay)
    }

    suspend fun getRecordsForWeek(weekStart: Date): List<TimeRecord> {
        val startDate = DateTimeUtils.getStartOfWeek(weekStart)
        val endDate = DateTimeUtils.getEndOfWeek(weekStart)
        return timeRecordDao.getDayRecords(startDate, endDate)
    }

    suspend fun updateRecordTime(recordId: Long, hour: Int, minute: Int): Boolean {
        val record = timeRecordDao.getRecordById(recordId) ?: return false

        val newDate = DateTimeUtils.setTimeToDate(record.date, hour, minute)
        timeRecordDao.update(record.copy(date = newDate))
        return true
    }

    suspend fun markWeekAsExported(weekStart: Date) {
        val startDate = DateTimeUtils.getStartOfWeek(weekStart)
        val endDate = DateTimeUtils.getEndOfWeek(weekStart)
        timeRecordDao.markWeekAsExported(startDate, endDate)
    }

    suspend fun deleteRecord(recordId: Long): Boolean {
        val record = timeRecordDao.getRecordById(recordId) ?: return false
        timeRecordDao.delete(record)
        return true
    }

    suspend fun getRecordsByDateRange(startDate: Date, endDate: Date): List<TimeRecord> {
        return timeRecordDao.getDayRecords(startDate, endDate)
    }

    /**
     * Obtiene las semanas que tienen registros no exportados
     */
    suspend fun getUnexportedWeeks(): List<Date> {
        // Obtener todas las semanas disponibles (hasta 4 semanas atrás)
        val today = Calendar.getInstance().time
        val calendar = Calendar.getInstance()
        val weeks = mutableListOf<Date>()

        for (i in 0 until 4) {
            val weekStart = DateTimeUtils.getStartOfWeek(calendar.time)
            if (weekStart.time <= today.time) {
                val records = getRecordsForWeek(weekStart)

                // Verificar si hay registros no exportados
                val hasUnexportedRecords = records.any { !it.exported }

                if (hasUnexportedRecords) {
                    weeks.add(weekStart)
                }
            }
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
        }

        return weeks
    }

    suspend fun canExportWeek(weekStart: Date): Boolean {
        val startDate = DateTimeUtils.getStartOfWeek(weekStart)
        val endDate = DateTimeUtils.getEndOfWeek(weekStart)

        // Obtener todos los registros de la semana
        val records = timeRecordDao.getDayRecords(startDate, endDate)

        // Verificar si hay al menos un registro y ninguno está marcado como exportado
        return records.isNotEmpty() && records.any { !it.exported }
    }

    suspend fun updateRecordNote(recordId: Long, note: String): Boolean {
        val record = timeRecordDao.getRecordById(recordId) ?: return false

        timeRecordDao.update(record.copy(note = note))
        return true
    }
}
