package com.asmrhelper.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val audioCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
