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

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = ""
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
            _state.value = DownloadState(true, 0f, "正在请求提取服务...")

            val result = extractor.extractAudio(url) { progress ->
                _state.value = _state.value.copy(
                    progress = progress,
                    statusText = if (progress > 0f)
                        "正在下载音频 ${(progress * 100).toInt()}%"
                    else
                        "正在获取视频信息..."
                )
            }

            if (result != null) {
                _state.value = DownloadState(true, 1f, "正在保存...")
                val videoAudio = VideoAudio(
                    title = result.title,
                    platform = result.platform,
                    sourceUrl = result.sourceUrl,
                    filePath = result.filePath,
                    durationMs = result.durationMs,
                    fileSizeBytes = result.fileSizeBytes
                )
                repository.insert(videoAudio)
                _state.value = DownloadState(false, 0f, "提取完成")
                onComplete(videoAudio)
            } else {
                val detail = extractor.getLastError().lines().firstOrNull()
                    ?.take(150) ?: "请检查链接是否有效"
                _state.value = DownloadState(false, 0f, "提取失败: $detail")
                extractor.resetInit()
                onComplete(null)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = DownloadState(false, 0f, "")
    }
}
