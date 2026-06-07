package com.asmrhelper.domain.model

data class VideoAudio(
    val id: Long = 0,
    val title: String,
    val platform: String = "other",
    val sourceUrl: String,
    val filePath: String,
    val coverPath: String? = null,
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
