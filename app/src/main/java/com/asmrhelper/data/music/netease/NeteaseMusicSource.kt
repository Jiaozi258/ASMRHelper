package com.asmrhelper.data.music.netease

import android.content.Context
import com.asmrhelper.R
import com.asmrhelper.data.music.neteaselib.Eapi
import com.asmrhelper.data.music.neteaselib.PublicKeyState
import com.asmrhelper.data.music.neteaselib.Weapi
import com.asmrhelper.data.music.neteaselib.Xeapi
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.WebLoginConfig
import java.security.MessageDigest
import java.util.Base64
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

/**
 * 网易云音乐原生音源。接口加密方式对齐 Folia（@neteasecloudmusicapienhanced/api）：
 * 搜索/歌词/歌单/榜单走 eapi，取流与游客注册走 xeapi（X25519 ECDH + AES-GCM/ECB），
 * 歌曲详情/推荐/用户歌单/电台/登录仍走 weapi。
 */
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

    private val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)

    /** 52 位大写 hex 设备指纹，持久化避免每次启动重复注册游客触发风控。 */
    private val deviceId: String by lazy {
        prefs.getString("netease_device_id", null)?.takeIf { it.isNotBlank() }
            ?: buildString { repeat(52) { append("0123456789ABCDEF"[Random.nextInt(16)]) } }
                .also { prefs.edit().putString("netease_device_id", it).apply() }
    }

    /** xeapi 公钥状态缓存（内存 + SharedPreferences）。 */
    private var cachedPublicKeyState: PublicKeyState? = null

    private val _isLoggedIn = MutableStateFlow(cookie.contains("MUSIC_U"))
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    override val supportsAccountLogin: Boolean = true
    override val webLogin: WebLoginConfig =
        // `__csrf` 在登录页匿名状态下也会下发，会导致"未登录也判登录"；仅以 MUSIC_U 为准。
        WebLoginConfig("https://music.163.com/#/login", "https://music.163.com", setOf("MUSIC_U"))

    override fun search(keyword: String, page: Int): Flow<List<RemoteSong>> = flow {
        ensureAnonymousSession()
        val data = JSONObject()
            .put("s", keyword)
            .put("type", 1)
            .put("limit", 30)
            .put("offset", (page - 1) * 30)
            .put("total", true)
        emit(NeteaseJson.parseSearch(eapiPost(SEARCH_URI, data)))
    }

    override fun getSongUrl(songId: String, quality: String): Flow<String> = flow {
        // 网易已废弃 weapi 与普通 eapi 取流，改用 xeapi v1 + `level`/`encodeType`。
        // 付费/会员歌只在更高档返回地址，逐档回退。
        ensureAnonymousSession()
        val orderedLevels = buildList {
            qualityLevel(quality)?.let(::add)
            add("exhigh"); add("standard")
        }.distinct()
        val result = orderedLevels.firstNotNullOfOrNull { level ->
            val encodeType = if (level == "lossless") "flac" else "mp3"
            val resp = xeapiPost(
                SONG_URL_URI,
                mapOf("ids" to "[$songId]", "level" to level, "encodeType" to encodeType),
            )
            NeteaseJson.parseSongUrl(resp.body).ifBlank { null }
        }
        emit(result ?: NeteaseJson.fallbackSongUrl(songId))
    }

    /** 音质档位 → 网易云 xeapi level；未知档位返回 null（走默认回退链）。 */
    private fun qualityLevel(quality: String): String? = when (quality) {
        "standard" -> "standard"
        "higher" -> "higher"
        "excellent" -> "exhigh"
        "lossless" -> "lossless"
        else -> null
    }

    override fun getLyric(songId: String): Flow<RemoteLyric> = flow {
        ensureAnonymousSession()
        val data = JSONObject()
            .put("id", songId)
            .put("tv", -1)
            .put("lv", -1)
            .put("rv", -1)
            .put("kv", -1)
            .put("_nmclfl", 1)
        emit(NeteaseJson.parseLyric(eapiPost(LYRIC_URI, data)))
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
        ensureAnonymousSession()
        val data = JSONObject().put("id", playlistId).put("n", 100000).put("s", 8)
        NeteaseJson.parsePlaylistDetail(eapiPost(PLAYLIST_URI, data))?.let { emit(it) }
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
        ensureAnonymousSession()
        emit(NeteaseJson.parseCharts(eapiPost(TOPLIST_URI, JSONObject())))
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
        if (cookie.isBlank()) ensureAnonymousSession()
        return postOnce(path, jsonBody)
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

    // ── 游客会话（xeapi） ─────────────────────────────

    /** 免登录也要一个游客 cookie（`MUSIC_A`），否则搜索/播放常返回空。 */
    private suspend fun ensureAnonymousSession() {
        if (cookie.contains("MUSIC_U") || cookie.contains("MUSIC_A")) return
        val state = loadOrFetchPublicKey() ?: return
        val musicA = registerAnonymous(state)
        if (musicA.isNotBlank()) {
            cookie = musicA
            onLoginChanged(cookie, nickname)
        }
    }

    /** 拉取并缓存 xeapi 公钥（`/api/bsr/sk/get`，明文 + HMAC 签名）。 */
    private suspend fun loadOrFetchPublicKey(): PublicKeyState? {
        cachedPublicKeyState?.let { return it }
        prefs.getString("netease_xeapi_pk", null)?.let { saved ->
            runCatching {
                val o = JSONObject(saved)
                PublicKeyState(
                    version = o.optString("version"),
                    publicKey = o.optString("publicKey"),
                    sk = o.optString("sk").ifBlank { null },
                )
            }.getOrNull()?.let { cachedPublicKeyState = it; return it }
        }
        return withContext(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis().toString()
                val nonce = buildString { repeat(16) { append(Random.nextInt(10)) } }
                val body = FormBody.Builder()
                    .add("appVersion", "9.5.61")
                    .add("currentKeyVersion", "")
                    .add("deviceId", deviceId)
                    .add("nonce", nonce)
                    .add("os", "android")
                    .add("requestType", "active")
                    .add("signature", Xeapi.xeapiSign(ts, nonce))
                    .add("t1", "")
                    .add("t2", "")
                    .add("timestamp", ts)
                    .add("uid", "")
                    .build()
                val req = Request.Builder()
                    .url(PUBLIC_KEY_URL)
                    .header("User-Agent", XEAPI_ANDROID_UA)
                    .header("Cookie", "deviceId=${urlEncode(deviceId)}")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: return@use null
                    val encrypted = JSONObject(text).optJSONObject("data")?.optString("encryptedData")
                        ?.takeIf { it.isNotBlank() } ?: return@use null
                    val state = Xeapi.decryptPublicKey(encrypted)
                    cachedPublicKeyState = state
                    prefs.edit().putString("netease_xeapi_pk", JSONObject()
                        .put("version", state.version)
                        .put("publicKey", state.publicKey)
                        .put("sk", state.sk ?: "")
                        .toString()).apply()
                    state
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /** 游客注册：返回 `MUSIC_A=...` cookie（失败返回空串）。 */
    private suspend fun registerAnonymous(state: PublicKeyState): String {
        val cmeid = cloudmusicDllEncodeId(deviceId)
        val encodedId = Base64.getEncoder().encodeToString("$deviceId $cmeid".toByteArray(Charsets.UTF_8))
        val resp = xeapiPost(ANON_URI, mapOf("username" to encodedId))
        val musicA = extractCookieValue(resp.setCookies, "MUSIC_A")
        return if (musicA.isNotBlank()) "MUSIC_A=$musicA" else ""
    }

    /** 游客注册的 cmeid 计算（deviceId 循环异或固定 key 后 MD5 + base64）。 */
    private fun cloudmusicDllEncodeId(id: String): String {
        val xored = StringBuilder(id.length)
        for (i in id.indices) {
            xored.append((id[i].code xor ID_XOR_KEY[i % ID_XOR_KEY.length].code).toChar())
        }
        val digest = MessageDigest.getInstance("MD5").digest(xored.toString().toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    // ── xeapi POST ──────────────────────────────────────

    private data class XeapiResp(val body: String, val setCookies: List<String>)

    private suspend fun xeapiPost(uri: String, data: Map<String, String>): XeapiResp {
        val state = loadOrFetchPublicKey() ?: return XeapiResp("", emptyList())
        return withContext(Dispatchers.IO) {
            try {
                val form = Xeapi.xeapi(data, state, "android", "", "")
                val body = FormBody.Builder()
                    .add("C", form.c).add("S", form.s).add("R", form.r)
                    .build()
                val builder = Request.Builder()
                    .url("$XEAPI_BASE/xeapi/" + uri.removePrefix("/api/"))
                    .header("User-Agent", XEAPI_ANDROID_UA)
                    .header("X-Client-Enc-State", "ENCRYPTED")
                    .header("x-aeapi", "true")
                    .header("content-type", "application/x-www-form-urlencoded;charset=utf-8")
                    .header("x-deviceid", deviceId)
                    .header("x-os", "android")
                    .header("x-osver", "16")
                    .header("x-appver", "9.5.61")
                    .header("x-sdeviceid", deviceId)
                    .header("x-buildver", (System.currentTimeMillis() / 1000).toString())
                    .header("Cookie", buildXeapiCookie())
                    .post(body)
                cookieValue("MUSIC_U").takeIf { it.isNotBlank() }?.let { builder.header("x-music-u", it) }
                client.newCall(builder.build()).execute().use { resp ->
                    val bytes = if (resp.isSuccessful) resp.body?.bytes() ?: ByteArray(0) else ByteArray(0)
                    val decrypted = if (bytes.isNotEmpty()) Xeapi.decryptResponse(bytes) else ""
                    XeapiResp(decrypted, resp.headers("Set-Cookie"))
                }
            } catch (_: Exception) {
                XeapiResp("", emptyList())
            }
        }
    }

    /** xeapi 的 Cookie 头：android 指纹 + 游客/登录 token，各字段 URL 编码。 */
    private fun buildXeapiCookie(): String {
        val now = System.currentTimeMillis()
        val nuid = randomHex(16)
        val map = linkedMapOf(
            "os" to "android",
            "appver" to "9.5.61",
            "osver" to "16",
            "channel" to "xiaomi",
            "__remember_me" to "true",
            "ntes_kaola_ad" to "1",
            "_ntes_nuid" to nuid,
            "_ntes_nnid" to "$nuid,$now",
            "WNMCID" to "abcdef.$now.01.0",
            "WEVNSM" to "1.0.0",
            "NMTID" to "00O" + randomHex(16),
            "buildver" to (now / 1000).toString(),
            "deviceId" to deviceId,
            "sDeviceId" to deviceId,
        )
        cookieValue("MUSIC_A").takeIf { it.isNotBlank() }?.let { map["MUSIC_A"] = it }
        cookieValue("MUSIC_U").takeIf { it.isNotBlank() }?.let { map["MUSIC_U"] = it }
        return map.entries.joinToString("; ") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }
    }

    private fun randomHex(bytes: Int): String =
        Random.nextBytes(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun extractCookieValue(setCookies: List<String>, name: String): String {
        for (c in setCookies) {
            Regex("""(?:^|;\s*)$name=([^;]+)""").find(c)?.let { return it.groupValues[1] }
        }
        return ""
    }

    // ── eapi POST ──────────────────────────────────────

    /** eapi 请求：`data` 内嵌 header 后 AES-128-ECB 加密，POST 到 eapi 域名。 */
    private suspend fun eapiPost(uri: String, data: JSONObject): String {
        if (cookie.isBlank()) ensureAnonymousSession()
        val header = eapiHeader()
        data.put("header", header)
        return withContext(Dispatchers.IO) {
            try {
                val params = Eapi.encrypt(uri, data.toString())
                val body = FormBody.Builder().add("params", params).build()
                val builder = Request.Builder()
                    .url("$EAPI_BASE/eapi/" + uri.removePrefix("/api/"))
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
        private const val ID_XOR_KEY = "3go8&$8*3*3h0k(2)2"

        private const val ANON_URI = "/api/register/anonimous"
        private const val SONG_URL_URI = "/api/song/enhance/player/url/v1"
        private const val SEARCH_URI = "/api/cloudsearch/pc"
        private const val LYRIC_URI = "/api/song/lyric"
        private const val PLAYLIST_URI = "/api/v6/playlist/detail"
        private const val TOPLIST_URI = "/api/toplist"

        private const val PUBLIC_KEY_URL = "https://interface.music.163.com/api/bsr/sk/get"
        private const val XEAPI_BASE = "https://interface3.music.163.com"
        private const val EAPI_BASE = "https://interfacepc.music.163.com"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        private const val EAPI_USER_AGENT =
            "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
        private const val XEAPI_ANDROID_UA =
            "NeteaseMusic/9.5.61.260802021928(9005061);Dalvik/2.1.0 (Linux; U; Android 12; HBN-AL00 Build/cd737a2.0)"
    }
}
