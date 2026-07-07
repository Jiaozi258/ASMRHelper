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

    /** Save last-played audio so the service can resume after restart. */
    private fun saveLastPlayback(audio: Audio) {
        prefs.edit()
            .putString("last_file_path", audio.filePath)
            .putString("last_title", audio.title)
            .putString("last_artist", audio.artist)
            .putLong("last_duration_ms", audio.durationMs)
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
        mainPlayer.play()
        _state.update { it.copy(currentAudio = audio, isPlaying = true) }
    }

    init {
        // Populate initial state with last-played audio so UI shows it on cold start
        val saved = loadLastPlayback()
        if (saved != null) {
            _state.update { it.copy(currentAudio = saved, durationMs = saved.durationMs) }
        }

        mainPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
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

        // 每秒更新进度（包括暂停时，以便 seek 后更新位置）
        scope.launch {
            while (true) {
                val current = mainPlayer.currentPosition
                val dur = mainPlayer.duration
                if (current >= 0 || dur > 0) {
                    _state.update {
                        it.copy(
                            progressMs = current.coerceAtLeast(0),
                            durationMs = dur.takeIf { d -> d > 0 } ?: it.durationMs
                        )
                    }
                }
                delay(200L)
            }
        }

        // Notify external listeners of state changes
        scope.launch {
            _state.collect { state ->
                onStateChanged?.invoke(state)
            }
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
            is PlayerEvent.SetCrossfade -> _state.update { it.copy(crossfadeDurationMs = event.durationMs) }
            is PlayerEvent.FadeOut -> fadeOut(event.durationMs)
            is PlayerEvent.FadeIn -> fadeIn(event.durationMs)
        }
    }

    private var crossfadeJob: Job? = null
    private var fadeJob: Job? = null

    /** Returns the audio session ID for attaching audio effects. */
    fun getAudioSessionId(): Int = mainPlayer.audioSessionId

    private fun play(audio: Audio, playlist: List<Audio>) {
        crossfadeJob?.cancel()
        crossfadeJob = null
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

    private fun startMediaServiceIfNeeded() {
        val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
        val showNotification = prefs.getBoolean("show_notification", true)
        if (!showNotification) return

        val intent = Intent(context, AsmrMediaService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun skipToNext() {
        if (currentPlaylist.isEmpty() || currentIndex < 0) return
        val nextIndex = (currentIndex + 1) % currentPlaylist.size
        if (nextIndex == 0 && _state.value.loopMode != LoopMode.LIST) return
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
            LoopMode.LIST -> skipToNext()
            LoopMode.NONE -> {
                _state.update { it.copy(isPlaying = false) }
            }
        }
    }

    private fun toggleBackground() {
        val wasPlaying = _state.value.isBackgroundPlaying
        if (wasPlaying) {
            backgroundPlayer.pause()
        } else {
            backgroundPlayer.play()
        }
        _state.update { it.copy(isBackgroundPlaying = !wasPlaying) }
    }

    /** 设置背景音轨的音频文件 */
    fun setBackgroundAudio(filePath: String) {
        backgroundPlayer.setMediaItem(MediaItem.fromUri(filePath))
        backgroundPlayer.prepare()
        backgroundPlayer.playWhenReady = true
    }

    /** 停止并释放背景音轨 */
    fun stopBackground() {
        backgroundPlayer.stop()
        _state.update { it.copy(isBackgroundPlaying = false) }
    }

    private fun fadeOut(durationMs: Long) {
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
