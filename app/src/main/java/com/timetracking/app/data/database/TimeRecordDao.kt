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

    @Query("""
        SELECT * 
        FROM time_records 
        WHERE date >= :startDay AND date < :endDay 
        ORDER BY date ASC
    """)
    suspend fun getDayRecords(startDay: Date, endDay: Date): List<TimeRecord>

    @Query("SELECT * FROM time_records WHERE id = :recordId")
    suspend fun getRecordById(recordId: Long): TimeRecord?

    @Query("UPDATE time_records SET exported = 1 WHERE date >= :weekStart AND date < :weekEnd")
    suspend fun markWeekAsExported(weekStart: Date, weekEnd: Date)
}
