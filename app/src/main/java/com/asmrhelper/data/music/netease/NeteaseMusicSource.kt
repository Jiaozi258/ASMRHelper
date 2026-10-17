package com.asmrhelper.data.music.netease

import com.asmrhelper.data.music.neteaselib.Weapi
import com.asmrhelper.domain.model.music.QrLoginState
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
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

/** 网易云音乐原生音源：直接请求 music.163.com，weapi 加密。 */
class NeteaseMusicSource(
    private val client: OkHttpClient
) : MusicSource {

    override val id: String = "netease"
    override val displayName: String = "网易云音乐"

    private val _isLoggedIn = MutableStateFlow(false)
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

    // ── 后续任务实现 ─────────────────────────────────────

    override fun getSongUrl(songId: String, quality: String): Flow<String> = flow { emit("") }
    override fun getLyric(songId: String): Flow<RemoteLyric> = flow { emit(RemoteLyric()) }
    override fun getCoverUrl(songId: String): Flow<String> = flow { emit("") }
    override fun getSongDetail(songId: String): Flow<RemoteSong> = flow {}
    override fun getPlaylist(playlistId: String): Flow<RemotePlaylist> = flow { emit(RemotePlaylist("", "")) }
    override fun getRecommendations(): Flow<List<RemoteSong>> = flow { emit(emptyList()) }
    override fun getCharts(): Flow<List<RemoteChart>> = flow { emit(emptyList()) }
    override fun loginAccount(username: String, password: String): Flow<Result<Unit>> =
        flow { emit(Result.failure(IllegalStateException("未实现"))) }
    override fun loginQr(): Flow<QrLoginState> = flow { emit(QrLoginState.Failed("未实现")) }
    override fun logout() { _isLoggedIn.value = false }

    // ── weapi POST ──────────────────────────────────────

    private suspend fun weapiPost(path: String, jsonBody: String): String =
        withContext(Dispatchers.IO) {
            try {
                val form = Weapi.encrypt(jsonBody)
                val body = FormBody.Builder()
                    .add("params", form.params)
                    .add("encSecKey", form.encSecKey)
                    .build()
                val req = Request.Builder()
                    .url("https://music.163.com$path")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://music.163.com")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) {
                ""
            }
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
