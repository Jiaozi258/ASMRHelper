package com.asmrhelper.domain.model

data class Audio(
    val id: Long = 0,
    val title: String,
    val artist: String = "",
    val filePath: String,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
