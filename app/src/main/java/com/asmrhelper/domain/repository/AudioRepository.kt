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
    /** 持久化在线歌曲（按 sourceId+sourceSongId 查重，存在则复用 id），返回其本地 id。 */
    suspend fun upsertRemoteAudio(audio: Audio): Long
}
