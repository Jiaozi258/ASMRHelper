package com.asmrhelper.domain.repository

import com.asmrhelper.data.local.db.entity.ImageAlbumEntity
import kotlinx.coroutines.flow.Flow

interface ImageAlbumRepository {
    fun getAll(): Flow<List<ImageAlbumEntity>>
    suspend fun insert(album: ImageAlbumEntity): Long
    suspend fun delete(album: ImageAlbumEntity)
}
