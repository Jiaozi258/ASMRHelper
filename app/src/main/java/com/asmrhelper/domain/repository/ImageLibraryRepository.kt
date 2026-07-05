package com.asmrhelper.domain.repository

import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import kotlinx.coroutines.flow.Flow

interface ImageLibraryRepository {
    fun getAll(): Flow<List<ImageLibraryEntity>>
    suspend fun insert(entity: ImageLibraryEntity): Long
    suspend fun deleteById(id: Long)
}
