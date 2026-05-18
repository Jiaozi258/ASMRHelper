package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundImageDao {
    @Query("SELECT * FROM background_image ORDER BY added_at DESC")
    fun getAllImages(): Flow<List<BackgroundImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: BackgroundImageEntity): Long

    @Query("DELETE FROM background_image WHERE id = :imageId")
    suspend fun deleteImage(imageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bindAudioToImage(binding: AudioBgBinding)

    @Query("DELETE FROM audio_bg_binding WHERE audio_id = :audioId AND image_id = :imageId")
    suspend fun unbindAudioFromImage(audioId: Long, imageId: Long)

    @Query("""
        SELECT bi.* FROM background_image bi
        INNER JOIN audio_bg_binding b ON bi.id = b.image_id
        WHERE b.audio_id = :audioId
        LIMIT 1
    """)
    suspend fun getBindingForAudio(audioId: Long): BackgroundImageEntity?

    @Query("DELETE FROM audio_bg_binding WHERE audio_id = :audioId")
    suspend fun clearBindingsForAudio(audioId: Long)
}
