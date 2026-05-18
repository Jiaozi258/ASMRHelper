package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.AudioDao
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.repository.AudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val audioDao: AudioDao
) : AudioRepository {

    override fun getAllAudio(): Flow<List<Audio>> =
        audioDao.getAllAudio().map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<Audio>> =
        audioDao.getFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Audio? =
        audioDao.getById(id)?.toDomain()

    override suspend fun addAudio(audio: Audio): Long {
        audioDao.getByFilePath(audio.filePath)?.let { return it.id }
        return audioDao.insert(audio.toEntity())
    }

    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        audioDao.getById(id)?.let { entity ->
            audioDao.update(entity.copy(isFavorite = isFavorite))
        }
    }

    override suspend fun deleteAudio(id: Long) =
        audioDao.deleteById(id)

    override suspend fun searchAudio(keyword: String): List<Audio> =
        audioDao.search(keyword).map { it.toDomain() }
}
