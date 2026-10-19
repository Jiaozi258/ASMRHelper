package com.asmrhelper.data.music.netease

import android.content.Context
import com.asmrhelper.R
import com.asmrhelper.data.music.neteaselib.Eapi
import com.asmrhelper.data.music.neteaselib.Weapi
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.WebLoginConfig
import com.asmrhelper.util.Cookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.random.Random

/** 网易云音乐原生音源：直接请求 music.163.com，weapi 加密。 */
class NeteaseMusicSource(
    private val client: OkHttpClient,
    private val context: Context,
    persistedCookie: String = "",
    persistedNickname: String = "",
    private val onLoginChanged: (cookie: String, nickname: String) -> Unit = { _, _ -> }
) : MusicSource {

    override val id: String = "netease"
    override val displayNameRes: Int = R.string.source_netease

    private var cookie: String = persistedCookie
    private var nickname: String = persistedNickname
    private var uid: String = ""

    /** eapi 设备指纹（52 位大写 hex），进程内固定，与 NeteaseCloudMusicApi 一致。 */
    private val deviceId: String by lazy {
        buildString { repeat(52) { append("0123456789ABCDEF"[Random.nextInt(16)]) } }
    }

    private val _isLoggedIn = MutableStateFlow(cookie.contains("MUSIC_U"))
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    override val supportsAccountLogin: Boolean = true
    override val webLogin: WebLoginConfig =
        WebLoginConfig("https://music.163.com/#/login", "https://music.163.com", setOf("MUSIC_U", "__csrf"))

    override fun search(keyword: String, page: Int): Flow<List<RemoteSong>> = flow {
        val body = json(mapOf(
            "s" to keyword,
            "type" to "1",
            "limit" to "30",
            "offset" to ((page - 1) * 30).toString()
        ))
        emit(NeteaseJson.parseSearch(weapiPost("/weapi/cloudsearch/get/web", body)))
    }

    override fun getSongUrl(songId: String, quality: String): Flow<String> = flow {
        // 老 `/weapi/song/enhance/player/url` + `br` 已被网易废弃（返回空），
        // 改用 eapi v1 + `level`/`encodeType`。付费歌只在更高档返回地址，逐档回退。
        val orderedLevels = buildList {
            qualityLevel(quality)?.let(::add)
            add("exhigh"); add("higher"); add("standard")
        }.distinct()
        val result = orderedLevels.firstNotNullOfOrNull { level ->
            val encodeType = if (level == "lossless") "flac" else "mp3"
            eapiSongUrl(songId, level, encodeType).ifBlank { null }
        }
        emit(result ?: NeteaseJson.fallbackSongUrl(songId))
    }

    /** 音质档位 → 网易云 level；未知档位返回 null（走默认回退链）。 */
    private fun qualityLevel(quality: String): String? = when (quality) {
        "standard" -> "standard"
        "higher" -> "higher"
        "excellent" -> "exhigh"
        "lossless" -> "lossless"
        else -> null
    }

    override fun getLyric(songId: String): Flow<RemoteLyric> = flow {
        val body = json(mapOf("id" to songId, "lv" to "-1", "tv" to "-1"))
        emit(NeteaseJson.parseLyric(weapiPost("/weapi/song/lyric", body)))
    }

    override fun getCoverUrl(songId: String): Flow<String> = flow {
        val body = json(mapOf("c" to "[{\"id\": $songId}]"))
        val cover = NeteaseJson.parseSongDetail(weapiPost("/weapi/v3/song/detail", body))?.coverUrl ?: ""
        emit(cover)
    }

    override fun getSongDetail(songId: String): Flow<RemoteSong> = flow {
        val body = json(mapOf("c" to "[{\"id\": $songId}]"))
        NeteaseJson.parseSongDetail(weapiPost("/weapi/v3/song/detail", body))?.let { emit(it) }
    }

    override fun getPlaylist(playlistId: String): Flow<RemotePlaylist> = flow {
        val body = json(mapOf("id" to playlistId, "n" to "1000", "s" to "0"))
        NeteaseJson.parsePlaylistDetail(weapiPost("/weapi/v6/playlist/detail", body))?.let { emit(it) }
    }

    /** 用户歌单：需登录并解析出 uid；匿名/失败返回空。 */
    override fun getUserPlaylists(): Flow<List<RemotePlaylist>> = flow {
        val u = resolveUid()
        if (u.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val body = json(mapOf("uid" to u, "limit" to "30", "offset" to "0"))
        emit(NeteaseJson.parseUserPlaylists(weapiPost("/weapi/user/playlist", body)))
    }

    /** 私人 FM（电台）：需登录；匿名/失败返回空。 */
    override fun getRadio(): Flow<List<RemoteSong>> = flow {
        emit(NeteaseJson.parseRadio(weapiPost("/weapi/v1/radio/get", "{}")))
    }

    /** 每日推荐歌曲：需登录；匿名/失败返回空。 */
    override fun getDailyRecommendSongs(): Flow<List<RemoteSong>> = flow {
        emit(NeteaseJson.parseDailyRecommend(weapiPost("/weapi/v1/discovery/recommend/songs", "{}")))
    }

    override fun getRecommendations(): Flow<List<RemoteSong>> = flow {
        val body = json(mapOf("limit" to "30"))
        emit(NeteaseJson.parseRecommend(weapiPost("/weapi/personalized/newsong", body)))
    }

    override fun getCharts(): Flow<List<RemoteChart>> = flow {
        emit(NeteaseJson.parseCharts(weapiPost("/weapi/toplist", "{}")))
    }

    // ── 登录 ───────────────────────────────────────────

    override fun loginAccount(username: String, password: String): Flow<Result<Unit>> = flow {
        val isEmail = username.contains("@")
        val body = if (isEmail) {
            json(mapOf(
                "username" to username,
                "password" to Weapi.md5(password),
                "rememberLogin" to "true"
            ))
        } else {
            json(mapOf(
                "phone" to username,
                "countrycode" to "86",
                "password" to Weapi.md5(password),
                "rememberLogin" to "true"
            ))
        }
        val path = if (isEmail) "/weapi/login" else "/weapi/login/cellphone"
        val resp = weapiPostRaw(path, body)
        val ck = resp.cookie.ifBlank { NeteaseJson.parseQrCookie(resp.body) }
        if (NeteaseJson.parseCode(resp.body) == 200 && ck.contains("MUSIC_U")) {
            cookie = ck
            nickname = NeteaseJson.parseLoginNickname(resp.body).ifBlank { username }
            uid = NeteaseJson.parseLoginUid(resp.body)
            _isLoggedIn.value = true
            onLoginChanged(cookie, nickname)
            emit(Result.success(Unit))
        } else {
            val msg = NeteaseJson.parseMessage(resp.body).ifBlank { context.getString(R.string.login_failed) }
            emit(Result.failure(IllegalStateException(msg)))
        }
    }

    /** 从 WebView 登录页拿到的 cookie 注入登录态。 */
    override fun importLogin(cookie: String, nickname: String) {
        this.cookie = cookie
        this.nickname = nickname
        _isLoggedIn.value = cookie.contains("MUSIC_U")
        onLoginChanged(cookie, nickname)
    }

    override fun logout() {
        cookie = ""
        nickname = ""
        uid = ""
        _isLoggedIn.value = false
        onLoginChanged("", "")
    }

    /** 惰性解析登录用户 uid（账号密码登录已带，WebView 导入时按需拉取）。 */
    private suspend fun resolveUid(): String {
        if (uid.isNotBlank()) return uid
        val resp = weapiPost("/weapi/w/nuser/account/get", "{}")
        val u = NeteaseJson.parseLoginUid(resp)
        if (u.isNotBlank()) uid = u
        return uid
    }

    // ── weapi POST ──────────────────────────────────────

    private data class WeapiResp(val body: String, val cookie: String)

    private suspend fun weapiPost(path: String, jsonBody: String): String =
        weapiPostRaw(path, jsonBody).body

    private suspend fun weapiPostRaw(path: String, jsonBody: String): WeapiResp {
        if (cookie.isBlank() && path != ANON_PATH) ensureAnonymousCookie()
        return postOnce(path, jsonBody)
    }

    /** 免登录也要一个游客 cookie（`MUSIC_A`），否则搜索/播放常返回空。 */
    private suspend fun ensureAnonymousCookie() {
        val raw = postOnce(ANON_PATH, "{}").body
        val anon = Cookies.select(NeteaseJson.parseQrCookie(raw), setOf("MUSIC_A"))
        if (anon.isNotBlank()) cookie = anon
    }

    private suspend fun postOnce(path: String, jsonBody: String): WeapiResp =
        withContext(Dispatchers.IO) {
            try {
                val form = Weapi.encrypt(jsonBody)
                val body = FormBody.Builder()
                    .add("params", form.params)
                    .add("encSecKey", form.encSecKey)
                    .build()
                val builder = Request.Builder()
                    .url("https://music.163.com$path")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://music.163.com")
                    .post(body)
                if (cookie.isNotBlank()) builder.header("Cookie", cookie)
                client.newCall(builder.build()).execute().use { resp ->
                    val respBody = if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    WeapiResp(respBody, extractCookie(resp.headers("Set-Cookie"), cookie))
                }
            } catch (_: Exception) {
                WeapiResp("", cookie)
            }
        }

    /** 从 Set-Cookie 里取 `MUSIC_U`/`__csrf`，保留已有值，拼成 Cookie 头。 */
    private fun extractCookie(setCookies: List<String>, existing: String): String {
        if (setCookies.isEmpty()) return existing
        val map = linkedMapOf<String, String>()
        (listOf(existing) + setCookies).forEach { header ->
            header.split(';').forEach { part ->
                val kv = part.trim().split('=', limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank()) map[kv[0]] = kv[1]
            }
        }
        return map.filterKeys { it == "MUSIC_U" || it == "__csrf" }
            .entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun json(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    // ── eapi POST（新版取流接口） ──────────────────────

    /** 单曲取流：eapi v1 + `level`/`encodeType`，返回可播放地址（空串=不可用）。 */
    private suspend fun eapiSongUrl(songId: String, level: String, encodeType: String): String {
        if (cookie.isBlank()) ensureAnonymousCookie()
        val header = eapiHeader()
        val data = JSONObject()
            .put("ids", "[$songId]")
            .put("level", level)
            .put("encodeType", encodeType)
            .put("header", header)
        return eapiPost(SONG_URL_URI, data.toString(), header)
    }

    /** 请求头字段：同时内嵌进加密 data 并拼进 Cookie 头，匿名时带 `MUSIC_A`。 */
    private fun eapiHeader(): JSONObject {
        val now = System.currentTimeMillis()
        val header = JSONObject()
            .put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            .put("deviceId", deviceId)
            .put("os", "pc")
            .put("appver", "3.1.17.204416")
            .put("versioncode", "140")
            .put("mobilename", "")
            .put("buildver", (now / 1000).toString())
            .put("resolution", "1920x1080")
            .put("__csrf", cookieValue("__csrf"))
            .put("channel", "netease")
            .put("requestId", "${now}_${Random.nextInt(0, 10000).toString().padStart(4, '0')}")
        cookieValue("MUSIC_A").takeIf { it.isNotBlank() }?.let { header.put("MUSIC_A", it) }
        cookieValue("MUSIC_U").takeIf { it.isNotBlank() }?.let { header.put("MUSIC_U", it) }
        return header
    }

    private suspend fun eapiPost(uri: String, jsonBody: String, header: JSONObject): String =
        withContext(Dispatchers.IO) {
            try {
                val params = Eapi.encrypt(uri, jsonBody)
                val body = FormBody.Builder().add("params", params).build()
                val builder = Request.Builder()
                    .url("https://interface.music.163.com/eapi/" + uri.removePrefix("/api/"))
                    .header("User-Agent", EAPI_USER_AGENT)
                    .header("Referer", "https://music.163.com")
                    .header("Cookie", eapiCookie(header))
                    .post(body)
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) {
                ""
            }
        }

    /** eapi 的 Cookie 头：header 各字段 URL-编码后 `k=v; k=v` 拼接。 */
    private fun eapiCookie(header: JSONObject): String {
        val parts = mutableListOf<String>()
        val keys = header.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            parts.add("${urlEncode(k)}=${urlEncode(header.optString(k))}")
        }
        return parts.joinToString("; ")
    }

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** 从当前 cookie 串提取单个键值（无则空串）。 */
    private fun cookieValue(name: String): String =
        Regex("""(?:^|;\s*)$name=([^;]*)""").find(cookie)?.groupValues?.get(1)?.trim() ?: ""

    companion object {
        private const val ANON_PATH = "/weapi/register/anonimous"
        private const val SONG_URL_URI = "/api/song/enhance/player/url/v1"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        private const val EAPI_USER_AGENT =
            "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
    }
}
