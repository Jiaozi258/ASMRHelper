package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.VideoAudio
import kotlinx.coroutines.flow.Flow

interface VideoAudioRepository {
    fun getAll(): Flow<List<VideoAudio>>
    fun getFavorites(): Flow<List<VideoAudio>>
    suspend fun getById(id: Long): VideoAudio?
    suspend fun getBySourceUrl(url: String): VideoAudio?
    suspend fun insert(videoAudio: VideoAudio): Long
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteById(id: Long)
}
