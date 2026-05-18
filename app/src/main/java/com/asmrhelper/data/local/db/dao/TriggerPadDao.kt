package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.TriggerPadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerPadDao {
    @Query("SELECT * FROM trigger_pads ORDER BY slot_index ASC")
    fun getAll(): Flow<List<TriggerPadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pad: TriggerPadEntity): Long

    @Update
    suspend fun update(pad: TriggerPadEntity)

    @Query("DELETE FROM trigger_pads WHERE slot_index = :slotIndex")
    suspend fun deleteBySlot(slotIndex: Int)

    @Query("SELECT * FROM trigger_pads WHERE slot_index = :slotIndex")
    suspend fun getBySlot(slotIndex: Int): TriggerPadEntity?
}
