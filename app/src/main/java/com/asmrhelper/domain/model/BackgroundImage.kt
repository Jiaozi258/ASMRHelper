package com.asmrhelper.domain.model

data class BackgroundImage(
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)
