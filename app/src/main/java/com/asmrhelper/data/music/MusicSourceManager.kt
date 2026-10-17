package com.asmrhelper.data.music

import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.SourceConfig
import com.asmrhelper.domain.music.SourcePresets
import com.asmrhelper.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理多个「音源」实例：当前音源列表、活动源切换、自定义源增删改。
 * 懒建 `HttpMusicSource` 并缓存。
 */
@Singleton
class MusicSourceManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sourceCache = mutableMapOf<String, MusicSource>()

    private val _sources = MutableStateFlow<List<SourceConfig>>(SourcePresets.ALL)
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    private val _activeSourceId = MutableStateFlow("netease")
    val activeSourceId: StateFlow<String> = _activeSourceId.asStateFlow()

    /** 活动音源；未配置 baseUrl 时为 null（供 UI 显示空态）。 */
    val activeSource: StateFlow<MusicSource?> =
        combine(_sources, _activeSourceId) { list, id ->
            list.firstOrNull { it.id == id }?.takeIf { it.baseUrl.isNotBlank() }?.let { sourceFor(it) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            settingsRepository.getMusicSources().collect { stored ->
                _sources.value = stored.ifEmpty { SourcePresets.ALL }
            }
        }
        scope.launch {
            settingsRepository.getActiveSourceId().collect { id ->
                if (id.isNotBlank()) _activeSourceId.value = id
            }
        }
    }

    fun sourceFor(config: SourceConfig): MusicSource =
        sourceCache.getOrPut("${config.id}:${config.baseUrl}") {
            HttpMusicSource(config, okHttpClient)
        }

    suspend fun addCustomSource(displayName: String, baseUrl: String) {
        val id = "custom_" + System.currentTimeMillis()
        settingsRepository.setMusicSources(
            _sources.value + SourceConfig(id, displayName, baseUrl.trim(), isCustom = true)
        )
    }

    suspend fun updateSource(config: SourceConfig) {
        settingsRepository.setMusicSources(
            _sources.value.map { if (it.id == config.id) config else it }
        )
    }

    suspend fun removeSource(id: String) {
        settingsRepository.setMusicSources(_sources.value.filterNot { it.id == id })
    }

    suspend fun setActive(id: String) {
        settingsRepository.setActiveSourceId(id)
    }

    companion object {
        /** 校验音源地址：http/https 且可解析。 */
        fun validateBaseUrl(s: String): Boolean = runCatching {
            val url = s.trim().toHttpUrlOrNull()
            url != null && (url.scheme == "http" || url.scheme == "https")
        }.getOrDefault(false)
    }
}
