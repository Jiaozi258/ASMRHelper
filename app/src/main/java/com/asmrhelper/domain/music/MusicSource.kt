package com.asmrhelper.domain.music

import androidx.annotation.StringRes
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * 音乐音源统一接口。由各平台的原生实现（如 `NeteaseMusicSource`）直接请求
 * 官方接口（weapi 加密等）并归一化为这些方法。
 */
/** WebView 网页登录配置：登录页 URL、抓 Cookie 的域、判定登录态的鉴权键。 */
data class WebLoginConfig(
    val url: String,
    val cookieDomain: String,
    val cookieKeys: Set<String>
)

interface MusicSource {
    val id: String
    @get:StringRes val displayNameRes: Int
    val isLoggedIn: StateFlow<Boolean>

    /** 网页登录（WebView 抓 Cookie）配置；null 表示该源不支持网页登录。 */
    val webLogin: WebLoginConfig?
        get() = null

    /** 是否支持账号密码 API 登录（否则仅提供网页登录）。 */
    val supportsAccountLogin: Boolean
        get() = false

    fun search(keyword: String, page: Int): Flow<List<RemoteSong>>
    fun getSongUrl(songId: String, quality: String = "standard"): Flow<String>
    fun getLyric(songId: String): Flow<RemoteLyric>
    fun getCoverUrl(songId: String): Flow<String>
    fun getSongDetail(songId: String): Flow<RemoteSong>
    fun getPlaylist(playlistId: String): Flow<RemotePlaylist>
    fun getRecommendations(): Flow<List<RemoteSong>>
    fun getCharts(): Flow<List<RemoteChart>>

    /** 当前登录用户的歌单列表（需登录）；未登录或不支持时返回空。 */
    fun getUserPlaylists(): Flow<List<RemotePlaylist>> = flowOf(emptyList())

    /** 电台（私人 FM / 推荐电台）歌曲；未登录或不支持时返回空。 */
    fun getRadio(): Flow<List<RemoteSong>> = flowOf(emptyList())

    /** 每日推荐歌曲（需登录）；未登录或不支持时返回空。 */
    fun getDailyRecommendSongs(): Flow<List<RemoteSong>> = flowOf(emptyList())

    fun loginAccount(username: String, password: String): Flow<Result<Unit>>
    fun importLogin(cookie: String, nickname: String)
    fun logout()
}
