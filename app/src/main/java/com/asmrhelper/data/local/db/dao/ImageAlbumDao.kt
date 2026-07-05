package com.asmrhelper.data.local.db.dao

import androidx.room.*
import com.asmrhelper.data.local.db.entity.ImageAlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageAlbumDao {
    @Query("SELECT * FROM image_albums ORDER BY createdAt ASC")
    fun getAll(): Flow<List<ImageAlbumEntity>>

    @Insert
    suspend fun insert(album: ImageAlbumEntity): Long

    @Delete
    suspend fun delete(album: ImageAlbumEntity)

    @Query("SELECT * FROM image_albums ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirst(): ImageAlbumEntity?
}
