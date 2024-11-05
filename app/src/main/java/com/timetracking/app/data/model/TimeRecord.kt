package com.timetracking.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,
    val type: RecordType,
    val note: String? = null
)

enum class RecordType {
    CHECK_IN,
    CHECK_OUT
}