package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.PlaylistDao
import com.asmrhelper.data.local.db.dao.PlaylistWithCount
import com.asmrhelper.data.local.db.entity.PlaylistAudioCrossRef
import com.asmrhelper.data.local.db.entity.PlaylistEntity
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { list -> list.map { it.toDomain() } }

    private fun PlaylistWithCount.toDomain() = Playlist(
        id = id,
        name = name,
        audioCount = audio_count,
        createdAt = created_at
    )

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.renamePlaylist(id, newName)
    }

    override suspend fun deletePlaylist(id: Long) =
        playlistDao.deletePlaylist(id)

    override suspend fun addAudioToPlaylist(playlistId: Long, audioId: Long) =
        playlistDao.addAudioToPlaylist(PlaylistAudioCrossRef(playlistId, audioId))

    override suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long) =
        playlistDao.removeAudioFromPlaylist(playlistId, audioId)

    override fun getPlaylistAudios(playlistId: Long): Flow<List<Audio>> =
        playlistDao.getPlaylistAudios(playlistId).map { list -> list.map { it.toDomain() } }
}
