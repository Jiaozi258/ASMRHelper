package com.asmrhelper.data.music

import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import org.json.JSONArray
import org.json.JSONObject

/** 账号密码登录结果。 */
data class LoginResult(val cookie: String, val username: String)

/** 扫码状态轮询结果。 */
data class QrCheckResult(val status: String, val username: String)

/**
 * 「音源地址」JSON 归一化解析（纯函数，无网络，直接可测）。
 *
 * 所有解析包裹 try/catch，异常返回空值/空串（不抛），保证 UI 永不因脏数据崩溃。
 * 统一契约见 `HttpMusicSource` KDoc。
 */
object MusicJsonAdapters {

    fun parseSearch(json: String): List<RemoteSong> = parseSongList(json)
    fun parseRecommendations(json: String): List<RemoteSong> = parseSongList(json)

    private fun parseSongList(json: String): List<RemoteSong> {
        val data = unwrapData(json) ?: return emptyList()
        return try {
            val arr = data as? JSONArray ?: return emptyList()
            (0 until arr.length()).mapNotNull { i -> parseSong(arr.optJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseSongUrl(json: String): String {
        val data = unwrapData(json) ?: return ""
        return try {
            (data as? JSONObject)?.optString("url", "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun parseSongDetail(json: String): RemoteSong? {
        val data = unwrapData(json) ?: return null
        return try {
            parseSong(data as? JSONObject)
        } catch (_: Exception) {
            null
        }
    }

    fun parseLyric(json: String): RemoteLyric {
        val data = unwrapData(json) ?: return RemoteLyric()
        val lrc = try {
            (data as? JSONObject)?.optString("lrc", "") ?: ""
        } catch (_: Exception) {
            ""
        }
        return RemoteLyric(lines = parseLrcLines(lrc), rawText = lrc)
    }

    fun parsePlaylist(json: String): RemotePlaylist {
        val data = unwrapData(json) ?: return RemotePlaylist("", "")
        return try {
            val o = data as? JSONObject ?: return RemotePlaylist("", "")
            RemotePlaylist(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                coverUrl = o.optString("cover", "").ifBlank { null },
                songs = parseSongArray(o.optJSONArray("songs"))
            )
        } catch (_: Exception) {
            RemotePlaylist("", "")
        }
    }

    fun parseCharts(json: String): List<RemoteChart> {
        val data = unwrapData(json) ?: return emptyList()
        return try {
            val arr = data as? JSONArray ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RemoteChart(
                    id = o.optString("id", ""),
                    name = o.optString("name", ""),
                    songs = parseSongArray(o.optJSONArray("songs"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseLogin(json: String): LoginResult {
        val data = unwrapData(json) ?: return LoginResult("", "")
        return try {
            val o = data as? JSONObject ?: return LoginResult("", "")
            LoginResult(
                cookie = o.optString("cookie", ""),
                username = o.optString("username", "")
            )
        } catch (_: Exception) {
            LoginResult("", "")
        }
    }

    fun parseQrKey(json: String): String {
        val data = unwrapData(json) ?: return ""
        return try {
            (data as? JSONObject)?.optString("key", "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun parseQrCheck(json: String): QrCheckResult {
        val data = unwrapData(json) ?: return QrCheckResult("", "")
        return try {
            val o = data as? JSONObject ?: return QrCheckResult("", "")
            QrCheckResult(
                status = o.optString("status", ""),
                username = o.optString("username", "")
            )
        } catch (_: Exception) {
            QrCheckResult("", "")
        }
    }

    /** 取包裹层的 data；`code != 0` 或缺失视为失败返回 null。 */
    private fun unwrapData(json: String): Any? {
        return try {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return null
            if (!root.has("data") || root.isNull("data")) return null
            root.get("data")
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSong(o: JSONObject?): RemoteSong? {
        if (o == null) return null
        return RemoteSong(
            id = o.optString("id", ""),
            title = o.optString("title", ""),
            artist = o.optString("artist", ""),
            album = o.optString("album", ""),
            durationMs = o.optLong("duration", 0L),
            coverUrl = o.optString("cover", "").ifBlank { null }
        )
    }

    private fun parseSongArray(arr: JSONArray?): List<RemoteSong> {
        if (arr == null) return emptyList()
        return try {
            (0 until arr.length()).mapNotNull { i -> parseSong(arr.optJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 解析 LRC 文本为带时间戳的行；无 `[mm:ss]` 时间戳时返回空列表。 */
    private fun parseLrcLines(lrc: String): List<RemoteLyricLine> {
        if (lrc.isBlank()) return emptyList()
        val regex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?](.*)""")
        return lrc.lineSequence().mapNotNull { line ->
            val m = regex.find(line.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val sec = m.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val frac = m.groupValues[3].ifBlank { "0" }
            val ms = frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            RemoteLyricLine(
                timeMs = min * 60_000L + sec * 1000L + ms,
                text = m.groupValues[4]
            )
        }.toList()
    }
}
