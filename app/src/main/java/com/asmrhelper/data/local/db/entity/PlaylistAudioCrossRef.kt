package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "playlist_audio_cross_ref",
    primaryKeys = ["playlist_id", "audio_id"]
)
data class PlaylistAudioCrossRef(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "audio_id") val audioId: Long
)
