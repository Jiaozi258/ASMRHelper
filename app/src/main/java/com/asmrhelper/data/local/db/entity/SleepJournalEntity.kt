package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_journals")
data class SleepJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "start_time_ms") val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms") val endTimeMs: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 0,
    @ColumnInfo(name = "quality") val quality: Int = 0,
    @ColumnInfo(name = "notes") val notes: String = ""
)
