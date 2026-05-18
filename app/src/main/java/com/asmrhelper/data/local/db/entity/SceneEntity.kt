package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val audioFilePath: String = "",
    val bgColorIndex: Int = 0,
    val binauralPresetName: String = "",
    val timerMinutes: Int = 0,
    val noiseType: String = "",
    val spatialMode: String = "",
    val visualizerEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
