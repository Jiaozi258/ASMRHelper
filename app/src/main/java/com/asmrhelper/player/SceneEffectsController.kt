package com.asmrhelper.player

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.di.MainPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 场景效果预设 */
enum class SceneEffect(val label: String) {
    NONE("无"),
    BLANKET("被窝声"),   // 闷声包裹感
    DISTORTED("失真声"), // 黑胶失真感
    CONCERT("演唱会"),   // 大厅混响
    BATHROOM("浴室"),    // 小房间混响
    THEATER("影院"),     // 中型厅混响
}

/**
 * 场景效果控制器：基于 Android 音频特效 API 合成场景预设。
 * 使用 PresetReverb（混响）、BassBoost（低音增强）、LoudnessEnhancer（响度）。
 */
@Singleton
class SceneEffectsController @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null

    private var reverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _currentScene = MutableStateFlow(SceneEffect.NONE)
    val currentScene: StateFlow<SceneEffect> = _currentScene.asStateFlow()

    private val _loudnessGain = MutableStateFlow(0)
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow() // dB * 100

    init {
        // 用 Player.Listener 监听 audioSessionId 变化作为挂载时机，而不是只在
        // STATE_READY 挂一次：audioSessionId 由底层 AudioTrack 运行时产生，可能与
        // STATE_READY 回调竞态（那一刻 sessionId 仍是 0），导致音效对象永久挂不上、
        // 场景/响度全程静默失效。onAudioSessionIdChanged 在 sessionId 真正可用时必然触发。
        mainPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) attachEffects() else releaseEffects()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !_isReady.value) {
                    attachEffects()
                }
            }
        })
        if (mainPlayer.audioSessionId > 0) attachEffects()
    }

    private fun attachEffects() {
        val sessionId = mainPlayer.audioSessionId
        if (sessionId <= 0) return
        // sessionId 变化时旧对象已失效，先释放再重建
        releaseEffects()
        try {
            reverb = PresetReverb(0, sessionId).apply {
                enabled = false
                preset = PresetReverb.PRESET_NONE
            }
        } catch (_: Exception) { reverb = null }
        try {
            bassBoost = BassBoost(0, sessionId).apply { enabled = false }
        } catch (_: Exception) { bassBoost = null }
        try {
            loudness = LoudnessEnhancer(sessionId).apply {
                enabled = false
                setTargetGain(0)
            }
        } catch (_: Exception) { loudness = null }
        _isReady.value = true
        // 迟到挂载后必须把用户已选的场景/响度重新应用一次，否则选择会丢失
        applyScene(_currentScene.value)
    }

    /** 应用场景预设（只控制混响/低音；响度由 [setLoudnessGain] 独立控制） */
    fun applyScene(scene: SceneEffect) {
        _currentScene.value = scene
        when (scene) {
            SceneEffect.NONE -> {
                reverb?.let { it.enabled = false }
                bassBoost?.let { it.enabled = false }
            }
            SceneEffect.BLANKET -> {
                // 被窝声：低音增强 + 小房间混响（闷声包裹感）
                bassBoost?.apply {
                    enabled = true
                    setStrength(700.toShort())
                }
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_SMALLROOM
                }
            }
            SceneEffect.DISTORTED -> {
                // 失真声：低音增强 + 无混响（颗粒感）
                bassBoost?.apply {
                    enabled = true
                    setStrength(500.toShort())
                }
                reverb?.let { it.enabled = false }
            }
            SceneEffect.CONCERT -> {
                // 演唱会：大厅混响
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_LARGEHALL
                }
                bassBoost?.apply {
                    enabled = true
                    setStrength(300.toShort())
                }
            }
            SceneEffect.BATHROOM -> {
                // 浴室：小房间混响
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_SMALLROOM
                }
                bassBoost?.let { it.enabled = false }
            }
            SceneEffect.THEATER -> {
                // 影院：中型厅混响
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_MEDIUMHALL
                }
                bassBoost?.let { it.enabled = false }
            }
        }
        applyLoudness()
    }

    /** 响度作为独立功能：只要用户设过增益就作用于 LoudnessEnhancer，与场景无关。 */
    fun setLoudnessGain(gainDbX100: Int) {
        val clamped = gainDbX100.coerceIn(0, 1000)
        _loudnessGain.value = clamped
        applyLoudness()
    }

    private fun applyLoudness() {
        val gain = _loudnessGain.value
        loudness?.apply {
            enabled = gain > 0
            if (gain > 0) setTargetGain(gain)
        }
    }

    /** 设置音量阈值模式：0=响度, 1=阈值。
     *  阈值模式的完整压缩器（DynamicsProcessing）在当前 SDK 环境下不可用，
     *  此处仅存储模式供 UI 与未来扩展；响度模式由 LoudnessEnhancer 实现。 */
    fun setThresholdMode(mode: Int) {
        // 响度模式由 setLoudnessGain 控制；阈值模式暂存。
    }

    /** 设置最大阈值（限幅器阈值，dB）。当前版本仅存储，不做实际 DSP。 */
    fun setMaxThreshold(db: Int) {
        // 存储逻辑在 SettingsRepository 中完成
    }

    /** 设置最小阈值（噪声门，dB）。当前版本仅存储，不做实际 DSP。 */
    fun setMinThreshold(db: Int) {
        // 存储逻辑在 SettingsRepository 中完成
    }

    private fun releaseEffects() {
        try { reverb?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        try { loudness?.release() } catch (_: Exception) { }
        reverb = null
        bassBoost = null
        loudness = null
        _isReady.value = false
    }

    fun release() {
        initJob?.cancel()
        initJob = null
        scope.cancel()
        releaseEffects()
    }
}
