package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.ImageLibraryDao
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.domain.repository.ImageLibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageLibraryRepositoryImpl @Inject constructor(
    private val dao: ImageLibraryDao
) : ImageLibraryRepository {
    override fun getAll(): Flow<List<ImageLibraryEntity>> = dao.getAll()
    override suspend fun insert(entity: ImageLibraryEntity) = dao.insert(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
