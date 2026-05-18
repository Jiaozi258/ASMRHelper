package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val audioId: Long,
    val name: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
