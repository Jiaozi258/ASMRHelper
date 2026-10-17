package com.asmrhelper.data.music.netease

import com.asmrhelper.domain.model.music.RemoteSong
import org.json.JSONObject

/** 网易云接口响应的纯解析（org.json，异常一律返回空/空串，不抛）。 */
object NeteaseJson {

    fun parseSearch(json: String): List<RemoteSong> = try {
        val songs = JSONObject(json)
            .optJSONObject("result")
            ?.optJSONArray("songs") ?: return emptyList()
        val list = mutableListOf<RemoteSong>()
        for (i in 0 until songs.length()) {
            val s = songs.optJSONObject(i) ?: continue
            val artists = s.optJSONArray("ar")?.let { ar ->
                val names = mutableListOf<String>()
                for (j in 0 until ar.length()) {
                    ar.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
                }
                names.joinToString(" / ")
            } ?: ""
            val al = s.optJSONObject("al")
            list.add(
                RemoteSong(
                    id = s.optLong("id").toString(),
                    title = s.optString("name"),
                    artist = artists,
                    album = al?.optString("name") ?: "",
                    durationMs = s.optLong("dt"),
                    coverUrl = al?.optString("picUrl")?.takeIf { it.isNotBlank() },
                    sourceId = "netease"
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}
