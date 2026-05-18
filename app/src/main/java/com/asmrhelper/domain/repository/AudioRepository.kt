package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.Audio
import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    fun getAllAudio(): Flow<List<Audio>>
    fun getFavorites(): Flow<List<Audio>>
    suspend fun getById(id: Long): Audio?
    suspend fun addAudio(audio: Audio): Long
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteAudio(id: Long)
    suspend fun searchAudio(keyword: String): List<Audio>
}
