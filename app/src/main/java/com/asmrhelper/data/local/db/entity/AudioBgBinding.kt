package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "audio_bg_binding",
    primaryKeys = ["audio_id", "image_id"]
)
data class AudioBgBinding(
    @ColumnInfo(name = "audio_id") val audioId: Long,
    @ColumnInfo(name = "image_id") val imageId: Long
)
