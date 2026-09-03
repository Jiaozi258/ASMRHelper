package com.asmrhelper.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlayerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.asmrhelper.di.MainPlayer
import com.asmrhelper.di.BackgroundPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer,
    @BackgroundPlayer private val backgroundPlayer: ExoPlayer,
    @ApplicationContext private val context: Context,
    private val playHistoryRepository: com.asmrhelper.data.repository.PlayHistoryRepositoryImpl
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentPlaylist: List<Audio> = emptyList()
    private var currentIndex: Int = -1

    // External state change listener (e.g., for MediaService notification updates)
    private var onStateChanged: ((PlayerState) -> Unit)? = null

    fun setStateListener(listener: ((PlayerState) -> Unit)?) {
        onStateChanged = listener
    }

    // ── Playback state persistence ──────────────────────────
    // Survives process death so the service can resume after
    // being killed by aggressive OEM power management.

    private val prefs = context.getSharedPreferences("asmr_player_state", Context.MODE_PRIVATE)
    private val settingsPrefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)

    // ── Settings helpers ─────────────────────────────────
    private fun isRememberPlaybackEnabled(): Boolean = settingsPrefs.getBoolean("remember_playback", true)
    private fun isAmbientFadeEnabled(): Boolean = settingsPrefs.getBoolean("ambient_fade", false)
    private fun getFadeOutMode(): Int = settingsPrefs.getInt("fade_out_mode", 0)

    /** Save last-played audio so the service can resume after restart.
     *  Respects the "remember playback" setting — disabled means no memory. */
    private fun saveLastPlayback(audio: Audio) {
        if (!isRememberPlaybackEnabled()) return
        prefs.edit()
            .putString("last_file_path", audio.filePath)
            .putString("last_title", audio.title)
            .putString("last_artist", audio.artist)
            .putLong("last_duration_ms", audio.durationMs)
            .apply()
    }

    private fun saveLastPosition(positionMs: Long) {
        if (!isRememberPlaybackEnabled()) return
        prefs.edit().putLong("last_position_ms", positionMs).apply()
    }

    private fun loadLastPosition(): Long = prefs.getLong("last_position_ms", 0L)

    private fun clearSavedPlayback() {
        prefs.edit()
            .remove("last_file_path")
            .remove("last_position_ms")
            .apply()
    }

    /** Load saved playback info. Returns null if nothing was saved. */
    fun loadLastPlayback(): Audio? {
        val path = prefs.getString("last_file_path", null) ?: return null
        val title = prefs.getString("last_title", "") ?: "未知"
        val artist = prefs.getString("last_artist", "") ?: ""
        val duration = prefs.getLong("last_duration_ms", 0L)
        return Audio(
            id = 0L,
            title = title,
            artist = artist,
            filePath = path,
            durationMs = duration,
            isFavorite = false
        )
    }

    /** Resume the last-played audio. Called by the service after process-death restart.
     *  Does NOT re-start the service (caller is already the service). */
    fun resumeLastPlayback() {
        val audio = loadLastPlayback() ?: return
        // Restore a minimal playlist so Next/Previous buttons work
        currentPlaylist = listOf(audio)
        currentIndex = 0
        val mediaItem = MediaItem.fromUri(audio.filePath)
        mainPlayer.setMediaItem(mediaItem)
        mainPlayer.prepare()
        mainPlayer.seekTo(loadLastPosition())
        mainPlayer.play()
        _state.update { it.copy(currentAudio = audio, isPlaying = true) }
    }

    init {
        // ── 记忆播放：恢复上次的歌曲和播放位置 ──
        // 修复"假保留"问题：之前只把标题写进 UI，但没有 prepare 播放器，
        // 导致点播放按钮无效。现在真正 prepare 播放器并 seek 到上次位置。
        if (isRememberPlaybackEnabled()) {
            val saved = loadLastPlayback()
            if (saved != null) {
                currentPlaylist = listOf(saved)
                currentIndex = 0
                _state.update { it.copy(currentAudio = saved, durationMs = saved.durationMs) }
                val mediaItem = MediaItem.fromUri(saved.filePath)
                mainPlayer.setMediaItem(mediaItem)
                mainPlayer.prepare()
                mainPlayer.seekTo(loadLastPosition())
            }
        } else {
            clearSavedPlayback()
        }

        mainPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                // 暂停时立即保存进度，实现精准定位
                if (!isPlaying) {
                    saveLastPosition(mainPlayer.currentPosition)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }
        })

        // Keep isBackgroundPlaying in sync with actual background player state
        backgroundPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isBackgroundPlaying = isPlaying) }
            }
        })

        // 更新进度（200ms 节流），并周期保存播放位置（每 5 秒一次）
        var lastPosSave = 0L
        scope.launch {
            while (true) {
                val current = mainPlayer.currentPosition
                val dur = mainPlayer.duration
                if (current >= 0 || dur > 0) {
                    // 切片循环：超过 B 点则跳回 A 点
                    if (sliceStartMs >= 0 && sliceEndMs > sliceStartMs && current > sliceEndMs) {
                        mainPlayer.seekTo(sliceStartMs)
                    }
                    _state.update {
                        it.copy(
                            progressMs = current.coerceAtLeast(0),
                            durationMs = dur.takeIf { d -> d > 0 } ?: it.durationMs
                        )
                    }
                    if (mainPlayer.isPlaying && current > 0L &&
                        kotlin.math.abs(current - lastPosSave) >= 5000L) {
                        lastPosSave = current
                        saveLastPosition(current)
                    }
                }
                delay(200L)
            }
        }

        // ── 环境音循环交叉淡入淡出：在循环边界处淡出，循环后淡入 ──
        scope.launch {
            var lastBgPos = 0L
            while (true) {
                delay(150L)
                if (!isAmbientFadeEnabled() || !backgroundPlayer.isPlaying) {
                    lastBgPos = backgroundPlayer.currentPosition
                    continue
                }
                val pos = backgroundPlayer.currentPosition
                val dur = backgroundPlayer.duration
                if (dur > 0L && backgroundPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                    // 检测循环回绕（位置从接近结尾跳回开头）
                    if (pos < lastBgPos - 300L) {
                        // 已循环回开头 — 渐入
                        fadeBackgroundIn()
                    } else if (dur - pos < 800L && backgroundPlayer.volume > 0.05f) {
                        // 接近结尾 — 渐出
                        fadeBackgroundOut(800L)
                    }
                }
                lastBgPos = pos
            }
        }

        // ── 结尾淡出模式：歌曲接近结尾时渐出并停止 ──
        scope.launch {
            while (true) {
                delay(200L)
                if (pendingFadeOutAtEnd && mainPlayer.isPlaying) {
                    val dur = mainPlayer.duration
                    val pos = mainPlayer.currentPosition
                    if (dur > 0L && dur - pos < fadeOutDurationMs) {
                        pendingFadeOutAtEnd = false
                        val remaining = (dur - pos).coerceAtLeast(100L)
                        val steps = 15
                        for (i in steps downTo 1) {
                            mainPlayer.volume = i.toFloat() / steps
                            delay(remaining / steps)
                        }
                        mainPlayer.pause()
                        mainPlayer.volume = 1f
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
            }
        }

        // Notify external listeners of state changes
        scope.launch {
            _state.collect { state ->
                onStateChanged?.invoke(state)
            }
        }
    }

    // ── 环境音渐入渐出辅助方法 ─────────────────────────

    private fun fadeBackgroundIn() {
        scope.launch {
            val steps = 20
            backgroundPlayer.volume = 0f
            for (i in 1..steps) {
                backgroundPlayer.volume = i.toFloat() / steps
                delay(50L) // 共 1 秒
            }
            backgroundPlayer.volume = 1f
        }
    }

    private fun fadeBackgroundOut(durationMs: Long = 1000L) {
        scope.launch {
            val steps = 20
            val stepMs = durationMs / steps
            for (i in steps downTo 1) {
                backgroundPlayer.volume = i.toFloat() / steps
                delay(stepMs)
            }
            // 保持静音，等待 fadeBackgroundIn 或停止后恢复
            backgroundPlayer.volume = 0f
        }
    }

    fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Play -> play(event.audio, event.playlist)
            PlayerEvent.Pause -> mainPlayer.pause()
            PlayerEvent.Resume -> mainPlayer.play()
            PlayerEvent.Next -> skipToNext()
            PlayerEvent.Previous -> skipToPrevious()
            is PlayerEvent.SeekTo -> mainPlayer.seekTo(event.positionMs)
            is PlayerEvent.SetLoopMode -> _state.update { it.copy(loopMode = event.mode) }
            PlayerEvent.ToggleBackground -> toggleBackground()
            is PlayerEvent.SetBackgroundAudio -> setBackgroundAudio(event.filePath)
            is PlayerEvent.SetAmbientLoop -> setAmbientLoop(event.enabled)
            is PlayerEvent.SetCrossfade -> _state.update { it.copy(crossfadeDurationMs = event.durationMs) }
            is PlayerEvent.FadeOut -> fadeOut(event.durationMs)
            is PlayerEvent.FadeIn -> fadeIn(event.durationMs)
        }
    }

    private var crossfadeJob: Job? = null
    private var fadeJob: Job? = null

    // 结尾淡出模式：歌曲结尾时淡出并停止
    private var pendingFadeOutAtEnd: Boolean = false
    private var fadeOutDurationMs: Long = 5000L

    // 切片 (A-B 循环) 状态
    private var sliceStartMs: Long = -1L
    private var sliceEndMs: Long = -1L

    /** 设置播放速度（保留当前音调） */
    fun setPlaybackSpeed(speed: Float) {
        val p = mainPlayer.playbackParameters
        val pitch = p?.pitch ?: 1f
        mainPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
    }

    /** 设置音调（保留当前速度） */
    fun setPlaybackPitch(pitch: Float) {
        val p = mainPlayer.playbackParameters
        val speed = p?.speed ?: 1f
        mainPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
    }

    /** 设置切片范围（A-B 循环），start/end 为 -1 表示清除 */
    fun setSlice(startMs: Long, endMs: Long) {
        sliceStartMs = startMs
        sliceEndMs = endMs
        if (startMs >= 0) mainPlayer.seekTo(startMs)
    }

    fun clearSlice() {
        sliceStartMs = -1L
        sliceEndMs = -1L
    }

    /** Returns the audio session ID for attaching audio effects. */
    fun getAudioSessionId(): Int = mainPlayer.audioSessionId

    private fun play(audio: Audio, playlist: List<Audio>) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        pendingFadeOutAtEnd = false
        // Reset volume in case a previous crossfade was cancelled mid-fade,
        // leaving the volume at a partial value.
        mainPlayer.volume = 1f
        currentPlaylist = playlist.ifEmpty { listOf(audio) }
        currentIndex = currentPlaylist.indexOfFirst { it.id == audio.id }
            .let { if (it >= 0) it else currentPlaylist.indexOfFirst { a -> a.filePath == audio.filePath } }
            .coerceAtLeast(0)

        val crossfadeMs = _state.value.crossfadeDurationMs
        if (crossfadeMs > 0 && mainPlayer.isPlaying) {
            // Fade out current, then switch
            crossfadeJob = scope.launch {
                val steps = 10
                val stepMs = crossfadeMs / steps
                for (i in steps downTo 1) {
                    mainPlayer.volume = i.toFloat() / steps
                    delay(stepMs.toLong())
                }
                mainPlayer.setMediaItem(MediaItem.fromUri(audio.filePath))
                mainPlayer.prepare()
                mainPlayer.play()
                for (i in 1..steps) {
                    mainPlayer.volume = i.toFloat() / steps
                    delay(stepMs.toLong())
                }
                mainPlayer.volume = 1f
            }
        } else {
            val mediaItem = MediaItem.fromUri(audio.filePath)
            mainPlayer.setMediaItem(mediaItem)
            mainPlayer.prepare()
            mainPlayer.play()
        }
        _state.update { it.copy(currentAudio = audio) }
        saveLastPlayback(audio) // survive process death
        // Record playback history (fire-and-forget)
        scope.launch(Dispatchers.IO) {
            try {
                playHistoryRepository.insert(
                    com.asmrhelper.data.local.db.entity.PlayHistoryEntity(
                        audioTitle = audio.title,
                        audioArtist = audio.artist,
                        filePath = audio.filePath,
                        durationMs = audio.durationMs,
                        playedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) { /* best-effort */ }
        }
        startMediaServiceIfNeeded()
    }

    /** Always start the foreground service during playback.
     *  Android requires a foreground-service notification — that's non-negotiable
     *  for background execution. The user's "show_notification" preference only
     *  controls how visible the notification is (lock screen, priority), not
     *  whether the service itself runs. Skipping the service when notifications
     *  are "disabled" would guarantee the app gets killed by OEMs. */
    private fun startMediaServiceIfNeeded() {
        val intent = Intent(context, AsmrMediaService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun skipToNext() {
        if (currentPlaylist.isEmpty() || currentIndex < 0) return
        val nextIndex = when (_state.value.loopMode) {
            LoopMode.SHUFFLE -> {
                // Random track, avoid repeating the same one when possible
                if (currentPlaylist.size <= 1) 0
                else {
                    var idx = (currentPlaylist.indices).random()
                    while (idx == currentIndex) idx = currentPlaylist.indices.random()
                    idx
                }
            }
            else -> {
                val idx = (currentIndex + 1) % currentPlaylist.size
                if (idx == 0 && _state.value.loopMode != LoopMode.LIST) return
                idx
            }
        }
        play(currentPlaylist[nextIndex], currentPlaylist)
    }

    private fun skipToPrevious() {
        if (currentPlaylist.isEmpty() || currentIndex < 0) return
        val prevIndex = if (mainPlayer.currentPosition > 3000L) currentIndex
        else (currentIndex - 1 + currentPlaylist.size) % currentPlaylist.size
        play(currentPlaylist[prevIndex], currentPlaylist)
    }

    private fun handlePlaybackEnded() {
        when (_state.value.loopMode) {
            LoopMode.SINGLE -> {
                mainPlayer.seekTo(0)
                mainPlayer.play()
            }
            LoopMode.LIST, LoopMode.SHUFFLE -> skipToNext()
            LoopMode.NONE -> {
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun toggleBackground() {
        val wasPlaying = _state.value.isBackgroundPlaying
        if (wasPlaying) {
            // 停止环境音：若开启渐入渐出则先淡出再暂停
            if (isAmbientFadeEnabled()) {
                scope.launch {
                    val steps = 20
                    for (i in steps downTo 1) {
                        backgroundPlayer.volume = i.toFloat() / steps
                        delay(50L)
                    }
                    backgroundPlayer.pause()
                    backgroundPlayer.volume = 1f
                }
            } else {
                backgroundPlayer.pause()
            }
        } else {
            backgroundPlayer.play()
            if (isAmbientFadeEnabled()) {
                backgroundPlayer.volume = 0f
                fadeBackgroundIn()
            }
        }
        _state.update { it.copy(isBackgroundPlaying = !wasPlaying) }
    }

    /** 设置背景音轨的音频文件。保留已有的循环模式设置。 */
    fun setBackgroundAudio(filePath: String) {
        // Preserve repeat mode — if user previously enabled ambient loop,
        // new ambient audio should also loop.
        val currentRepeatMode = backgroundPlayer.repeatMode
        backgroundPlayer.setMediaItem(MediaItem.fromUri(filePath))
        backgroundPlayer.prepare()
        backgroundPlayer.repeatMode = currentRepeatMode
        backgroundPlayer.playWhenReady = true
        if (isAmbientFadeEnabled()) {
            fadeBackgroundIn()
        } else {
            backgroundPlayer.volume = 1f
        }
    }

    /** 设置环境音是否循环播放 */
    fun setAmbientLoop(enabled: Boolean) {
        backgroundPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(ambientLoopEnabled = enabled) }
    }

    /** 停止并释放背景音轨 */
    fun stopBackground() {
        backgroundPlayer.stop()
        _state.update { it.copy(isBackgroundPlaying = false) }
    }

    private fun fadeOut(durationMs: Long) {
        fadeOutDurationMs = durationMs
        // 结尾淡出模式：等歌曲/音频接近结尾时再淡出
        if (getFadeOutMode() == 1) {
            pendingFadeOutAtEnd = true
            return
        }
        // 当前位置淡出模式
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 20
            val stepMs = durationMs / steps
            for (i in steps downTo 1) {
                mainPlayer.volume = i.toFloat() / steps
                delay(stepMs)
            }
            mainPlayer.pause()
            mainPlayer.volume = 1f
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun fadeIn(durationMs: Long) {
        fadeJob?.cancel()
        val startVol = mainPlayer.volume
        fadeJob = scope.launch {
            val steps = 20
            val stepMs = durationMs / steps
            mainPlayer.play()
            for (i in 0..steps) {
                mainPlayer.volume = (startVol + (1f - startVol) * i.toFloat() / steps).coerceAtMost(1f)
                delay(stepMs)
            }
            mainPlayer.volume = 1f
            _state.update { it.copy(isPlaying = true) }
        }
    }

    /** 停止播放并释放背景音轨（但不释放 ExoPlayer 实例） */
    fun stop() {
        crossfadeJob?.cancel()
        fadeJob?.cancel()
        mainPlayer.pause()
        backgroundPlayer.stop()
        _state.update { it.copy(isPlaying = false, isBackgroundPlaying = false) }
    }

    fun release() {
        scope.cancel()
        mainPlayer.release()
        backgroundPlayer.release()
    }
}
