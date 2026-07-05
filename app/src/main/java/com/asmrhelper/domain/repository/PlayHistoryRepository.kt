package com.asmrhelper.domain.repository

import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

interface PlayHistoryRepository {
    fun getAll(): Flow<List<PlayHistoryEntity>>
    suspend fun insert(entry: PlayHistoryEntity)
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
