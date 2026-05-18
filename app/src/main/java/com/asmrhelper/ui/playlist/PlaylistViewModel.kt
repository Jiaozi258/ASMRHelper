package com.asmrhelper.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val audioRepository: AudioRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAudio: StateFlow<List<Audio>> = audioRepository.getAllAudio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val selectedPlaylistAudios: StateFlow<List<Audio>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) playlistRepository.getPlaylistAudios(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPlaylist(id: Long) {
        _selectedPlaylistId.value = if (_selectedPlaylistId.value == id) null else id
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun addAudioToPlaylist(playlistId: Long, audioId: Long) {
        viewModelScope.launch {
            playlistRepository.addAudioToPlaylist(playlistId, audioId)
        }
    }

    fun removeAudioFromPlaylist(playlistId: Long, audioId: Long) {
        viewModelScope.launch {
            playlistRepository.removeAudioFromPlaylist(playlistId, audioId)
        }
    }

    // ── Import / Export ────────────────────────────

    private val _exportResult = MutableSharedFlow<String>()
    val exportResult: SharedFlow<String> = _exportResult.asSharedFlow()

    suspend fun getExportJson(playlistId: Long): String? {
        val playlist = playlists.value.find { it.id == playlistId } ?: return null
        val audios = playlistRepository.getPlaylistAudios(playlistId).first()
        val json = JSONObject()
        json.put("version", 1)
        val pl = JSONObject()
        pl.put("name", playlist.name)
        val arr = JSONArray()
        for (a in audios) {
            val ao = JSONObject()
            ao.put("title", a.title)
            ao.put("artist", a.artist)
            ao.put("filePath", a.filePath)
            ao.put("durationMs", a.durationMs)
            arr.put(ao)
        }
        pl.put("audios", arr)
        json.put("playlist", pl)
        return json.toString(2)
    }

    fun importPlaylistFromJson(jsonStr: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(jsonStr)
                val pl = json.getJSONObject("playlist")
                val name = pl.getString("name")
                val arr = pl.getJSONArray("audios")
                val newId = playlistRepository.createPlaylist(name)
                for (i in 0 until arr.length()) {
                    val ao = arr.getJSONObject(i)
                    val title = ao.getString("title")
                    val artist = ao.optString("artist", "")
                    val filePath = ao.getString("filePath")
                    val durationMs = ao.optLong("durationMs", 0L)
                    val audioId = audioRepository.addAudio(
                        Audio(title = title, artist = artist, filePath = filePath, durationMs = durationMs)
                    )
                    playlistRepository.addAudioToPlaylist(newId, audioId)
                }
                _exportResult.emit("导入成功: $name")
            } catch (e: Exception) {
                _exportResult.emit("导入失败: ${e.message}")
            }
        }
    }
}
