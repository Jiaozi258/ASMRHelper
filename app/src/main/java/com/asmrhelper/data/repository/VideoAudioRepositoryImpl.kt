package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.VideoAudioDao
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoAudioRepositoryImpl @Inject constructor(
    private val dao: VideoAudioDao
) : VideoAudioRepository {

    override fun getAll(): Flow<List<VideoAudio>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<VideoAudio>> =
        dao.getFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): VideoAudio? =
        dao.getById(id)?.toDomain()

    override suspend fun getBySourceUrl(url: String): VideoAudio? =
        dao.getBySourceUrl(url)?.toDomain()

    override suspend fun insert(videoAudio: VideoAudio): Long =
        dao.insert(videoAudio.toEntity())

    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(isFavorite = isFavorite)) }
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
