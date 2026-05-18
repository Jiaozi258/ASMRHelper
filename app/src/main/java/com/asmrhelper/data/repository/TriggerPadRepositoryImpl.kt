package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.TriggerPadDao
import com.asmrhelper.data.local.db.entity.TriggerPadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TriggerPadRepositoryImpl @Inject constructor(
    private val dao: TriggerPadDao
) {
    fun getAll(): Flow<List<TriggerPadEntity>> = dao.getAll()

    suspend fun save(pad: TriggerPadEntity): Long {
        val existing = dao.getBySlot(pad.slotIndex)
        return if (existing != null) {
            dao.update(pad.copy(id = existing.id))
            existing.id
        } else {
            dao.insert(pad)
        }
    }

    suspend fun deleteBySlot(slotIndex: Int) = dao.deleteBySlot(slotIndex)
}
