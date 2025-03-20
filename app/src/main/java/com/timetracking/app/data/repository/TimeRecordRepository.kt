package com.timetracking.app.data.repository

import com.timetracking.app.data.database.TimeRecordDao
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.data.model.RecordType
import com.timetracking.app.utils.DateUtils
import java.util.*

class TimeRecordRepository(private val timeRecordDao: TimeRecordDao) {
    suspend fun insertRecord(date: Date, type: RecordType, note: String? = null) {
        val record = TimeRecord(
            date = DateUtils.clearSeconds(date),
            type = type,
            note = note
        )
        timeRecordDao.insert(record)
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
        val startDate = DateUtils.getStartOfWeek(weekStart)
        val endDate = DateUtils.getEndOfWeek(weekStart)
        return timeRecordDao.getDayRecords(startDate, endDate)
    }

    suspend fun updateRecordTime(recordId: Long, hour: Int, minute: Int) {
        timeRecordDao.getRecordById(recordId)?.let { record ->
            val newDate = DateUtils.setTimeToDate(record.date, hour, minute)
            timeRecordDao.update(record.copy(date = newDate))
        }
    }

    suspend fun markWeekAsExported(weekStart: Date) {
        val startDate = DateUtils.getStartOfWeek(weekStart)
        val endDate = DateUtils.getEndOfWeek(weekStart)
        timeRecordDao.markWeekAsExported(startDate, endDate)
    }

    suspend fun deleteRecord(recordId: Long) {
        timeRecordDao.getRecordById(recordId)?.let { record ->
            timeRecordDao.delete(record)
        }
    }
}
