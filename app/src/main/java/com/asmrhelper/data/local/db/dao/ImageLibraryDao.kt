package com.asmrhelper.data.local.db.dao

import androidx.room.*
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageLibraryDao {
    @Query("SELECT * FROM image_library ORDER BY addedAt ASC")
    fun getAll(): Flow<List<ImageLibraryEntity>>

    @Insert
    suspend fun insert(entity: ImageLibraryEntity): Long

    @Delete
    suspend fun delete(entity: ImageLibraryEntity)

    @Query("DELETE FROM image_library WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM image_library ORDER BY addedAt ASC LIMIT 1")
    suspend fun getFirst(): ImageLibraryEntity?
}
