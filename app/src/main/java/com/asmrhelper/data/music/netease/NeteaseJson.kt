package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import org.json.JSONArray
import org.json.JSONObject

/** 网易云接口响应的纯解析（org.json，异常一律返回空/空串，不抛）。 */
object NeteaseJson {

    private val LYRIC_TIME = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")

    fun parseSearch(json: String): List<RemoteSong> = try {
        parseSongArray(JSONObject(json).optJSONObject("result")?.optJSONArray("songs"))
    } catch (_: Exception) {
        emptyList()
    }

    fun parseSongUrl(json: String): String = try {
        val data = JSONObject(json).optJSONArray("data")
        val url = data?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() } ?: ""
        https(url)
    } catch (_: Exception) {
        ""
    }

    /** 播放地址兜底：网易云免费歌外链，302 跳转到真实 mp3，免登录可用。 */
    fun fallbackSongUrl(id: String): String = "https://music.163.com/song/media/outer/url?id=$id.mp3"

    fun parseLyric(json: String): RemoteLyric = try {
        val obj = JSONObject(json)
        val raw = obj.optJSONObject("lrc")?.optString("lyric") ?: ""
        val yrc = obj.optJSONObject("yrc")?.optString("lyric") ?: ""
        val tlyric = obj.optJSONObject("tlyric")?.optString("lyric") ?: ""
        val romalrc = obj.optJSONObject("romalrc")?.optString("lyric") ?: ""
        RemoteLyric(
            lines = parseTimestampedLines(raw),
            rawText = raw,
            yrc = yrc,
            translatedLines = parseTimestampedLines(tlyric),
            romanizedLines = parseTimestampedLines(romalrc)
        )
    } catch (_: Exception) {
        RemoteLyric()
    }

    /** 解析带 `[mm:ss.xx]` 时间标签的歌词文本为带时间戳的行（供 lrc/tlyric/romalrc 复用）。 */
    private fun parseTimestampedLines(raw: String): List<RemoteLyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = mutableListOf<RemoteLyricLine>()
        for (line in raw.split('\n')) {
            val m = LYRIC_TIME.find(line) ?: continue
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toDouble()
            val timeMs = min * 60_000L + (sec * 1000).toLong()
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) lines.add(RemoteLyricLine(timeMs, text))
        }
        return lines
    }

    fun parseCharts(json: String): List<RemoteChart> = try {
        val list = JSONObject(json).optJSONArray("list") ?: return emptyList()
        val charts = mutableListOf<RemoteChart>()
        for (i in 0 until list.length()) {
            val c = list.optJSONObject(i) ?: continue
            charts.add(
                RemoteChart(
                    id = c.optLong("id").toString(),
                    name = c.optString("name"),
                    songs = parseSongArray(c.optJSONArray("tracks"))
                )
            )
        }
        charts
    } catch (_: Exception) {
        emptyList()
    }

    fun parseRecommend(json: String): List<RemoteSong> = try {
        val result = JSONObject(json).optJSONArray("result") ?: return emptyList()
        val songs = mutableListOf<RemoteSong>()
        for (i in 0 until result.length()) {
            val item = result.optJSONObject(i) ?: continue
            // personalized/newsong：歌信息在 `song` 内；搜索/榜单形状则直接是歌对象
            songs.add(parseSong(item.optJSONObject("song") ?: item))
        }
        songs
    } catch (_: Exception) {
        emptyList()
    }

    /** 单曲详情（`/weapi/v3/song/detail` → songs[0]）。 */
    fun parseSongDetail(json: String): RemoteSong? = try {
        JSONObject(json).optJSONArray("songs")?.optJSONObject(0)?.let { parseSong(it) }
    } catch (_: Exception) {
        null
    }

    /** 歌单详情（`/weapi/v6/playlist/detail` → playlist）。 */
    fun parsePlaylistDetail(json: String): RemotePlaylist? = try {
        val p = JSONObject(json).optJSONObject("playlist") ?: return null
        RemotePlaylist(
            id = p.optLong("id").toString(),
            name = p.optString("name"),
            coverUrl = p.optString("coverImgUrl").takeIf { it.isNotBlank() },
            songs = parseSongArray(p.optJSONArray("tracks"))
        )
    } catch (_: Exception) {
        null
    }

    /** 用户歌单列表（`/weapi/user/playlist` → playlist[]，仅名称/封面）。 */
    fun parseUserPlaylists(json: String): List<RemotePlaylist> = try {
        val arr = JSONObject(json).optJSONArray("playlist") ?: return emptyList()
        val list = mutableListOf<RemotePlaylist>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            list.add(
                RemotePlaylist(
                    id = p.optLong("id").toString(),
                    name = p.optString("name"),
                    coverUrl = p.optString("coverImgUrl").takeIf { it.isNotBlank() }
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }

    /** 私人 FM（`/weapi/v1/radio/get` → data[]）。 */
    fun parseRadio(json: String): List<RemoteSong> = try {
        parseSongArray(JSONObject(json).optJSONArray("data"))
    } catch (_: Exception) {
        emptyList()
    }

    /** 每日推荐（`/weapi/v1/discovery/recommend/songs` → data.dailySongs[] 或 recommend[]）。 */
    fun parseDailyRecommend(json: String): List<RemoteSong> = try {
        val obj = JSONObject(json)
        val daily = parseSongArray(obj.optJSONObject("data")?.optJSONArray("dailySongs"))
        if (daily.isNotEmpty()) daily else parseSongArray(obj.optJSONArray("recommend"))
    } catch (_: Exception) {
        emptyList()
    }

    /** 登录响应中的用户 uid（account.id 或 profile.userId）。 */
    fun parseLoginUid(json: String): String = try {
        val obj = JSONObject(json)
        obj.optJSONObject("account")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: obj.optJSONObject("profile")?.optString("userId")?.takeIf { it.isNotBlank() }
            ?: ""
    } catch (_: Exception) {
        ""
    }

    // ── 登录 ────────────────────────────────────────────

    fun parseCode(json: String): Int = try {
        JSONObject(json).optInt("code")
    } catch (_: Exception) {
        -1
    }

    fun parseMessage(json: String): String = try {
        JSONObject(json).optString("message")
    } catch (_: Exception) {
        ""
    }

    fun parseQrCookie(json: String): String = try {
        JSONObject(json).optString("cookie")
    } catch (_: Exception) {
        ""
    }

    fun parseLoginNickname(json: String): String = try {
        JSONObject(json).optJSONObject("profile")?.optString("nickname") ?: ""
    } catch (_: Exception) {
        ""
    }

    // ── 内部 ────────────────────────────────────────────

    /** Android 默认禁明文 HTTP；网易云封面/流地址常返回 `http://`，统一转 https（CDN 支持 https）。 */
    private fun https(url: String): String =
        if (url.startsWith("http://")) "https://" + url.substring(7) else url

    private fun parseSongArray(arr: JSONArray?): List<RemoteSong> {
        if (arr == null) return emptyList()
        val list = mutableListOf<RemoteSong>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            list.add(parseSong(s))
        }
        return list
    }

    private fun parseSong(s: JSONObject): RemoteSong {
        val ar = s.optJSONArray("ar") ?: s.optJSONArray("artists")
        val artists = ar?.let { a ->
            val names = mutableListOf<String>()
            for (j in 0 until a.length()) {
                a.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
            names.joinToString(" / ")
        } ?: ""
        val al = s.optJSONObject("al") ?: s.optJSONObject("album")
        val cover = (al?.optString("picUrl")?.takeIf { it.isNotBlank() }
            ?: s.optString("picUrl").takeIf { it.isNotBlank() })
            ?.let { https(it) }
        val duration = if (s.has("dt")) s.optLong("dt") else s.optLong("duration", 0L)
        val publishTime = s.optLong("publishTime", 0L).takeIf { it > 0 }
            ?: al?.optLong("publishTime", 0L) ?: 0L
        return RemoteSong(
            id = s.optLong("id").toString(),
            title = s.optString("name"),
            artist = artists,
            album = al?.optString("name") ?: "",
            durationMs = duration,
            coverUrl = cover,
            sourceId = "netease",
            publishTime = publishTime
        )
    }
}
