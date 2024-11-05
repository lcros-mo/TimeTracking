package com.timetracking.app.data.repository


import com.timetracking.app.data.database.TimeRecordDao
import com.timetracking.app.data.model.TimeRecord
import com.timetracking.app.data.model.RecordType  // Asegúrate de que esta importación es correcta
import java.util.*

class TimeRecordRepository(private val timeRecordDao: TimeRecordDao) {

    suspend fun insertRecord(date: Date, type: RecordType, note: String? = null) {
        val record = TimeRecord(date = date, type = type, note = note)
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
        }
        val startOfDay = calendar.time

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.time

        return timeRecordDao.getRecordsForDay(startOfDay, endOfDay)
    }

    suspend fun updateRecord(record: TimeRecord) {
        timeRecordDao.update(record)
    }
}