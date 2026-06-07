package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_audio")
data class VideoAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "platform") val platform: String = "other",
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
