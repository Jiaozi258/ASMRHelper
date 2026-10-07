package com.asmrhelper.util

/** 单条歌词行：timeMs 为该行出现的时间（毫秒），text 为歌词文本。 */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * LRC 歌词解析器。支持主流音乐软件（网易云 / QQ 音乐等）的歌词格式：
 * - 时间标签 [mm:ss] / [mm:ss.x] / [mm:ss.xx] / [mm:ss.xxx]（小数位 1~3 位）
 * - 一行多个时间标签（同一句歌词在多个时间点重复）
 * - 元数据标签 [ti:][ar:][al:][by:][offset:±ms]
 */
object LrcParser {

    // 时间标签：分:秒，可选小数位（1~3 位，用 . 或 : 分隔）
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LrcLine> {
        var offsetMs = 0L
        val result = mutableListOf<LrcLine>()

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // 元数据标签（[xx:...] 且不是时间标签）
            if (trimmed.startsWith("[") && !TIME_TAG.containsMatchIn(trimmed)) {
                val meta = Regex("""^\[([a-zA-Z]+):(.*)]$""").find(trimmed)
                if (meta != null) {
                    val key = meta.groupValues[1]
                    val value = meta.groupValues[2]
                    if (key.equals("offset", true)) {
                        offsetMs = value.toLongOrNull() ?: 0L
                    }
                }
                return@forEach
            }

            val tags = TIME_TAG.findAll(trimmed).toList()
            if (tags.isEmpty()) return@forEach // 无时间标签的纯文本行跳过

            // 最后一个时间标签之后的内容为歌词文本
            val text = trimmed.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach

            tags.forEach { m ->
                val min = m.groupValues[1].toInt()
                val sec = m.groupValues[2].toInt()
                val frac = m.groupValues[3]
                // 小数位语义：1 位=十分秒，2 位=百分秒，3 位=毫秒
                val ms = if (frac.isEmpty()) 0L else when (frac.length) {
                    1 -> frac.toLong() * 100L
                    2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                val time = (min * 60_000L + sec * 1000L + ms + offsetMs).coerceAtLeast(0L)
                result.add(LrcLine(time, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }
}
