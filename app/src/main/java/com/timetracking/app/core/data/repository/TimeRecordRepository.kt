package com.timetracking.app.core.data.repository

import com.timetracking.app.core.data.db.OvertimeBalanceDao
import com.timetracking.app.core.data.db.TimeRecordDao
import com.timetracking.app.core.data.model.OvertimeBalance
import com.timetracking.app.core.data.model.TimeRecord
import com.timetracking.app.core.data.model.RecordType
import com.timetracking.app.core.utils.DateTimeUtils
import java.util.*

class TimeRecordRepository(private val timeRecordDao: TimeRecordDao, private val overtimeBalanceDao: OvertimeBalanceDao) {
    suspend fun insertRecord(date: Date, type: RecordType, note: String? = null): Long {
        val record = TimeRecord(
            date = DateTimeUtils.clearSeconds(date),
            type = type,
            note = note,
            exported = false
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

    suspend fun updateRecordNote(recordId: Long, note: String): Boolean {
        val record = timeRecordDao.getRecordById(recordId) ?: return false

        timeRecordDao.update(record.copy(note = note))
        return true
    }

    suspend fun getOvertimeBalance(): Long {
        val balance = overtimeBalanceDao.getBalance()
        return balance?.totalMinutes ?: 0L
    }

    suspend fun addToOvertimeBalance(weeklyMinutes: Long) {
        // Calcular diferencia contra 37.5h (2250 minutos)
        val overtimeMinutes = weeklyMinutes - com.timetracking.app.core.utils.AppConstants.WEEKLY_BASELINE_MINUTES

        val currentBalance = getOvertimeBalance()
        val newBalance = OvertimeBalance(id = 1, totalMinutes = currentBalance + overtimeMinutes)

        overtimeBalanceDao.insertOrUpdateBalance(newBalance)
    }
}
