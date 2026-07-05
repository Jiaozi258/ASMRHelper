package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.ImageAlbumDao
import com.asmrhelper.data.local.db.entity.ImageAlbumEntity
import com.asmrhelper.domain.repository.ImageAlbumRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageAlbumRepositoryImpl @Inject constructor(
    private val dao: ImageAlbumDao
) : ImageAlbumRepository {
    override fun getAll(): Flow<List<ImageAlbumEntity>> = dao.getAll()
    override suspend fun insert(album: ImageAlbumEntity): Long = dao.insert(album)
    override suspend fun delete(album: ImageAlbumEntity) = dao.delete(album)
}
