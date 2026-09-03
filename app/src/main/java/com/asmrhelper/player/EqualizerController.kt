package com.asmrhelper.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.di.MainPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 均衡器预设 */
data class EqPreset(
    val name: String,
    val values: List<Float> // 10 个频段的 dB 值
)

@Singleton
class EqualizerController @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null
    private var equalizer: Equalizer? = null

    /** 10 段均衡器的标准中心频率 (Hz) */
    val bandFrequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    /** 10 个 UI 频段的增益值 (dB) */
    private val _bandLevels = MutableStateFlow(List(10) { 0f })
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    /** 当前选中的预设名 */
    private val _currentPreset = MutableStateFlow("自定义")
    val currentPreset: StateFlow<String> = _currentPreset.asStateFlow()

    /** 设备实际提供的 EQ 频段索引（用于 UI 频段 → 设备频段映射） */
    private val actualBandIndices = mutableListOf<Short>()

    private val prefs = context.getSharedPreferences("asmr_eq", Context.MODE_PRIVATE)

    companion object {
        /** 内置预设 */
        val PRESETS = listOf(
            EqPreset("默认", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqPreset("明亮", listOf(-2f, -1f, 0f, 1f, 2f, 3f, 4f, 5f, 5f, 6f)),
            EqPreset("低音增强", listOf(6f, 6f, 5f, 4f, 2f, 1f, 0f, 0f, 0f, 0f)),
            EqPreset("清脆", listOf(0f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f, 0f)),
            EqPreset("沉闷", listOf(3f, 3f, 2f, 1f, 0f, -1f, -2f, -3f, -4f, -5f)),
            EqPreset("人声增强", listOf(0f, 0f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f)),
        )
    }

    init {
        initJob = scope.launch {
            var attempts = 0
            while (isActive && attempts < 20) {
                val sessionId = mainPlayer.audioSessionId
                if (sessionId > 0) {
                    try {
                        val eq = Equalizer(0, sessionId).apply { enabled = true }
                        val numBands = eq.numberOfBands.toInt()

                        // 收集设备所有频段的中心频率 (Hz)
                        val deviceBands = (0 until numBands).map { b ->
                            b to eq.getCenterFreq(b.toShort())
                        }
                        actualBandIndices.clear()
                        // 为每个 UI 频段（31~16k Hz）找到最接近的设备频段
                        for (uiFreq in bandFrequencies) {
                            val nearest = deviceBands.minByOrNull { (_, f) -> kotlin.math.abs(f - uiFreq) }
                            val idx = nearest?.first?.toShort() ?: 0
                            actualBandIndices.add(idx)
                        }
                        equalizer = eq
                        _isEnabled.value = true
                        applyLevels(_bandLevels.value)
                        break
                    } catch (_: Exception) {
                        _isEnabled.value = false
                    }
                }
                attempts++
                delay(500L)
            }
        }
    }

    /** 设置某个 UI 频段的增益值 (dB) */
    fun setBandLevel(band: Int, levelDb: Float) {
        val clamped = levelDb.coerceIn(-10f, 10f)
        _bandLevels.update { levels ->
            levels.toMutableList().also {
                if (band in it.indices) it[band] = clamped
            }
        }
        _currentPreset.value = "自定义"
        applySingleBand(band, clamped)
    }

    /** 应用预设 */
    fun applyPreset(preset: EqPreset) {
        _bandLevels.value = preset.values.toList()
        _currentPreset.value = preset.name
        applyLevels(preset.values)
    }

    /** 保存当前设置为自定义预设 */
    fun saveCustomPreset(name: String) {
        val values = _bandLevels.value
        val arr = org.json.JSONArray()
        values.forEach { arr.put(it.toDouble()) }
        prefs.edit()
            .putString("custom_preset_name", name)
            .putString("custom_preset_values", arr.toString())
            .apply()
        _currentPreset.value = name
    }

    /** 加载自定义预设 */
    fun loadCustomPreset(): EqPreset? {
        val name = prefs.getString("custom_preset_name", null) ?: return null
        val valuesJson = prefs.getString("custom_preset_values", null) ?: return null
        return try {
            val arr = org.json.JSONArray(valuesJson)
            val values = (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            EqPreset(name, values)
        } catch (_: Exception) { null }
    }

    fun reset() {
        applyPreset(PRESETS.first()) // 默认（全 0）
    }

    private fun applyLevels(values: List<Float>) {
        for (i in values.indices) {
            applySingleBand(i, values[i])
        }
    }

    private fun applySingleBand(band: Int, levelDb: Float) {
        try {
            val eq = equalizer ?: return
            if (band < actualBandIndices.size) {
                eq.setBandLevel(actualBandIndices[band], (levelDb * 100).toInt().toShort())
            }
        } catch (_: Exception) { }
    }

    fun release() {
        initJob?.cancel()
        initJob = null
        scope.cancel()
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) { }
        equalizer = null
        _isEnabled.value = false
    }
}
