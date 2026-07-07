package com.asmrhelper.data.repository

import android.content.Context
import com.asmrhelper.data.local.db.dao.BackgroundImageDao
import com.asmrhelper.data.local.db.entity.AudioBgBinding
import com.asmrhelper.data.local.db.entity.BackgroundImageEntity
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import com.asmrhelper.R

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val imageDao: BackgroundImageDao,
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
    private val _privacyMode = MutableStateFlow(prefs.getBoolean("privacy_mode", false))
    private val _themePresetOrdinal = MutableStateFlow(prefs.getInt("theme_preset", 0))
    private val _bgColorIndex = MutableStateFlow(prefs.getInt("bg_color_index", 0))
    private val _visualizerEnabled = MutableStateFlow(prefs.getBoolean("audio_visualizer", false))
    private val _triggerEnabled = MutableStateFlow(prefs.getBoolean("volume_trigger", false))
    private val _triggerThreshold = MutableStateFlow(prefs.getInt("volume_threshold", 70))
    private val _triggerEffect = MutableStateFlow(prefs.getInt("volume_effect", 0))
    private val _ambientAudios = MutableStateFlow(loadAmbientList())
    private val _hypnosisEnabled = MutableStateFlow(prefs.getBoolean("hypnosis_mode", false))
    private val _hypnosisBgType = MutableStateFlow(prefs.getInt("hypnosis_bg_type", 0))

    override fun getBackgroundImages(): Flow<List<BackgroundImage>> =
        imageDao.getAllImages().map { list -> list.map { it.toDomain() } }

    override suspend fun addBackgroundImage(name: String, filePath: String): Long =
        imageDao.insertImage(BackgroundImageEntity(name = name, filePath = filePath))

    override suspend fun deleteBackgroundImage(id: Long) =
        imageDao.deleteImage(id)

    override suspend fun bindAudioToImage(audioId: Long, imageId: Long) =
        imageDao.bindAudioToImage(AudioBgBinding(audioId, imageId))

    override suspend fun unbindAudioFromImage(audioId: Long, imageId: Long) =
        imageDao.unbindAudioFromImage(audioId, imageId)

    override suspend fun getBindingForAudio(audioId: Long): BackgroundImage? =
        imageDao.getBindingForAudio(audioId)?.toDomain()

    override fun isPrivacyMode(): Flow<Boolean> = _privacyMode

    override suspend fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_mode", enabled).apply()
        _privacyMode.value = enabled
    }

    override fun getThemePresetOrdinal(): Flow<Int> = _themePresetOrdinal

    override suspend fun setThemePresetOrdinal(ordinal: Int) {
        prefs.edit().putInt("theme_preset", ordinal).apply()
        _themePresetOrdinal.value = ordinal
    }

    override fun getBgColorIndex(): Flow<Int> = _bgColorIndex

    override suspend fun setBgColorIndex(index: Int) {
        prefs.edit().putInt("bg_color_index", index).apply()
        _bgColorIndex.value = index
    }

    private val _currentBgImagePath = MutableStateFlow(prefs.getString("current_bg_image", null))
    override fun getCurrentBgImagePath(): Flow<String?> = _currentBgImagePath
    override suspend fun setCurrentBgImagePath(path: String?) {
        if (path != null) prefs.edit().putString("current_bg_image", path).apply()
        else prefs.edit().remove("current_bg_image").apply()
        _currentBgImagePath.value = path
    }

    // ── Ambient audios ─────────────────────────────────

    /** Built-in ambient sound OGG files in res/raw/.
     *  Key = the "builtin:" path used by AmbientSource, value = R.raw resource ID. */
    private val builtInAmbientResIds = mapOf(
        "builtin:ambient_rain" to R.raw.ambient_rain,
        "builtin:ambient_stream" to R.raw.ambient_stream,
        "builtin:ambient_campfire" to R.raw.ambient_campfire,
        "builtin:ambient_wind" to R.raw.ambient_wind,
        "builtin:ambient_thunder" to R.raw.ambient_thunder,
        "builtin:ambient_ocean" to R.raw.ambient_ocean,
    )

    /** Returns the res/raw ID for a built-in ambient, or 0 if not found. */
    override fun getBuiltInResId(path: String): Int = builtInAmbientResIds[path] ?: 0

    /** All built-in ambient paths, always available. */
    override fun getBuiltInAmbients(): List<String> = builtInAmbientResIds.keys.toList()

    override fun getAmbientAudios(): Flow<List<String>> = _ambientAudios

    override suspend fun addAmbientAudio(path: String) {
        val list = _ambientAudios.value.toMutableList()
        if (path !in list) {
            list.add(path)
            saveAmbientList(list)
            _ambientAudios.value = list
        }
    }

    override suspend fun removeAmbientAudio(path: String) {
        val list = _ambientAudios.value.toMutableList()
        list.remove(path)
        saveAmbientList(list)
        _ambientAudios.value = list
        if (getSelectedAmbientAudio() == path) setSelectedAmbientAudio(null)
    }

    override suspend fun getSelectedAmbientAudio(): String? =
        prefs.getString("selected_ambient", null)

    override suspend fun setSelectedAmbientAudio(path: String?) {
        if (path != null) prefs.edit().putString("selected_ambient", path).apply()
        else prefs.edit().remove("selected_ambient").apply()
    }

    // ── Audio visualizer ───────────────────────────────

    override fun getAudioVisualizerEnabled(): Flow<Boolean> = _visualizerEnabled

    override suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("audio_visualizer", enabled).apply()
        _visualizerEnabled.value = enabled
    }

    // ── Volume trigger ─────────────────────────────────

    override fun getVolumeTriggerEnabled(): Flow<Boolean> = _triggerEnabled

    override suspend fun setVolumeTriggerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("volume_trigger", enabled).apply()
        _triggerEnabled.value = enabled
    }

    override fun getVolumeTriggerThreshold(): Flow<Int> = _triggerThreshold

    override suspend fun setVolumeTriggerThreshold(threshold: Int) {
        prefs.edit().putInt("volume_threshold", threshold).apply()
        _triggerThreshold.value = threshold
    }

    override fun getVolumeTriggerEffect(): Flow<Int> = _triggerEffect

    override suspend fun setVolumeTriggerEffect(effect: Int) {
        prefs.edit().putInt("volume_effect", effect).apply()
        _triggerEffect.value = effect
    }

    private val _triggerColor = MutableStateFlow(prefs.getLong("volume_effect_color", 0xFFFFFFFF))
    override fun getVolumeTriggerColor(): Flow<Long> = _triggerColor
    override suspend fun setVolumeTriggerColor(color: Long) {
        prefs.edit().putLong("volume_effect_color", color).apply()
        _triggerColor.value = color
    }

    private val _triggerEmoji = MutableStateFlow(prefs.getString("volume_effect_emoji", "") ?: "")
    override fun getVolumeTriggerEmoji(): Flow<String> = _triggerEmoji
    override suspend fun setVolumeTriggerEmoji(emoji: String) {
        prefs.edit().putString("volume_effect_emoji", emoji).apply()
        _triggerEmoji.value = emoji
    }

    private val _triggerAnimType = MutableStateFlow(prefs.getInt("volume_effect_anim", 0))
    override fun getVolumeTriggerAnimType(): Flow<Int> = _triggerAnimType
    override suspend fun setVolumeTriggerAnimType(type: Int) {
        prefs.edit().putInt("volume_effect_anim", type).apply()
        _triggerAnimType.value = type
    }

    // ── Hypnosis mode ────────────────────────────────

    override fun getHypnosisModeEnabled(): Flow<Boolean> = _hypnosisEnabled

    override suspend fun setHypnosisModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hypnosis_mode", enabled).apply()
        _hypnosisEnabled.value = enabled
    }

    override fun getHypnosisBackgroundType(): Flow<Int> = _hypnosisBgType

    override suspend fun setHypnosisBackgroundType(type: Int) {
        prefs.edit().putInt("hypnosis_bg_type", type).apply()
        _hypnosisBgType.value = type
    }

    // ── 通知与锁屏 ───────────────────────────────────

    private val _showNotification = MutableStateFlow(prefs.getBoolean("show_notification", true))
    override fun getShowNotification(): Flow<Boolean> = _showNotification
    override suspend fun setShowNotification(enabled: Boolean) {
        prefs.edit().putBoolean("show_notification", enabled).apply()
        _showNotification.value = enabled
    }

    private val _showOnLockScreen = MutableStateFlow(prefs.getBoolean("show_on_lockscreen", false))
    override fun getShowOnLockScreen(): Flow<Boolean> = _showOnLockScreen
    override suspend fun setShowOnLockScreen(enabled: Boolean) {
        prefs.edit().putBoolean("show_on_lockscreen", enabled).apply()
        _showOnLockScreen.value = enabled
    }

    // ── 触发特效参数 ─────────────────────────────────

    private val _triggerParticleCount = MutableStateFlow(prefs.getInt("trigger_particle_count", 16))
    override fun getTriggerParticleCount(): Flow<Int> = _triggerParticleCount
    override suspend fun setTriggerParticleCount(count: Int) {
        prefs.edit().putInt("trigger_particle_count", count).apply()
        _triggerParticleCount.value = count
    }

    private val _triggerCooldownMs = MutableStateFlow(prefs.getInt("trigger_cooldown_ms", 1000))
    override fun getTriggerCooldownMs(): Flow<Int> = _triggerCooldownMs
    override suspend fun setTriggerCooldownMs(cooldownMs: Int) {
        prefs.edit().putInt("trigger_cooldown_ms", cooldownMs).apply()
        _triggerCooldownMs.value = cooldownMs
    }

    // ── Ambient list serialization ─────────────────────

    // ── 播放界面特效 ─────────────────────────────────

    override fun getPlayEffectsEnabled(): Boolean =
        prefs.getBoolean("play_effects", true)

    override suspend fun setPlayEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("play_effects", enabled).apply()
    }

    private fun loadAmbientList(): List<String> {
        val json = prefs.getString("ambient_audios", null) ?: return emptyList()
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun saveAmbientList(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("ambient_audios", arr.toString()).apply()
    }
}
