package com.timetracking.app.data.database

import androidx.room.*
import com.timetracking.app.data.model.TimeRecord
import java.util.Date

@Dao
interface TimeRecordDao {
    @Query("SELECT * FROM time_records ORDER BY date DESC")
    suspend fun getAllRecords(): List<TimeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TimeRecord)

    @Update
    suspend fun update(record: TimeRecord)

    @Delete
    suspend fun delete(record: TimeRecord)

    @Query("SELECT * FROM time_records ORDER BY date DESC LIMIT 1")
    suspend fun getLastRecord(): TimeRecord?

    @Query("SELECT * FROM time_records WHERE date >= :startOfDay AND date < :endOfDay ORDER BY date ASC")
    suspend fun getRecordsForDay(startOfDay: Date, endOfDay: Date): List<TimeRecord>
}