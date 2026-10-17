package com.asmrhelper.util

import com.asmrhelper.domain.model.Audio
import org.json.JSONArray
import org.json.JSONObject

/** 播放队列的 JSON 编解码（含封面/来源元数据），供 PlayerManager 持久化与恢复。 */
object PlaylistStateCodec {
    fun encode(audios: List<Audio>): String {
        val arr = JSONArray()
        audios.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("artist", a.artist)
                put("filePath", a.filePath)
                put("durationMs", a.durationMs)
                put("isFavorite", a.isFavorite)
                put("coverUrl", a.coverUrl ?: "")
                put("sourceId", a.sourceId)
                put("sourceSongId", a.sourceSongId ?: "")
            })
        }
        return arr.toString()
    }

    fun decode(json: String, unknownTitle: String = ""): List<Audio> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Audio(
                id = o.optLong("id", 0L),
                title = o.optString("title", unknownTitle),
                artist = o.optString("artist", ""),
                filePath = o.optString("filePath", ""),
                durationMs = o.optLong("durationMs", 0L),
                isFavorite = o.optBoolean("isFavorite", false),
                coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
                sourceId = o.optString("sourceId", ""),
                sourceSongId = o.optString("sourceSongId", "").takeIf { it.isNotBlank() }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
