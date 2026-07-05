package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_library")
data class ImageLibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)
