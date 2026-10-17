package com.asmrhelper.util

/** 从 Set-Cookie 或 Cookie 串中挑出指定 name，返回可直接放进 `Cookie` 头的 `name=value` 串。 */
object Cookies {

    fun select(raw: String?, keep: Set<String>): String {
        if (raw.isNullOrBlank()) return ""
        val result = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2 && kv[0].isNotBlank() && kv[0] in keep) {
                result[kv[0]] = kv[1]
            }
        }
        return result.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
