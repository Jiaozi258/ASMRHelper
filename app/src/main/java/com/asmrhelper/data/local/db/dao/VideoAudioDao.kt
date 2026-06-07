package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.VideoAudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoAudioDao {
    @Query("SELECT * FROM video_audio ORDER BY created_at DESC")
    fun getAll(): Flow<List<VideoAudioEntity>>

    @Query("SELECT * FROM video_audio WHERE is_favorite = 1 ORDER BY created_at DESC")
    fun getFavorites(): Flow<List<VideoAudioEntity>>

    @Query("SELECT * FROM video_audio WHERE id = :id")
    suspend fun getById(id: Long): VideoAudioEntity?

    @Query("SELECT * FROM video_audio WHERE source_url = :url LIMIT 1")
    suspend fun getBySourceUrl(url: String): VideoAudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VideoAudioEntity): Long

    @Update
    suspend fun update(entity: VideoAudioEntity)

    @Query("DELETE FROM video_audio WHERE id = :id")
    suspend fun deleteById(id: Long)
}
