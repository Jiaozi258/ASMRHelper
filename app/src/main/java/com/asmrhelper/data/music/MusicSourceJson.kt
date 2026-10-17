package com.asmrhelper.data.music

import com.asmrhelper.domain.music.SourceConfig
import org.json.JSONArray
import org.json.JSONObject

/** 音源配置列表的 JSON 序列化（存 SharedPreferences）。 */
object MusicSourceJson {

    fun sourcesToJson(list: List<SourceConfig>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("displayName", c.displayName)
                    put("baseUrl", c.baseUrl)
                    put("isCustom", c.isCustom)
                }
            )
        }
        return arr.toString()
    }

    fun sourcesFromJson(json: String): List<SourceConfig> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SourceConfig(
                    id = o.optString("id", ""),
                    displayName = o.optString("displayName", ""),
                    baseUrl = o.optString("baseUrl", ""),
                    isCustom = o.optBoolean("isCustom", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
