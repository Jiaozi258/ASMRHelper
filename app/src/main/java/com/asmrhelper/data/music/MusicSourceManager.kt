package com.asmrhelper.data.music

import android.content.Context
import com.asmrhelper.data.music.netease.NeteaseMusicSource
import com.asmrhelper.data.music.qq.QqMusicSource
import com.asmrhelper.domain.model.music.MusicLogin
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.SourceConfig
import com.asmrhelper.domain.music.SourcePresets
import com.asmrhelper.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理多个在线音乐平台（原生实现）：平台列表、活动平台切换、登录态持久化。
 * 每个平台由原生 `MusicSource` 直接请求官方接口，无「音源地址」。
 */
@Singleton
class MusicSourceManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sourceCache = mutableMapOf<String, MusicSource>()

    private val _sources = MutableStateFlow(SourcePresets.ALL)
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    private val _activeSourceId = MutableStateFlow("netease")
    val activeSourceId: StateFlow<String> = _activeSourceId.asStateFlow()

    /** 活动音源：原生源恒可用，永不为空。 */
    val activeSource: StateFlow<MusicSource> =
        _activeSourceId.map { id ->
            sourceFor(SourcePresets.ALL.firstOrNull { it.id == id } ?: SourcePresets.NETEASE)
        }.stateIn(scope, SharingStarted.Eagerly, sourceFor(SourcePresets.NETEASE))

    init {
        scope.launch {
            settingsRepository.getActiveSourceId().collect { id ->
                if (id.isNotBlank()) _activeSourceId.value = id
            }
        }
    }

    /** 按平台 id 定位音源（用于按音频自身的 sourceId 拉取歌词/详情），未知 id 返回 null。 */
    fun sourceForId(id: String): MusicSource? =
        SourcePresets.ALL.firstOrNull { it.id == id }?.let { sourceFor(it) }

    fun sourceFor(config: SourceConfig): MusicSource =
        sourceCache.getOrPut(config.id) {
            val login = settingsRepository.getMusicLogin(config.id)
            val onLoginChanged: (String, String) -> Unit = { cookie, nickname ->
                scope.launch {
                    settingsRepository.setMusicLogin(config.id, MusicLogin(cookie, nickname))
                }
            }
            when (config.id) {
                "qq" -> QqMusicSource(
                    client = okHttpClient,
                    context = context,
                    persistedCookie = login.cookie,
                    persistedNickname = login.nickname,
                    onLoginChanged = onLoginChanged
                )
                else -> NeteaseMusicSource(
                    client = okHttpClient,
                    context = context,
                    persistedCookie = login.cookie,
                    persistedNickname = login.nickname,
                    onLoginChanged = onLoginChanged
                )
            }
        }

    suspend fun setActive(id: String) {
        settingsRepository.setActiveSourceId(id)
    }
}
