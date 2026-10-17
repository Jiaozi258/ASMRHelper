package com.asmrhelper.data.music

import com.asmrhelper.domain.model.music.QrLoginState
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `MusicSource` 的唯一实现：通用 HTTP 适配器。
 *
 * 把「音源地址」上的 HTTP/JSON 接口归一化为 `MusicSource`。加密/风控交给服务端完成。
 *
 * 归一化契约（所有「音源地址」遵循此约定）：
 * ```
 * 包裹层：{ "code": 0, "data": ... }   // code != 0 视为失败
 * GET  {base}/search?keyword=&page=        data: [song]
 * GET  {base}/song/url?id=                 data: { "url": "https://..." }
 * GET  {base}/song/detail?id=              data: song
 * GET  {base}/lyric?id=                    data: { "lrc": "..." }
 * GET  {base}/playlist?id=                 data: { "id","name","cover","songs":[song] }
 * GET  {base}/recommend                    data: [song]
 * GET  {base}/charts                       data: [ { "id","name","songs":[song] } ]
 * POST {base}/login?username=&password=    data: { "cookie","username" }
 * GET  {base}/login/qr/key                 data: { "key" }
 * GET  {base}/login/qr/check?key=          data: { "status","username" }   // waiting|scanned|confirmed|expired
 * ```
 * song = { "id","title","artist","album","duration","cover" }。
 */
class HttpMusicSource(
    private val config: SourceConfig,
    private val client: OkHttpClient,
    private val onLoginChanged: (Boolean) -> Unit = {}
) : MusicSource {

    override val id: String = config.id
    override val displayName: String = config.displayName

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val hasBaseUrl: Boolean get() = config.baseUrl.isNotBlank()

    // ── 搜索 / 曲目 ─────────────────────────────────────

    override fun search(keyword: String, page: Int): Flow<List<RemoteSong>> = flow {
        if (!hasBaseUrl) { emit(emptyList()); return@flow }
        emit(MusicJsonAdapters.parseSearch(get(buildSearchUrl(keyword, page))))
    }

    override fun getSongUrl(songId: String, quality: String): Flow<String> = flow {
        if (!hasBaseUrl) { emit(""); return@flow }
        emit(MusicJsonAdapters.parseSongUrl(get(buildSongUrl(songId))))
    }

    override fun getSongDetail(songId: String): Flow<RemoteSong> = flow {
        if (!hasBaseUrl) { return@flow }
        MusicJsonAdapters.parseSongDetail(get(buildUrl("song", "detail", params = mapOf("id" to songId))))?.let { emit(it) }
    }

    override fun getCoverUrl(songId: String): Flow<String> = flow {
        getSongDetail(songId).collect { detail -> emit(detail.coverUrl ?: "") }
    }

    override fun getLyric(songId: String): Flow<RemoteLyric> = flow {
        if (!hasBaseUrl) { emit(RemoteLyric()); return@flow }
        emit(MusicJsonAdapters.parseLyric(get(buildUrl("lyric", params = mapOf("id" to songId)))))
    }

    // ── 歌单 / 推荐 / 榜单 ───────────────────────────────

    override fun getPlaylist(playlistId: String): Flow<RemotePlaylist> = flow {
        if (!hasBaseUrl) { emit(RemotePlaylist("", "")); return@flow }
        emit(MusicJsonAdapters.parsePlaylist(get(buildUrl("playlist", params = mapOf("id" to playlistId)))))
    }

    override fun getRecommendations(): Flow<List<RemoteSong>> = flow {
        if (!hasBaseUrl) { emit(emptyList()); return@flow }
        emit(MusicJsonAdapters.parseRecommendations(get(buildUrl("recommend"))))
    }

    override fun getCharts(): Flow<List<RemoteChart>> = flow {
        if (!hasBaseUrl) { emit(emptyList()); return@flow }
        emit(MusicJsonAdapters.parseCharts(get(buildUrl("charts"))))
    }

    // ── 登录 ───────────────────────────────────────────

    override fun loginAccount(username: String, password: String): Flow<Result<Unit>> = flow {
        if (!hasBaseUrl) {
            emit(Result.failure(IllegalStateException("音源未配置")))
            return@flow
        }
        val url = buildUrl("login", params = mapOf("username" to username, "password" to password))
        val result = MusicJsonAdapters.parseLogin(post(url))
        if (result.cookie.isNotBlank()) {
            _isLoggedIn.value = true
            onLoginChanged(true)
            emit(Result.success(Unit))
        } else {
            emit(Result.failure(IllegalStateException("登录失败")))
        }
    }

    override fun loginQr(): Flow<QrLoginState> = flow {
        if (!hasBaseUrl) {
            emit(QrLoginState.Failed("音源未配置"))
            return@flow
        }
        val key = MusicJsonAdapters.parseQrKey(get(buildUrl("login", "qr", "key")))
        if (key.isBlank()) {
            emit(QrLoginState.Failed("获取二维码失败"))
            return@flow
        }
        val qrContent = buildUrl("login", "qr", "scan", params = mapOf("key" to key))
        emit(QrLoginState.Waiting(key, qrContent))

        while (true) {
            delay(2000L)
            val check = MusicJsonAdapters.parseQrCheck(
                get(buildUrl("login", "qr", "check", params = mapOf("key" to key)))
            )
            when (check.status.lowercase()) {
                "confirmed" -> {
                    _isLoggedIn.value = true
                    onLoginChanged(true)
                    emit(QrLoginState.Confirmed(check.username))
                    break
                }
                "scanned" -> emit(QrLoginState.Scanned(key))
                "expired" -> {
                    emit(QrLoginState.Expired(key))
                    break
                }
                else -> Unit // waiting / unknown → 继续轮询
            }
        }
    }

    override fun logout() {
        _isLoggedIn.value = false
        onLoginChanged(false)
    }

    // ── URL 构造（internal 供单测）──────────────────────

    internal fun buildSearchUrl(keyword: String, page: Int): String =
        buildUrl("search", params = mapOf("keyword" to keyword, "page" to page.toString()))

    internal fun buildSongUrl(songId: String): String =
        buildUrl("song", "url", params = mapOf("id" to songId))

    private fun buildUrl(vararg segments: String, params: Map<String, String> = emptyMap()): String {
        val base = config.baseUrl.trimEnd('/').toHttpUrlOrNull() ?: return ""
        val builder = base.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    // ── HTTP（同步 + IO 线程）──────────────────────────

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ""
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun post(url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext ""
        try {
            client.newCall(
                Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
            ).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
