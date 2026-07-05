package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.PlayHistoryDao
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.domain.repository.PlayHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayHistoryRepositoryImpl @Inject constructor(
    private val dao: PlayHistoryDao
) : PlayHistoryRepository {
    override fun getAll(): Flow<List<PlayHistoryEntity>> = dao.getAll()
    override suspend fun insert(entry: PlayHistoryEntity) = dao.insert(entry)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
