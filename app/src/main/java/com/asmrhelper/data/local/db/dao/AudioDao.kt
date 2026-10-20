package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.AudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    // 仅本地音频（source_id 为空）；在线歌曲只通过歌单关联表出现在歌单里，
    // 不混入音频库/统计/背景绑定等本地音频列表。
    @Query("SELECT * FROM audio WHERE source_id = '' ORDER BY added_at DESC")
    fun getAllAudio(): Flow<List<AudioEntity>>

    @Query("SELECT * FROM audio WHERE source_id = '' AND is_favorite = 1 ORDER BY added_at DESC")
    fun getFavorites(): Flow<List<AudioEntity>>

    @Query("SELECT * FROM audio WHERE id = :id")
    suspend fun getById(id: Long): AudioEntity?

    @Query("SELECT * FROM audio WHERE file_path = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): AudioEntity?

    @Query("SELECT * FROM audio WHERE source_id = :sourceId AND source_song_id = :sourceSongId LIMIT 1")
    suspend fun getBySource(sourceId: String, sourceSongId: String): AudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioList: List<AudioEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audio: AudioEntity): Long

    @Update
    suspend fun update(audio: AudioEntity)

    @Query("DELETE FROM audio WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM audio WHERE source_id = '' AND (file_path LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%')")
    suspend fun search(keyword: String): List<AudioEntity>
}
