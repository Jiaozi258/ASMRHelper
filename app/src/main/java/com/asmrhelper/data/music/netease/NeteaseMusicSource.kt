package com.asmrhelper.data.music.netease

import com.asmrhelper.data.music.neteaselib.Weapi
import com.asmrhelper.domain.model.music.QrLoginState
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** 网易云音乐原生音源：直接请求 music.163.com，weapi 加密。 */
class NeteaseMusicSource(
    private val client: OkHttpClient,
    persistedCookie: String = "",
    persistedNickname: String = "",
    private val onLoginChanged: (cookie: String, nickname: String) -> Unit = { _, _ -> }
) : MusicSource {

    override val id: String = "netease"
    override val displayName: String = "网易云音乐"

    private var cookie: String = persistedCookie
    private var nickname: String = persistedNickname

    private val _isLoggedIn = MutableStateFlow(cookie.contains("MUSIC_U"))
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

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
        val body = json(mapOf(
            "ids" to "[$songId]",
            "level" to "standard",
            "encodeType" to "aac"
        ))
        emit(NeteaseJson.parseSongUrl(weapiPost("/weapi/song/enhance/player/url/v1", body)))
    }

    override fun getLyric(songId: String): Flow<RemoteLyric> = flow {
        val body = json(mapOf("id" to songId, "lv" to "-1", "tv" to "-1"))
        emit(NeteaseJson.parseLyric(weapiPost("/weapi/song/lyric", body)))
    }

    override fun getCoverUrl(songId: String): Flow<String> = flow { emit("") }
    override fun getSongDetail(songId: String): Flow<RemoteSong> = flow {}
    override fun getPlaylist(playlistId: String): Flow<RemotePlaylist> = flow { emit(RemotePlaylist("", "")) }

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
            _isLoggedIn.value = true
            onLoginChanged(cookie, nickname)
            emit(Result.success(Unit))
        } else {
            val msg = NeteaseJson.parseMessage(resp.body).ifBlank { "登录失败" }
            emit(Result.failure(IllegalStateException(msg)))
        }
    }

    override fun loginQr(): Flow<QrLoginState> = flow {
        val keyResp = weapiPostRaw("/weapi/login/qr/key", """{"type":"1"}""")
        val key = NeteaseJson.parseQrKey(keyResp.body)
        if (key.isBlank()) {
            emit(QrLoginState.Failed("获取二维码失败"))
            return@flow
        }
        val createResp = weapiPostRaw("/weapi/login/qr/create?qrimg=true", """{"key":"$key","qrimg":true}""")
        val qrimg = NeteaseJson.parseQrImg(createResp.body)
        if (qrimg.isBlank()) {
            emit(QrLoginState.Failed("生成二维码失败"))
            return@flow
        }
        emit(QrLoginState.Waiting(key, qrimg))

        while (true) {
            delay(2000L)
            val checkResp = weapiPostRaw("/weapi/login/qr/check", """{"key":"$key","type":"1"}""")
            when (NeteaseJson.parseCode(checkResp.body)) {
                800 -> { emit(QrLoginState.Expired(key)); break }
                801 -> Unit // 未扫码，继续轮询
                802 -> emit(QrLoginState.Scanned(key))
                803 -> {
                    cookie = checkResp.cookie.ifBlank { NeteaseJson.parseQrCookie(checkResp.body) }
                    nickname = NeteaseJson.parseQrNickname(checkResp.body)
                    _isLoggedIn.value = true
                    onLoginChanged(cookie, nickname)
                    emit(QrLoginState.Confirmed(nickname))
                    break
                }
            }
        }
    }

    override fun logout() {
        cookie = ""
        nickname = ""
        _isLoggedIn.value = false
        onLoginChanged("", "")
    }

    // ── weapi POST ──────────────────────────────────────

    private data class WeapiResp(val body: String, val cookie: String)

    private suspend fun weapiPost(path: String, jsonBody: String): String =
        weapiPostRaw(path, jsonBody).body

    private suspend fun weapiPostRaw(path: String, jsonBody: String): WeapiResp =
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

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
}
