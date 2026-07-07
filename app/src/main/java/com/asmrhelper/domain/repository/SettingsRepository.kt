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
}
