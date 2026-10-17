package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
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
        data?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() } ?: ""
    } catch (_: Exception) {
        ""
    }

    /** 播放地址兜底：网易云免费歌外链，302 跳转到真实 mp3，免登录可用。 */
    fun fallbackSongUrl(id: String): String = "https://music.163.com/song/media/outer/url?id=$id.mp3"

    fun parseLyric(json: String): RemoteLyric = try {
        val raw = JSONObject(json).optJSONObject("lrc")?.optString("lyric") ?: ""
        val lines = mutableListOf<RemoteLyricLine>()
        for (line in raw.split('\n')) {
            val m = LYRIC_TIME.find(line) ?: continue
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toDouble()
            val timeMs = min * 60_000L + (sec * 1000).toLong()
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) lines.add(RemoteLyricLine(timeMs, text))
        }
        RemoteLyric(lines = lines, rawText = raw)
    } catch (_: Exception) {
        RemoteLyric()
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
        val cover = al?.optString("picUrl")?.takeIf { it.isNotBlank() }
            ?: s.optString("picUrl").takeIf { it.isNotBlank() }
        val duration = if (s.has("dt")) s.optLong("dt") else s.optLong("duration", 0L)
        return RemoteSong(
            id = s.optLong("id").toString(),
            title = s.optString("name"),
            artist = artists,
            album = al?.optString("name") ?: "",
            durationMs = duration,
            coverUrl = cover,
            sourceId = "netease"
        )
    }
}
