package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.SceneDao
import com.asmrhelper.data.local.db.entity.SceneEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneRepository @Inject constructor(
    private val dao: SceneDao
) {
    fun getAll(): Flow<List<SceneEntity>> = dao.getAll()

    suspend fun save(scene: SceneEntity): Long = dao.insert(scene)

    suspend fun delete(scene: SceneEntity) = dao.delete(scene)
}
