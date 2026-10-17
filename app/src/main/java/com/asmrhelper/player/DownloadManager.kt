package com.asmrhelper.player

import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { IDLE, REQUESTING, DOWNLOADING, SAVING, SUCCESS, FAILED }

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val errorDetail: String = ""
)

@Singleton
class DownloadManager @Inject constructor(
    private val extractor: VideoAudioExtractor,
    private val repository: VideoAudioRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun startDownload(url: String, platform: String? = null, onComplete: (VideoAudio?) -> Unit = {}) {
        if (_state.value.isDownloading) return
        downloadJob = scope.launch {
            extractor.selectedPlatform = platform
            _state.value = DownloadState(true, 0f, DownloadStatus.REQUESTING)

            val result = extractor.extractAudio(url) { progress ->
                _state.value = _state.value.copy(
                    progress = progress,
                    status = DownloadStatus.DOWNLOADING
                )
            }

            if (result != null) {
                _state.value = DownloadState(true, 1f, DownloadStatus.SAVING)
                val videoAudio = VideoAudio(
                    title = result.title,
                    platform = result.platform,
                    sourceUrl = result.sourceUrl,
                    filePath = result.filePath,
                    durationMs = result.durationMs,
                    fileSizeBytes = result.fileSizeBytes
                )
                repository.insert(videoAudio)
                _state.value = DownloadState(false, 0f, DownloadStatus.SUCCESS)
                onComplete(videoAudio)
            } else {
                val detail = extractor.getLastError().lines().firstOrNull()?.take(150) ?: ""
                _state.value = DownloadState(false, 0f, DownloadStatus.FAILED, errorDetail = detail)
                extractor.resetInit()
                onComplete(null)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        // 主动断开下载 socket，使阻塞的 HttpURLConnection.read() 中断，
        // 否则取消只取消协程、下载仍会继续到完成。
        extractor.cancelActiveDownload()
        _state.value = DownloadState()
    }
}
