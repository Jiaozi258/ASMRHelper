package com.asmrhelper.data.music.qq

import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
import com.asmrhelper.domain.model.music.RemoteSong
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** QQ 音乐接口响应的纯解析（org.json，异常一律返回空/空串，不抛）。 */
object QqMusicJson {

    private val LYRIC_TIME = Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")

    fun parseSearch(json: String): List<RemoteSong> = try {
        val items = JSONObject(json).optJSONObject("req_0")?.optJSONObject("data")
            ?.optJSONObject("body")?.optJSONObject("item_song")?.optJSONArray("items")
            ?: return emptyList()
        val songs = mutableListOf<RemoteSong>()
        for (i in 0 until items.length()) {
            val s = items.optJSONObject(i) ?: continue
            songs.add(parseSong(s))
        }
        songs
    } catch (_: Exception) {
        emptyList()
    }

    fun parseSongUrl(json: String): String = try {
        val data = JSONObject(json).optJSONObject("req_0")?.optJSONObject("data") ?: return ""
        val info = data.optJSONArray("midurlinfo")?.optJSONObject(0) ?: return ""
        val purl = info.optString("purl")
        val sip = data.optJSONArray("sip")
        if (info.optInt("result") == 0 && purl.isNotBlank() && sip != null && sip.length() > 0) {
            sip.optString(0) + purl
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }

    fun parseLyric(json: String): RemoteLyric = try {
        val lyric = JSONObject(json).optJSONObject("req_0")?.optJSONObject("data")
            ?.optString("lyric") ?: return RemoteLyric()
        val raw = String(Base64.getDecoder().decode(lyric), Charsets.UTF_8)
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

    // ── 内部 ────────────────────────────────────────────

    private fun parseSong(s: JSONObject): RemoteSong {
        val artists = s.optJSONArray("singer")?.let { a ->
            val names = mutableListOf<String>()
            for (j in 0 until a.length()) {
                a.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
            names.joinToString(" / ")
        } ?: ""
        val album = s.optJSONObject("album")
        val albumMid = album?.optString("mid") ?: ""
        val cover = albumMid.takeIf { it.isNotBlank() }
            ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" }
        return RemoteSong(
            id = s.optString("mid"),
            title = s.optString("title").ifBlank { s.optString("name") },
            artist = artists,
            album = album?.optString("name") ?: "",
            durationMs = s.optLong("interval", 0L) * 1000L,
            coverUrl = cover,
            sourceId = "qq"
        )
    }
}
