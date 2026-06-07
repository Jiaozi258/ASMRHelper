package com.asmrhelper.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import com.asmrhelper.player.DownloadManager
import com.asmrhelper.player.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoAudioViewModel @Inject constructor(
    private val repository: VideoAudioRepository,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val deletedUrlsKey = "deleted_video_audio_urls"

    val videoAudios: StateFlow<List<VideoAudio>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadState: StateFlow<DownloadState> = downloadManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState())

    fun startDownload(url: String, onComplete: (Boolean) -> Unit = {}) {
        // Check if URL was previously deleted by user
        val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
        val deletedUrls = prefs.getStringSet(deletedUrlsKey, emptySet()) ?: emptySet()
        if (url in deletedUrls) {
            onComplete(false)
            return
        }
        downloadManager.startDownload(url) { result ->
            onComplete(result != null)
        }
    }

    fun cancelDownload() = downloadManager.cancel()

    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch { repository.updateFavorite(id, isFavorite) }
    }

    fun deleteVideoAudio(videoAudio: VideoAudio, deleteFile: Boolean = false) {
        viewModelScope.launch {
            repository.deleteById(videoAudio.id)
            // Track deleted URL to prevent re-import
            val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
            val deletedUrls = (prefs.getStringSet(deletedUrlsKey, emptySet())
                ?: emptySet()).toMutableSet()
            deletedUrls.add(videoAudio.sourceUrl)
            prefs.edit().putStringSet(deletedUrlsKey, deletedUrls).apply()
            // Optionally delete local cache file
            if (deleteFile) {
                try { File(videoAudio.filePath).delete() } catch (_: Exception) {}
                videoAudio.coverPath?.let { try { File(it).delete() } catch (_: Exception) {} }
            }
        }
    }
}
