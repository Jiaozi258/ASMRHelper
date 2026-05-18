package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.domain.model.Audio
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, newName: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addAudioToPlaylist(playlistId: Long, audioId: Long)
    suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long)
    fun getPlaylistAudios(playlistId: Long): Flow<List<Audio>>
}
