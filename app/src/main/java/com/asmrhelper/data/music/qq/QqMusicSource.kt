package com.asmrhelper.data.music.qq

import android.content.Context
import com.asmrhelper.R
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.WebLoginConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** QQ 音乐原生音源：直接 POST `u.y.qq.com/cgi-bin/musics.fcg`，zzc 签名，匿名 `uin=0`。 */
class QqMusicSource(
    private val client: OkHttpClient,
    private val context: Context,
    persistedCookie: String = "",
    persistedNickname: String = "",
    private val onLoginChanged: (cookie: String, nickname: String) -> Unit = { _, _ -> }
) : MusicSource {

    override val id: String = "qq"
    override val displayNameRes: Int = R.string.source_qq

    private var cookie: String = persistedCookie
    private var nickname: String = persistedNickname

    private val _isLoggedIn = MutableStateFlow(persistedCookie.contains("uin"))
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    override val webLogin: WebLoginConfig =
        WebLoginConfig("https://y.qq.com/", "https://y.qq.com", setOf("qm_keyst", "uin"))

    override fun search(keyword: String, page: Int): Flow<List<RemoteSong>> = flow {
        val param = JSONObject()
            .put("searchid", "12345678901234567890")
            .put("query", keyword)
            .put("search_type", 0)
            .put("page_num", 30)
            .put("page_id", page)
            .put("highlight", 1)
            .put("grp", 1)
        emit(QqMusicJson.parseSearch(post("music.adaptor.SearchAdaptor", "do_search_v2", param)))
    }

    override fun getSongUrl(songId: String, quality: String): Flow<String> = flow {
        // 免登录可拿免费歌地址；VIP/绿钻歌返回 result=104003 → 空串。
        val param = JSONObject()
            .put("uin", "0")
            .put("guid", "10000")
            .put("songmid", JSONArray().put(songId))
            .put("filename", JSONArray().put("M500$songId$songId.mp3"))
            .put("songtype", JSONArray().put(0))
            .put("loginflag", 1)
            .put("platform", "20")
        emit(QqMusicJson.parseSongUrl(post("music.vkey.GetVkey", "UrlGetVkey", param)))
    }

    override fun getLyric(songId: String): Flow<RemoteLyric> = flow {
        val param = JSONObject()
            .put("songMid", songId)
            .put("crypt", 0)
            .put("lrc_t", 0)
            .put("qrc", 0)
            .put("roma", 0)
            .put("trans", 0)
        emit(QqMusicJson.parseLyric(post("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", param)))
    }

    override fun getCoverUrl(songId: String): Flow<String> = flow { emit("") }
    override fun getSongDetail(songId: String): Flow<RemoteSong> = flow {}
    override fun getPlaylist(playlistId: String): Flow<RemotePlaylist> = flow { emit(RemotePlaylist("", "")) }

    /** 排行榜/推荐需登录；未登录时匿名返回 500003，解析器返回空，不崩溃。 */
    override fun getRecommendations(): Flow<List<RemoteSong>> = flow {
        val param = JSONObject().put("offset", 0).put("limit", 30)
        emit(QqMusicJson.parseRecommendations(post("music.recommend.RecommendAdaptor", "getRecommendation", param)))
    }

    override fun getCharts(): Flow<List<RemoteChart>> = flow {
        emit(QqMusicJson.parseCharts(post("musicToplist.ToplistInfo", "GetAll", JSONObject())))
    }

    // ── 登录（账号密码走 QQ 网页登录页；WebView 抓 Cookie 后 importLogin） ──

    override fun loginAccount(username: String, password: String): Flow<Result<Unit>> = flow {
        emit(Result.failure(IllegalStateException(context.getString(R.string.login_qq_use_web))))
    }

    override fun importLogin(cookie: String, nickname: String) {
        this.cookie = cookie
        this.nickname = nickname
        _isLoggedIn.value = cookie.contains("uin")
        onLoginChanged(cookie, nickname)
    }

    override fun logout() {
        cookie = ""
        nickname = ""
        _isLoggedIn.value = false
        onLoginChanged("", "")
    }

    // ── zzc POST ───────────────────────────────────────

    private suspend fun post(module: String, method: String, param: JSONObject): String =
        withContext(Dispatchers.IO) {
            try {
                val comm = JSONObject()
                    .put("cv", 4747474)
                    .put("ct", 24)
                    .put("format", "json")
                    .put("inCharset", "utf-8")
                    .put("outCharset", "utf-8")
                    .put("notice", 0)
                    .put("platform", "yqq.json")
                    .put("needNewCode", 1)
                    .put("uin", QqMusicJson.uin(cookie).toLongOrNull() ?: 0L)
                val payload = JSONObject()
                    .put("comm", comm)
                    .put("req_0", JSONObject().put("module", module).put("method", method).put("param", param))
                val body = payload.toString()
                val url = "https://u.y.qq.com/cgi-bin/musics.fcg?_=${System.currentTimeMillis()}&sign=${QqMusicSign.sign(body)}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://y.qq.com/")
                    .header("Content-Type", "application/json")
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) {
                ""
            }
        }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
