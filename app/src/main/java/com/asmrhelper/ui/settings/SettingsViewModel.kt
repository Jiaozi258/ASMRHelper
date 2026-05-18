package com.asmrhelper.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.PlaylistRepository
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val audioRepository: AudioRepository,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)

    // ── 隐私模式 ──────────────────────────────────────────

    val isPrivacyMode: StateFlow<Boolean> = settingsRepository.isPrivacyMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrivacyMode(enabled)
        }
    }

    // ── 默认循环模式 ──────────────────────────────────────

    private val _loopMode = kotlinx.coroutines.flow.MutableStateFlow(getLoopModeFromPrefs())
    val loopMode: StateFlow<LoopMode> = _loopMode

    fun setLoopMode(mode: LoopMode) {
        _loopMode.value = mode
        prefs.edit().putInt("default_loop_mode", mode.ordinal).apply()
    }

    private fun getLoopModeFromPrefs(): LoopMode {
        val ordinal = prefs.getInt("default_loop_mode", 0)
        return LoopMode.entries.getOrElse(ordinal) { LoopMode.NONE }
    }

    // ── 背景颜色 ──────────────────────────────────────────

    val bgColorIndex: StateFlow<Int> = settingsRepository.getBgColorIndex()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun cycleBgColor() {
        viewModelScope.launch {
            val next = (bgColorIndex.value + 1) % bgColorOptions.size
            settingsRepository.setBgColorIndex(next)
        }
    }

    // ── 数据库统计 ────────────────────────────────────────

    val audioCount: StateFlow<Int> = audioRepository.getAllAudio()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val playlistCount: StateFlow<Int> = playlistRepository.getAllPlaylists()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── 主题色预设 ──────────────────────────────────────

    val themePresetOrdinal: StateFlow<Int> = settingsRepository.getThemePresetOrdinal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setThemePresetOrdinal(ordinal: Int) {
        viewModelScope.launch {
            settingsRepository.setThemePresetOrdinal(ordinal)
        }
    }

    // ── 缓存管理 ──────────────────────────────────────────

    fun clearImageCache(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                Coil.imageLoader(context).diskCache?.clear()
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    fun clearAudioCache(onResult: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = try {
                Coil.imageLoader(context).diskCache?.clear()
                Coil.imageLoader(context).memoryCache?.clear()
                "缓存已清除"
            } catch (e: Exception) {
                "清除失败: ${e.message}"
            }
            withContext(Dispatchers.Main) { onResult(msg) }
        }
    }

    // ── 环境音管理 ──────────────────────────────────────

    val ambientAudios: StateFlow<List<String>> = settingsRepository.getAmbientAudios()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAmbientAudio(path: String) {
        viewModelScope.launch { settingsRepository.addAmbientAudio(path) }
    }

    fun removeAmbientAudio(path: String) {
        viewModelScope.launch { settingsRepository.removeAmbientAudio(path) }
    }

    private val _selectedAmbient = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val selectedAmbient: StateFlow<String?> = _selectedAmbient

    init {
        viewModelScope.launch {
            _selectedAmbient.value = settingsRepository.getSelectedAmbientAudio()
        }
    }

    fun selectAmbient(path: String?) {
        _selectedAmbient.value = path
        viewModelScope.launch { settingsRepository.setSelectedAmbientAudio(path) }
    }

    // ── 音频可视化 ──────────────────────────────────────

    val audioVisualizerEnabled: StateFlow<Boolean> = settingsRepository.getAudioVisualizerEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAudioVisualizer(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAudioVisualizerEnabled(enabled) }
    }

    // ── 音量触发特效 ────────────────────────────────────

    val volumeTriggerEnabled: StateFlow<Boolean> = settingsRepository.getVolumeTriggerEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val volumeTriggerThreshold: StateFlow<Int> = settingsRepository.getVolumeTriggerThreshold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 70)

    val volumeTriggerEffect: StateFlow<Int> = settingsRepository.getVolumeTriggerEffect()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setVolumeTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerEnabled(enabled) }
    }

    fun setVolumeTriggerThreshold(threshold: Int) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerThreshold(threshold) }
    }

    fun setVolumeTriggerEffect(effect: Int) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerEffect(effect) }
    }

    val volumeTriggerColor: StateFlow<Long> = settingsRepository.getVolumeTriggerColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFFFFFFFF)
    fun setVolumeTriggerColor(color: Long) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerColor(color) }
    }

    val volumeTriggerEmoji: StateFlow<String> = settingsRepository.getVolumeTriggerEmoji()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setVolumeTriggerEmoji(emoji: String) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerEmoji(emoji) }
    }

    val volumeTriggerAnimType: StateFlow<Int> = settingsRepository.getVolumeTriggerAnimType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setVolumeTriggerAnimType(type: Int) {
        viewModelScope.launch { settingsRepository.setVolumeTriggerAnimType(type) }
    }

    // ── 催眠模式 ──────────────────────────────────────────

    val hypnosisModeEnabled: StateFlow<Boolean> = settingsRepository.getHypnosisModeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hypnosisBgType: StateFlow<Int> = settingsRepository.getHypnosisBackgroundType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setHypnosisModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHypnosisModeEnabled(enabled) }
    }

    fun setHypnosisBgType(type: Int) {
        viewModelScope.launch { settingsRepository.setHypnosisBackgroundType(type) }
    }

    // ── 遮罩工具 ──────────────────────────────────────────

    companion object {
        val volumeEffectOptions = listOf("扩散式", "闪烁", "喷泉式")

        val triggerColorOptions = listOf(
            "纯白" to 0xFFFFFFFFL,
            "紫色" to 0xFFBB86FCL,
            "金色" to 0xFFFFD740L,
            "青色" to 0xFF03DAC6L,
            "粉色" to 0xFFFF4081L,
            "绿色" to 0xFF69F0AEL
        )

        val bgColorOptions = listOf(
            "深空黑" to androidx.compose.ui.graphics.Color(0xFF0D0D0D),
            "深邃蓝" to androidx.compose.ui.graphics.Color(0xFF1A1A2E),
            "暗紫"   to androidx.compose.ui.graphics.Color(0xFF1A0A2E),
            "墨绿"   to androidx.compose.ui.graphics.Color(0xFF0A1A1A)
        )

        /** 隐私遮罩：保留首尾字符，中间替换为星号 */
        fun maskTitle(title: String): String {
            if (title.length <= 2) return title
            val first = title.first()
            val last = title.last()
            val stars = "*".repeat(title.length - 2)
            return "$first$stars$last"
        }
    }
}
