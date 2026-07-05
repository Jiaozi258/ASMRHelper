package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioTitle: String,
    val audioArtist: String,
    val filePath: String,
    val playedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)
