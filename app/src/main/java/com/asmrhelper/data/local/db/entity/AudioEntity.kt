package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio")
data class AudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String = "",
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0L,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    // 在线歌曲元数据（本地音频文件这些字段均为空/0）
    @ColumnInfo(name = "source_id") val sourceId: String = "",
    @ColumnInfo(name = "source_song_id") val sourceSongId: String? = null,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "album") val album: String = "",
    @ColumnInfo(name = "publish_time") val publishTime: Long = 0L
)
