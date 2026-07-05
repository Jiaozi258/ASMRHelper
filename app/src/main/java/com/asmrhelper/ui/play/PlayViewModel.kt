package com.asmrhelper.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlayerState
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.data.local.db.entity.BookmarkEntity
import com.asmrhelper.data.local.db.entity.SceneEntity
import com.asmrhelper.data.repository.BookmarkRepository
import com.asmrhelper.data.repository.SceneRepository
import com.asmrhelper.player.AudioVisualizerController
import com.asmrhelper.player.BinauralBeatEngine
import com.asmrhelper.player.BinauralPreset
import com.asmrhelper.player.HapticFeedbackController
import com.asmrhelper.player.NoiseGenerator
import com.asmrhelper.player.NoiseType
import com.asmrhelper.player.PlayerEvent
import com.asmrhelper.player.PlayerManager
import com.asmrhelper.player.SpatialAudioController
import com.asmrhelper.player.SpatialMode
import com.asmrhelper.util.maskPrivacy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TriggerEffectConfig(
    val color: Long = 0xFFFFFFFF,
    val emoji: String = "",
    val animationType: Int = 0 // 0=spray, 1=flash, 2=fountain
)

data class PlayUiState(
    val playerState: PlayerState = PlayerState(),
    val audioList: List<Audio> = emptyList(),
    val displayTitle: String = "",
    val backgroundImagePath: String? = null,
    val backgroundColorIndex: Int = 0,
    val menuExpanded: Boolean = false,
    // 睡眠定时器
    val timerMinutes: Int = 0,
    val timerRemainingMs: Long = 0L,
    val timerActive: Boolean = false,
    val stopAfterCurrent: Boolean = false,
    // 番茄钟
    val pomodoroActive: Boolean = false,
    val pomodoroIsFocus: Boolean = true,
    val pomodoroCycle: Int = 0,
    val pomodoroRemainingMs: Long = 0L,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroCustomFocusMin: Int = 25,
    val pomodoroCustomBreakMin: Int = 5,
    // 双耳节拍
    val binauralActive: Boolean = false,
    val binauralPreset: BinauralPreset? = null,
    // 催眠模式
    val hypnosisEnabled: Boolean = false,
    val hypnosisBgType: Int = 0,
    val hypnosisUiVisible: Boolean = true,
    // 噪音生成
    val noiseActive: Boolean = false,
    val noiseType: String = "WHITE",
    val noiseVolume: Float = 0.3f,
    // 双耳节拍
    val binauralVolume: Float = 0.4f,
    // 3D空间音效
    val spatialMode: String = "OFF",
    // 场景
    val scenes: List<SceneEntity> = emptyList(),
    // 书签
    val bookmarks: List<BookmarkEntity> = emptyList(),
    // 触觉反馈
    val hapticEnabled: Boolean = false,
    // 环境音
    val ambientAudios: List<String> = emptyList(),
    val selectedAmbientPath: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val audioRepository: AudioRepository,
    private val settingsRepository: SettingsRepository,
    private val binauralBeatEngine: BinauralBeatEngine,
    private val audioVisualizerController: AudioVisualizerController,
    private val noiseGenerator: NoiseGenerator,
    private val spatialAudioController: SpatialAudioController,
    private val hapticFeedbackController: HapticFeedbackController,
    private val sceneRepository: SceneRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayUiState())
    val uiState: StateFlow<PlayUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var pomodoroJob: Job? = null
    private var hypnosisUiJob: Job? = null

    // Volume trigger
    private val _triggerEffect = MutableSharedFlow<TriggerEffectConfig>(extraBufferCapacity = 2)
    val triggerEffect: SharedFlow<TriggerEffectConfig> = _triggerEffect
    private var lastTriggerMs: Long = 0L

    // Settings: visualizer and volume trigger
    private val _visualizerEnabled = MutableStateFlow(false)
    val visualizerEnabled: StateFlow<Boolean> = _visualizerEnabled
    private val _volumeTriggerEnabled = MutableStateFlow(false)
    val volumeTriggerEnabled: StateFlow<Boolean> = _volumeTriggerEnabled
    private val _volumeThreshold = MutableStateFlow(70)
    val volumeThreshold: StateFlow<Int> = _volumeThreshold
    private val _triggerColor = MutableStateFlow(0xFFFFFFFFL)
    private val _triggerEmoji = MutableStateFlow("")
    private val _triggerAnimType = MutableStateFlow(0)
    private val _triggerParticleCount = MutableStateFlow(16)
    private val _triggerCooldownMs = MutableStateFlow(1000)
    private val _fftMagnitudes = MutableStateFlow<FloatArray?>(null)
    val fftMagnitudes: StateFlow<FloatArray?> = _fftMagnitudes
    val waveformBytes: StateFlow<ByteArray?> = audioVisualizerController.waveformBytes
    val triggerParticleCount: StateFlow<Int> = _triggerParticleCount
    val triggerCooldownMs: StateFlow<Int> = _triggerCooldownMs

    init {
        // 合并播放器状态、音频列表和隐私模式
        viewModelScope.launch {
            combine(
                playerManager.state,
                audioRepository.getAllAudio(),
                settingsRepository.isPrivacyMode()
            ) { playerState, audioList, isPrivacy ->
                val title = playerState.currentAudio?.title ?: ""
                Triple(playerState, audioList, if (isPrivacy) title.maskPrivacy() else title)
            }.collect { (playerState, audioList, displayTitle) ->
                _uiState.update { current ->
                    current.copy(
                        playerState = playerState,
                        audioList = audioList,
                        displayTitle = displayTitle
                    )
                }
            }
        }

        // 收集背景颜色偏好
        viewModelScope.launch {
            settingsRepository.getBgColorIndex().collect { index ->
                _uiState.update { it.copy(backgroundColorIndex = index) }
            }
        }

        // 收集全局背景图设置
        viewModelScope.launch {
            settingsRepository.getCurrentBgImagePath().collect { path ->
                _uiState.update { it.copy(backgroundImagePath = path) }
            }
        }

        // 收集催眠模式设置（combine 避免竞态）
        viewModelScope.launch {
            combine(
                settingsRepository.getHypnosisModeEnabled(),
                settingsRepository.getHypnosisBackgroundType()
            ) { enabled, type ->
                enabled to type
            }.collect { (enabled, type) ->
                _uiState.update {
                    it.copy(
                        hypnosisEnabled = enabled,
                        hypnosisBgType = type,
                        hypnosisUiVisible = if (enabled) it.hypnosisUiVisible else true
                    )
                }
            }
        }

        // 收集可视化与触发设置
        viewModelScope.launch {
            settingsRepository.getAudioVisualizerEnabled().collect { _visualizerEnabled.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeTriggerEnabled().collect { _volumeTriggerEnabled.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeTriggerThreshold().collect { _volumeThreshold.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeTriggerColor().collect { _triggerColor.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeTriggerEmoji().collect { _triggerEmoji.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getVolumeTriggerAnimType().collect { _triggerAnimType.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getTriggerParticleCount().collect { _triggerParticleCount.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getTriggerCooldownMs().collect { _triggerCooldownMs.value = it }
        }

        // 音量触发检测（基于真实音频波形数据）
        viewModelScope.launch {
            playerManager.state.collect { state ->
                if (!_volumeTriggerEnabled.value || !state.isPlaying) return@collect
                val threshold = _volumeThreshold.value

                // Use real waveform data when available, otherwise simulated
                val waveform = audioVisualizerController.waveformBytes.value
                val volume = if (waveform != null && waveform.isNotEmpty()) {
                    var sum = 0.0
                    for (b in waveform) sum += ((b.toInt() - 128) * (b.toInt() - 128))
                    kotlin.math.sqrt(sum / waveform.size).toInt().coerceIn(0, 100)
                } else {
                    val progress = if (state.durationMs > 0) state.progressMs.toFloat() / state.durationMs else 0f
                    (50 + (kotlin.math.sin(progress * 10) * 40)).toInt()
                }

                val now = System.currentTimeMillis()
                val cooldown = _triggerCooldownMs.value.toLong()
                if (volume >= threshold && now - lastTriggerMs > cooldown) {
                    lastTriggerMs = now
                    _triggerEffect.tryEmit(
                        TriggerEffectConfig(
                            color = _triggerColor.value,
                            emoji = _triggerEmoji.value,
                            animationType = _triggerAnimType.value
                        )
                    )
                }
            }
        }

        // 监听播放结束，处理"播完当前停止"
        viewModelScope.launch {
            var wasPlaying = false
            playerManager.state.collect { state ->
                if (wasPlaying && !state.isPlaying && _uiState.value.stopAfterCurrent) {
                    _uiState.update { it.copy(stopAfterCurrent = false) }
                    playerManager.handleEvent(PlayerEvent.Pause)
                }
                wasPlaying = state.isPlaying
            }
        }

        // 收集场景列表
        viewModelScope.launch {
            sceneRepository.getAll().collect { scenes ->
                _uiState.update { it.copy(scenes = scenes) }
            }
        }

        // 收集环境音列表和选中项
        viewModelScope.launch {
            settingsRepository.getAmbientAudios().collect { audios ->
                _uiState.update { it.copy(ambientAudios = audios) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(selectedAmbientPath = settingsRepository.getSelectedAmbientAudio()) }
        }

        // Attach spatial audio once (session ID persists across tracks)
        viewModelScope.launch {
            playerManager.state
                .map { it.isPlaying }
                .collect { playing ->
                    if (playing && !spatialAudioController.isActive && _uiState.value.spatialMode != "OFF") {
                        spatialAudioController.attach(playerManager.getAudioSessionId())
                    }
                }
        }

        // Attach audio visualizer when enabled (visualizer or trigger) and playing
        viewModelScope.launch {
            combine(playerManager.state, _visualizerEnabled, _volumeTriggerEnabled) { state, vis, trig ->
                state.isPlaying && (vis || trig)
            }.collect { shouldAttach ->
                if (shouldAttach && !audioVisualizerController.isActive) {
                    audioVisualizerController.attach(playerManager.getAudioSessionId())
                } else if (!shouldAttach && audioVisualizerController.isActive) {
                    audioVisualizerController.release()
                }
            }
        }

        // Collect FFT data for visualizer
        viewModelScope.launch {
            audioVisualizerController.fftMagnitudes.collect { mags ->
                _fftMagnitudes.value = mags
            }
        }

        // 监听当前音频变化，加载书签
        viewModelScope.launch {
            playerManager.state
                .map { it.currentAudio }
                .flatMapLatest { audio ->
                    if (audio != null) {
                        bookmarkRepository.getByAudioId(audio.id)
                    } else {
                        flowOf(emptyList())
                    }
                }
                .collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
        }

        // 触觉反馈同步
        viewModelScope.launch {
            playerManager.state.collect { state ->
                if (_uiState.value.hapticEnabled && state.isPlaying) {
                    hapticFeedbackController.startSync {
                        val s = playerManager.state.value
                        if (s.isPlaying && s.durationMs > 0) s.progressMs to s.durationMs
                        else null
                    }
                } else {
                    hapticFeedbackController.stopSync()
                }
            }
        }
    }

    fun play(audio: Audio) {
        viewModelScope.launch {
            playerManager.handleEvent(PlayerEvent.Play(audio, uiState.value.audioList))
            loadBackgroundForAudio(audio.id)
        }
    }

    fun playPlaylist(audios: List<Audio>) {
        audios.firstOrNull()?.let { audio ->
            viewModelScope.launch {
                playerManager.handleEvent(PlayerEvent.Play(audio, audios))
                loadBackgroundForAudio(audio.id)
            }
        }
    }

    fun togglePlayPause() {
        val event = if (uiState.value.playerState.isPlaying) PlayerEvent.Pause
        else PlayerEvent.Resume
        playerManager.handleEvent(event)
    }

    fun seekTo(positionMs: Long) = playerManager.handleEvent(PlayerEvent.SeekTo(positionMs))

    fun next() = playerManager.handleEvent(PlayerEvent.Next)
    fun previous() = playerManager.handleEvent(PlayerEvent.Previous)
    fun setLoopMode(mode: LoopMode) = playerManager.handleEvent(PlayerEvent.SetLoopMode(mode))

    fun cycleLoopMode() {
        val nextMode = when (uiState.value.playerState.loopMode) {
            LoopMode.NONE -> LoopMode.SINGLE
            LoopMode.SINGLE -> LoopMode.LIST
            LoopMode.LIST -> LoopMode.NONE
        }
        playerManager.handleEvent(PlayerEvent.SetLoopMode(nextMode))
    }

    fun toggleBackground() {
        // If background is not playing and we have a selected ambient, start it
        if (!_uiState.value.playerState.isBackgroundPlaying && _uiState.value.selectedAmbientPath != null) {
            setBackgroundAudio(_uiState.value.selectedAmbientPath!!)
        } else {
            playerManager.handleEvent(PlayerEvent.ToggleBackground)
        }
    }

    fun setBackgroundAudio(filePath: String) =
        playerManager.handleEvent(PlayerEvent.SetBackgroundAudio(filePath))

    fun selectAmbientAudio(path: String?) {
        viewModelScope.launch {
            settingsRepository.setSelectedAmbientAudio(path)
            _uiState.update { it.copy(selectedAmbientPath = path) }
            if (path != null) {
                setBackgroundAudio(path)
            }
        }
    }

    fun toggleMenu() {
        // menuExpanded is managed by the dropdown component directly
    }

    fun setTimerSeconds(totalSeconds: Int) {
        cancelTimer()
        val minutes = totalSeconds / 60
        val totalMs = totalSeconds * 1000L
        _uiState.update { it.copy(timerMinutes = minutes, timerRemainingMs = totalMs, timerActive = true) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerRemainingMs > 0) {
                delay(1000L)
                _uiState.update { current ->
                    val remaining = (current.timerRemainingMs - 1000L).coerceAtLeast(0L)
                    current.copy(timerRemainingMs = remaining)
                }
            }
            _uiState.update { it.copy(timerActive = false, timerMinutes = 0, timerRemainingMs = 0L) }
            playerManager.handleEvent(PlayerEvent.Pause)
        }
    }

    @Deprecated("Use setTimerSeconds", ReplaceWith("setTimerSeconds(minutes * 60)"))
    fun setTimer(minutes: Int) = setTimerSeconds(minutes * 60)

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(timerMinutes = 0, timerRemainingMs = 0L, timerActive = false) }
    }

    fun toggleStopAfterCurrent() {
        _uiState.update { it.copy(stopAfterCurrent = !it.stopAfterCurrent) }
    }

    // ── 番茄钟 ──────────────────────────────────────────

    fun startPomodoro(focusMin: Int = 25, breakMin: Int = 5) {
        cancelPomodoro()
        val totalMs = focusMin * 60 * 1000L
        val cycle = _uiState.value.pomodoroCycle + 1
        _uiState.update {
            it.copy(
                pomodoroActive = true,
                pomodoroIsFocus = true,
                pomodoroCycle = cycle,
                pomodoroRemainingMs = totalMs,
                pomodoroFocusMinutes = focusMin,
                pomodoroBreakMinutes = breakMin
            )
        }
        runPomodoroPhase()
    }

    fun cancelPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _uiState.update {
            it.copy(
                pomodoroActive = false,
                pomodoroIsFocus = true,
                pomodoroCycle = 0,
                pomodoroRemainingMs = 0L
            )
        }
    }

    fun updatePomodoroCustomFocus(minutes: Int) {
        _uiState.update { it.copy(pomodoroCustomFocusMin = minutes.coerceIn(1, 120)) }
    }

    fun updatePomodoroCustomBreak(minutes: Int) {
        _uiState.update { it.copy(pomodoroCustomBreakMin = minutes.coerceIn(1, 30)) }
    }

    private fun runPomodoroPhase() {
        pomodoroJob = viewModelScope.launch {
            while (_uiState.value.pomodoroRemainingMs > 0) {
                delay(1000L)
                _uiState.update { current ->
                    val remaining = (current.pomodoroRemainingMs - 1000L).coerceAtLeast(0L)
                    current.copy(pomodoroRemainingMs = remaining)
                }
            }
            // Phase ended, switch focus ↔ break
            val state = _uiState.value
            if (state.pomodoroIsFocus) {
                // Focus done → break
                val breakMs = state.pomodoroBreakMinutes * 60 * 1000L
                _uiState.update {
                    it.copy(pomodoroIsFocus = false, pomodoroRemainingMs = breakMs)
                }
            } else {
                // Break done → next focus
                val focusMs = state.pomodoroFocusMinutes * 60 * 1000L
                _uiState.update {
                    it.copy(
                        pomodoroIsFocus = true,
                        pomodoroCycle = it.pomodoroCycle + 1,
                        pomodoroRemainingMs = focusMs
                    )
                }
            }
            runPomodoroPhase()
        }
    }

    fun loadBackgroundForAudio(audioId: Long) {
        viewModelScope.launch {
            // Audio-bound background takes priority over global
            val boundImage = settingsRepository.getBindingForAudio(audioId)
            if (boundImage != null) {
                _uiState.update { it.copy(backgroundImagePath = boundImage.filePath) }
            }
            // If no binding, global bg (if any) is already being collected by getCurrentBgImagePath
        }
    }

    // ── 双耳节拍 ──────────────────────────────────────────

    fun toggleBinaural(preset: BinauralPreset) {
        if (_uiState.value.binauralActive && _uiState.value.binauralPreset == preset) {
            binauralBeatEngine.stop()
            _uiState.update { it.copy(binauralActive = false, binauralPreset = null) }
        } else {
            binauralBeatEngine.start(preset.baseFrequency, preset.beatFrequency)
            _uiState.update { it.copy(binauralActive = true, binauralPreset = preset) }
        }
    }

    fun stopBinaural() {
        binauralBeatEngine.stop()
        _uiState.update { it.copy(binauralActive = false, binauralPreset = null) }
    }

    fun showHypnosisUi() {
        if (!_uiState.value.hypnosisEnabled) return
        _uiState.update { it.copy(hypnosisUiVisible = true) }
        hypnosisUiJob?.cancel()
        hypnosisUiJob = viewModelScope.launch {
            delay(5000L)
            _uiState.update { it.copy(hypnosisUiVisible = false) }
        }
    }

    // ── 噪音生成 ──────────────────────────────────────────

    fun toggleNoise(type: NoiseType = NoiseType.WHITE) {
        if (_uiState.value.noiseActive && noiseGenerator.currentType == type) {
            noiseGenerator.stop()
            _uiState.update { it.copy(noiseActive = false) }
        } else {
            noiseGenerator.start(type)
            _uiState.update { it.copy(noiseActive = true, noiseType = type.name) }
        }
    }

    fun stopNoise() {
        noiseGenerator.stop()
        _uiState.update { it.copy(noiseActive = false) }
    }

    fun setNoiseVolume(vol: Float) {
        noiseGenerator.updateVolume(vol)
        _uiState.update { it.copy(noiseVolume = vol) }
    }
    fun setBinauralVolume(vol: Float) {
        binauralBeatEngine.updateVolume(vol)
        _uiState.update { it.copy(binauralVolume = vol) }
    }

    // ── 3D空间音效 ────────────────────────────────────────

    fun cycleSpatialMode() {
        val next = when (SpatialMode.valueOf(_uiState.value.spatialMode)) {
            SpatialMode.OFF -> SpatialMode.SWEEP
            SpatialMode.SWEEP -> SpatialMode.CIRCLE
            SpatialMode.CIRCLE -> SpatialMode.WIDE
            SpatialMode.WIDE -> SpatialMode.OFF
        }
        spatialAudioController.setMode(next)
        _uiState.update { it.copy(spatialMode = next.name) }
    }

    fun stopSpatial() {
        spatialAudioController.setMode(SpatialMode.OFF)
        _uiState.update { it.copy(spatialMode = "OFF") }
    }

    // ── 淡入淡出 ──────────────────────────────────────────

    fun fadeOut(durationMs: Long = 3000L) {
        playerManager.handleEvent(PlayerEvent.FadeOut(durationMs))
    }

    fun fadeIn(durationMs: Long = 3000L) {
        playerManager.handleEvent(PlayerEvent.FadeIn(durationMs))
    }

    // ── 场景 ───────────────────────────────────────────────

    fun saveCurrentScene(name: String) {
        val state = _uiState.value
        viewModelScope.launch {
            val scene = SceneEntity(
                name = name,
                audioFilePath = state.playerState.currentAudio?.filePath ?: "",
                bgColorIndex = state.backgroundColorIndex,
                binauralPresetName = state.binauralPreset?.name ?: "",
                timerMinutes = state.timerMinutes,
                noiseType = state.noiseType,
                spatialMode = state.spatialMode,
                visualizerEnabled = _visualizerEnabled.value
            )
            sceneRepository.save(scene)
        }
    }

    fun applyScene(scene: SceneEntity) {
        viewModelScope.launch {
            // Play audio if specified — use stable id from filePath hash
            if (scene.audioFilePath.isNotEmpty()) {
                val fileName = scene.audioFilePath.substringAfterLast('/')
                val audio = Audio(
                    id = scene.audioFilePath.hashCode().toLong(),
                    title = fileName,
                    artist = "",
                    filePath = scene.audioFilePath,
                    durationMs = 0L,
                    isFavorite = false
                )
                play(audio)
            }

            delay(300L) // let player init before applying other settings
            applySceneSettings(scene)
        }
    }

    private suspend fun applySceneSettings(scene: SceneEntity) {
        // Apply background color
        viewModelScope.launch {
            settingsRepository.setBgColorIndex(scene.bgColorIndex)
        }
        // Apply binaural
        if (scene.binauralPresetName.isNotEmpty()) {
            BinauralPreset.PRESETS.find { it.name == scene.binauralPresetName }?.let {
                toggleBinaural(it)
            }
        }
        // Apply timer
        if (scene.timerMinutes > 0) {
            setTimerSeconds(scene.timerMinutes * 60)
        }
        // Apply noise (only if scene has a non-default noise type)
        if (scene.noiseType.isNotEmpty() && scene.noiseType != "WHITE") {
            try {
                toggleNoise(NoiseType.valueOf(scene.noiseType))
            } catch (_: Exception) {}
        } else if (_uiState.value.noiseActive && scene.noiseType.isEmpty()) {
            stopNoise()
        }
        // Apply spatial
        if (scene.spatialMode.isNotEmpty() && scene.spatialMode != "OFF") {
            try {
                spatialAudioController.setMode(SpatialMode.valueOf(scene.spatialMode))
                _uiState.update { it.copy(spatialMode = scene.spatialMode) }
            } catch (_: Exception) {}
        }
        // Apply visualizer
        viewModelScope.launch {
            settingsRepository.setAudioVisualizerEnabled(scene.visualizerEnabled)
        }
    }

    fun deleteScene(scene: SceneEntity) {
        viewModelScope.launch { sceneRepository.delete(scene) }
    }

    // ── 书签 ───────────────────────────────────────────────

    fun addBookmark(name: String) {
        val audio = _uiState.value.playerState.currentAudio ?: return
        viewModelScope.launch {
            bookmarkRepository.save(
                BookmarkEntity(
                    audioId = audio.id,
                    name = name,
                    positionMs = _uiState.value.playerState.progressMs
                )
            )
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkRepository.delete(bookmark) }
    }

    fun jumpToBookmark(positionMs: Long) {
        seekTo(positionMs)
    }

    // ── 触觉反馈 ──────────────────────────────────────────

    fun toggleHaptic() {
        val next = !_uiState.value.hapticEnabled
        _uiState.update { it.copy(hapticEnabled = next) }
        hapticFeedbackController.enabled = next
        // State collector in init handles startSync/stopSync reactively
    }

    fun setHapticIntensity(intensity: Float) {
        hapticFeedbackController.intensity = intensity.coerceIn(0f, 1f)
    }

    override fun onCleared() {
        super.onCleared()
        hypnosisUiJob?.cancel()
        noiseGenerator.stop()
        audioVisualizerController.release()
        spatialAudioController.release()
        hapticFeedbackController.release()
        binauralBeatEngine.stop()
    }
}
