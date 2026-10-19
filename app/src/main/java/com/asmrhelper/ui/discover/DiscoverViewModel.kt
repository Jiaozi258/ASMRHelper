package com.asmrhelper.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.R
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.data.music.MusicSourceManager
import com.asmrhelper.data.repository.PlayHistoryRepositoryImpl
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.player.PlayerEvent
import com.asmrhelper.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 发现页状态机：搜索 / 推荐 / 排行榜 / 最近播放，以及在线流播放。
 * 活动音源由原生平台实现，恒可用。
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val musicSourceManager: MusicSourceManager,
    private val playHistoryRepository: PlayHistoryRepositoryImpl,
    private val playerManager: PlayerManager
) : ViewModel() {

    /** 活动音源：原生源恒可用。 */
    val activeSource: StateFlow<MusicSource> = musicSourceManager.activeSource

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RemoteSong>>(emptyList())
    val searchResults: StateFlow<List<RemoteSong>> = _searchResults.asStateFlow()

    private val _recommendations = MutableStateFlow<List<RemoteSong>>(emptyList())
    val recommendations: StateFlow<List<RemoteSong>> = _recommendations.asStateFlow()

    private val _charts = MutableStateFlow<List<RemoteChart>>(emptyList())
    val charts: StateFlow<List<RemoteChart>> = _charts.asStateFlow()

    /** 每日推荐歌曲（需登录）。 */
    private val _dailyRecommend = MutableStateFlow<List<RemoteSong>>(emptyList())
    val dailyRecommend: StateFlow<List<RemoteSong>> = _dailyRecommend.asStateFlow()

    /** 电台（私人 FM）歌曲（需登录）。 */
    private val _radio = MutableStateFlow<List<RemoteSong>>(emptyList())
    val radio: StateFlow<List<RemoteSong>> = _radio.asStateFlow()

    /** 用户歌单（需登录）。 */
    private val _userPlaylists = MutableStateFlow<List<RemotePlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<RemotePlaylist>> = _userPlaylists.asStateFlow()

    /** 当前展开的歌单 id 及其已拉取的歌曲。 */
    private val _expandedPlaylistId = MutableStateFlow<String?>(null)
    val expandedPlaylistId: StateFlow<String?> = _expandedPlaylistId.asStateFlow()

    private val _expandedPlaylistSongs = MutableStateFlow<List<RemoteSong>>(emptyList())
    val expandedPlaylistSongs: StateFlow<List<RemoteSong>> = _expandedPlaylistSongs.asStateFlow()

    /** 当前展开的排行榜 id 及其已拉取的歌曲（toplist 接口不带 tracks，展开时再拉）。 */
    private val _expandedChartId = MutableStateFlow<String?>(null)
    val expandedChartId: StateFlow<String?> = _expandedChartId.asStateFlow()

    private val _expandedChartSongs = MutableStateFlow<List<RemoteSong>>(emptyList())
    val expandedChartSongs: StateFlow<List<RemoteSong>> = _expandedChartSongs.asStateFlow()

    /** 最近播放：复用本地播放历史（这些是本地文件，播放时直接用 filePath）。 */
    val recent: StateFlow<List<PlayHistoryEntity>> = playHistoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 一次性消息（如「无法获取播放地址」），UI 消费后清空。 */
    private val _messageRes = MutableStateFlow<Int?>(null)
    val messageRes: StateFlow<Int?> = _messageRes.asStateFlow()

    /** 正在获取播放链接的歌曲 id（非空时 UI 显示「正在获取歌曲链接」加载态）。 */
    private val _fetchingSongId = MutableStateFlow<String?>(null)
    val fetchingSongId: StateFlow<String?> = _fetchingSongId.asStateFlow()

    init {
        // 初始拉取 + 音源切换 / 登录状态变化时刷新（登录后能立刻看到歌单/电台/每日推荐）。
        viewModelScope.launch {
            activeSource.flatMapLatest { it.isLoggedIn }.collect { refresh() }
        }
    }

    /** 重新拉取推荐 + 排行榜 + 电台 + 用户歌单（音源切换/登录后调用）。 */
    fun refresh() {
        loadRecommendations()
        loadCharts()
        loadDailyRecommend()
        loadRadio()
        loadUserPlaylists()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        search(value)
    }

    fun search(query: String) {
        val source = activeSource.value
        _searching.value = true
        viewModelScope.launch {
            source.search(query, 1).collect { list ->
                _searchResults.value = list
                _searching.value = false
            }
        }
    }

    fun loadRecommendations() {
        val source = activeSource.value
        viewModelScope.launch {
            source.getRecommendations().collect { list ->
                _recommendations.value = list
            }
        }
    }

    fun loadCharts() {
        val source = activeSource.value
        viewModelScope.launch {
            source.getCharts().collect { list ->
                _charts.value = list
            }
        }
    }

    fun loadDailyRecommend() {
        val source = activeSource.value
        viewModelScope.launch {
            source.getDailyRecommendSongs().collect { list ->
                _dailyRecommend.value = list
            }
        }
    }

    fun loadRadio() {
        val source = activeSource.value
        viewModelScope.launch {
            source.getRadio().collect { list ->
                _radio.value = list
            }
        }
    }

    fun loadUserPlaylists() {
        val source = activeSource.value
        viewModelScope.launch {
            source.getUserPlaylists().collect { list ->
                _userPlaylists.value = list
            }
        }
    }

    /** 点击用户歌单：展开并拉取其歌曲，再点收起。 */
    fun toggleUserPlaylist(playlist: RemotePlaylist) {
        if (_expandedPlaylistId.value == playlist.id) {
            _expandedPlaylistId.value = null
            _expandedPlaylistSongs.value = emptyList()
            return
        }
        _expandedPlaylistId.value = playlist.id
        _expandedPlaylistSongs.value = playlist.songs
        val source = activeSource.value
        viewModelScope.launch {
            source.getPlaylist(playlist.id).collect { detail ->
                if (detail.songs.isNotEmpty()) _expandedPlaylistSongs.value = detail.songs
            }
        }
    }

    /** 点击排行榜标题：展开并拉取榜单歌曲（toplist 摘要不含 tracks），再点收起。 */
    fun toggleChart(chart: RemoteChart) {
        if (_expandedChartId.value == chart.id) {
            _expandedChartId.value = null
            _expandedChartSongs.value = emptyList()
            return
        }
        _expandedChartId.value = chart.id
        _expandedChartSongs.value = chart.songs
        val source = activeSource.value
        viewModelScope.launch {
            source.getPlaylist(chart.id).collect { detail ->
                if (detail.songs.isNotEmpty()) _expandedChartSongs.value = detail.songs
            }
        }
    }

    /** 播放在线歌曲：拿不到流地址则提示，不把空串塞给 ExoPlayer（Review Focus #3）。 */
    fun play(song: RemoteSong) {
        val source = activeSource.value
        _fetchingSongId.value = song.id
        viewModelScope.launch {
            try {
                source.getSongUrl(song.id).collect { url ->
                    if (url.isBlank()) {
                        _messageRes.value = R.string.discover_play_failed
                    } else {
                        val audio = song.toAudio(url)
                        playerManager.handleEvent(PlayerEvent.Play(audio, listOf(audio)))
                    }
                }
            } finally {
                _fetchingSongId.value = null
            }
        }
    }

    /** 播放最近播放里的本地文件。 */
    fun playRecent(entry: PlayHistoryEntity) {
        val audio = Audio(
            id = entry.id,
            title = entry.audioTitle,
            artist = entry.audioArtist,
            filePath = entry.filePath,
            durationMs = entry.durationMs
        )
        playerManager.handleEvent(PlayerEvent.Play(audio, listOf(audio)))
    }

    fun consumeMessage() {
        _messageRes.value = null
    }
}

/** 在线歌曲 → 本地播放模型；流 URL 作为 filePath 交给 ExoPlayer 的 MediaItem.fromUri。 */
fun RemoteSong.toAudio(streamUrl: String): Audio = Audio(
    id = id.toLongOrNull() ?: 0L,
    title = title,
    artist = artist,
    filePath = streamUrl,
    durationMs = durationMs,
    coverUrl = coverUrl,
    sourceId = sourceId,
    sourceSongId = id
)
