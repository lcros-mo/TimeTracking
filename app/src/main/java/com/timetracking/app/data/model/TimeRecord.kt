// app/src/main/java/com/timetracking/app/data/model/TimeRecord.kt
package com.timetracking.app.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

@Entity(tableName = "time_records")
@Parcelize
data class TimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Date,
    val type: RecordType,
    val note: String? = null,
    val exported: Boolean = false
) : Parcelable

enum class RecordType {
    CHECK_IN,
    CHECK_OUT
}