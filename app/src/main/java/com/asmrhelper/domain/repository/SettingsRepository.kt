package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.BackgroundImage
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getBackgroundImages(): Flow<List<BackgroundImage>>
    suspend fun addBackgroundImage(name: String, filePath: String): Long
    suspend fun deleteBackgroundImage(id: Long)
    suspend fun bindAudioToImage(audioId: Long, imageId: Long)
    suspend fun unbindAudioFromImage(audioId: Long, imageId: Long)
    suspend fun getBindingForAudio(audioId: Long): BackgroundImage?
    fun isPrivacyMode(): Flow<Boolean>
    suspend fun setPrivacyMode(enabled: Boolean)
    fun getThemePresetOrdinal(): Flow<Int>
    suspend fun setThemePresetOrdinal(ordinal: Int)
    fun getDarkTheme(): Flow<Boolean>
    suspend fun setDarkTheme(enabled: Boolean)
    fun getBgColorIndex(): Flow<Int>
    suspend fun setBgColorIndex(index: Int)
    fun getCurrentBgImagePath(): Flow<String?>
    suspend fun setCurrentBgImagePath(path: String?)
    fun getAmbientAudios(): Flow<List<String>>
    suspend fun addAmbientAudio(path: String)
    suspend fun removeAmbientAudio(path: String)
    suspend fun getSelectedAmbientAudio(): String?
    suspend fun setSelectedAmbientAudio(path: String?)
    fun getBuiltInResId(path: String): Int
    fun getBuiltInAmbients(): List<String>
    fun getAudioVisualizerEnabled(): Flow<Boolean>
    suspend fun setAudioVisualizerEnabled(enabled: Boolean)
    fun getVolumeTriggerEnabled(): Flow<Boolean>
    suspend fun setVolumeTriggerEnabled(enabled: Boolean)
    fun getVolumeTriggerThreshold(): Flow<Int>
    suspend fun setVolumeTriggerThreshold(threshold: Int)
    fun getVolumeTriggerEffect(): Flow<Int> // now used as animation type (0=spray, 1=flash, 2=fountain)
    suspend fun setVolumeTriggerEffect(effect: Int)
    fun getVolumeTriggerColor(): Flow<Long>
    suspend fun setVolumeTriggerColor(color: Long)
    fun getVolumeTriggerEmoji(): Flow<String>
    suspend fun setVolumeTriggerEmoji(emoji: String)
    fun getVolumeTriggerAnimType(): Flow<Int>
    suspend fun setVolumeTriggerAnimType(type: Int)
    fun getHypnosisModeEnabled(): Flow<Boolean>
    suspend fun setHypnosisModeEnabled(enabled: Boolean)
    fun getHypnosisBackgroundType(): Flow<Int>
    suspend fun setHypnosisBackgroundType(type: Int)

    // ── 通知与锁屏 ───────────────────────────────────
    fun getShowNotification(): Flow<Boolean>
    suspend fun setShowNotification(enabled: Boolean)
    fun getShowOnLockScreen(): Flow<Boolean>
    suspend fun setShowOnLockScreen(enabled: Boolean)

    // ── 触发特效参数 ─────────────────────────────────
    fun getTriggerParticleCount(): Flow<Int>
    suspend fun setTriggerParticleCount(count: Int)
    fun getTriggerCooldownMs(): Flow<Int>
    suspend fun setTriggerCooldownMs(cooldownMs: Int)

    // ── 播放界面特效 ─────────────────────────────────
    fun getPlayEffectsEnabled(): Boolean
    fun getPlayEffectsEnabledFlow(): Flow<Boolean>
    suspend fun setPlayEffectsEnabled(enabled: Boolean)

    // ── 环境音渐入渐出 ─────────────────────────────────
    fun getAmbientFadeEnabled(): Flow<Boolean>
    suspend fun setAmbientFadeEnabled(enabled: Boolean)

    // ── 淡出模式 ───────────────────────────────────────
    // 0 = 在当前位置淡出; 1 = 在歌曲/音频结尾时淡出
    fun getFadeOutMode(): Flow<Int>
    suspend fun setFadeOutMode(mode: Int)

    // ── 记忆播放 ───────────────────────────────────────
    fun getRememberPlayback(): Flow<Boolean>
    suspend fun setRememberPlayback(enabled: Boolean)

    // ── 播放设置 ───────────────────────────────────────
    // 断开蓝牙/耳机时停止播放
    fun getBluetoothStopEnabled(): Flow<Boolean>
    suspend fun setBluetoothStopEnabled(enabled: Boolean)
    // 连接耳机时自动续播
    fun getBluetoothResumeEnabled(): Flow<Boolean>
    suspend fun setBluetoothResumeEnabled(enabled: Boolean)
    // 其他应用播放音频时暂停
    fun getPauseOnOtherAudio(): Flow<Boolean>
    suspend fun setPauseOnOtherAudio(enabled: Boolean)
    // 快进/快退时间（秒，5~30）
    fun getSeekTimeSeconds(): Flow<Int>
    suspend fun setSeekTimeSeconds(seconds: Int)
    // 播放时渐强/暂停时渐弱音量的时长（ms，0=关闭）
    fun getVolumeFadeMs(): Flow<Int>
    suspend fun setVolumeFadeMs(ms: Int)

    // ── 歌词设置 ───────────────────────────────────────
    fun getLyricsFontSize(): Flow<Int>
    suspend fun setLyricsFontSize(sp: Int)
    fun getLyricsShadowEnabled(): Flow<Boolean>
    suspend fun setLyricsShadowEnabled(enabled: Boolean)
    fun getLyricsLineSpacing(): Flow<Float>
    suspend fun setLyricsLineSpacing(spacing: Float)
    fun getLyricsDisplayArea(): Flow<Int> // 0=上三分之一, 1=全屏
    suspend fun setLyricsDisplayArea(area: Int)
    fun getLyricsAlignment(): Flow<Int> // 0=居中, 1=左对齐, 2=右对齐
    suspend fun setLyricsAlignment(alignment: Int)

    // ── 音量阈值 ───────────────────────────────────────
    // 0=响度模式(目标响度), 1=阈值模式(最小/最大阈值压缩)
    fun getVolumeThresholdMode(): Flow<Int>
    suspend fun setVolumeThresholdMode(mode: Int)
    fun getMinThresholdDb(): Flow<Int> // 最小阈值 dB (-60~0)
    suspend fun setMinThresholdDb(db: Int)
    fun getMaxThresholdDb(): Flow<Int> // 最大阈值 dB (-40~0)
    suspend fun setMaxThresholdDb(db: Int)
}
