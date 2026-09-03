package com.asmrhelper.player

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
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
        initJob = scope.launch {
            var attempts = 0
            while (isActive && attempts < 20) {
                val sessionId = mainPlayer.audioSessionId
                if (sessionId > 0) {
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
                    break
                }
                attempts++
                delay(500L)
            }
        }
    }

    /** 应用场景预设 */
    fun applyScene(scene: SceneEffect) {
        _currentScene.value = scene
        when (scene) {
            SceneEffect.NONE -> {
                reverb?.let { it.enabled = false }
                bassBoost?.let { it.enabled = false }
                loudness?.let { it.enabled = false }
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
                loudness?.let { it.enabled = false }
            }
            SceneEffect.DISTORTED -> {
                // 失真声：高响度增益 + 无混响（黑胶颗粒感）
                loudness?.apply {
                    enabled = true
                    setTargetGain(800) // +8 dB 增益，模拟过载失真
                }
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
                loudness?.let { it.enabled = false }
            }
            SceneEffect.BATHROOM -> {
                // 浴室：小房间混响
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_SMALLROOM
                }
                bassBoost?.let { it.enabled = false }
                loudness?.let { it.enabled = false }
            }
            SceneEffect.THEATER -> {
                // 影院：中型厅混响
                reverb?.apply {
                    enabled = true
                    preset = PresetReverb.PRESET_MEDIUMHALL
                }
                bassBoost?.let { it.enabled = false }
                loudness?.let { it.enabled = false }
            }
        }
    }

    /** 设置响度目标增益（音量阈值 - 响度模式）。dB * 100，范围 0~1000。 */
    fun setLoudnessGain(gainDbX100: Int) {
        val clamped = gainDbX100.coerceIn(0, 1000)
        _loudnessGain.value = clamped
        loudness?.apply {
            enabled = clamped > 0
            setTargetGain(clamped)
        }
        // 若当前场景使用响度（失真声），重新应用
        if (_currentScene.value == SceneEffect.DISTORTED) {
            applyScene(SceneEffect.DISTORTED)
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

    fun release() {
        initJob?.cancel()
        initJob = null
        scope.cancel()
        try { reverb?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        try { loudness?.release() } catch (_: Exception) { }
        reverb = null
        bassBoost = null
        loudness = null
        _isReady.value = false
    }
}
