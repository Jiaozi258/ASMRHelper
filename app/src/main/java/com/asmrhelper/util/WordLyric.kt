package com.asmrhelper.util

/** 单个词的逐词时间戳（毫秒）。 */
data class WordLyric(val startMs: Long, val endMs: Long, val text: String)

/**
 * 单行歌词（逐词展示用）：startMs/endMs 为整行起止，words 为该行的逐词切片。
 * words 为空表示无逐词时间戳，展示时按字符均分（见 [WordLyricLine.evenWords]）。
 */
data class WordLyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<WordLyric> = emptyList()
)

/** 当前进度对应的歌词行：最后一个 startMs <= progressMs 的下标，无匹配取 0。 */
fun List<WordLyricLine>.currentLineIndex(progressMs: Long): Int =
    indexOfLast { it.startMs <= progressMs }.coerceAtLeast(0)

/**
 * 在副歌词（翻译/谐音）中查找与 [reference] 时间对齐的一行文本。
 * 精确时间戳优先；否则取前后 1.5s 内最近的一行，超出则视为该句无副文本返回 null。
 */
fun List<WordLyricLine>.captionFor(reference: WordLyricLine): String? {
    if (isEmpty()) return null
    val t = reference.startMs
    firstOrNull { it.startMs == t }?.let { return it.text }
    return minByOrNull { kotlin.math.abs(it.startMs - t) }
        ?.takeIf { kotlin.math.abs(it.startMs - t) <= 1500L }
        ?.text
}

/** 当前行内唱到的词下标：最后一个 startMs <= progressMs 的下标。 */
fun List<WordLyric>.wordIndexAt(progressMs: Long): Int =
    indexOfLast { it.startMs <= progressMs }.coerceAtLeast(0)

/** 无逐词时间戳时，把整行时长按分词结果均分到每个词。 */
fun WordLyricLine.evenWords(): List<WordLyric> {
    val tokens = tokenizeWords(text)
    if (tokens.isEmpty()) return emptyList()
    val total = (endMs - startMs).coerceAtLeast(1L)
    val per = total / tokens.size
    var cursor = startMs
    return tokens.mapIndexed { i, t ->
        val s = cursor
        val e = if (i == tokens.lastIndex) endMs else s + per
        cursor = e
        WordLyric(s, e, t)
    }
}

/** 歌词分词：CJK 字符逐字成词，连续拉丁/数字/标点归为一个词，词间空白附着到前一词尾（保留英文单词间距，避免 helloworld）。 */
fun tokenizeWords(text: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    fun flush() {
        if (sb.isNotEmpty()) {
            out.add(sb.toString())
            sb.clear()
        }
    }
    for (ch in text) {
        when {
            ch.isCjk() -> {
                flush()
                out.add(ch.toString())
            }
            ch.isWhitespace() -> {
                flush()
                if (out.isNotEmpty()) out[out.lastIndex] = out.last() + ch.toString()
            }
            else -> sb.append(ch)
        }
    }
    flush()
    return out
}

/** 把本地 LRC（无逐词时间戳）转成逐词展示模型，words 留空由 [evenWords] 均分。 */
fun List<LrcLine>.toWordLyricLines(): List<WordLyricLine> =
    mapIndexed { i, l ->
        val end = getOrNull(i + 1)?.timeMs ?: (l.timeMs + 5000L)
        WordLyricLine(l.timeMs, end, l.text)
    }

private fun Char.isCjk(): Boolean {
    val c = code
    return c in 0x4E00..0x9FFF ||   // CJK 统一表意文字
        c in 0x3400..0x4DBF ||      // 扩展 A
        c in 0x3000..0x303F ||      // CJK 标点
        c in 0xFF00..0xFFEF         // 全角
}

/** 网易云 yrc 逐词歌词解析：`(start,duration)词` 反复出现，按行分组。 */
object YrcParser {
    private val WORD = Regex("""\((\d+),(\d+)\)([^()]*)""")

    fun parse(yrc: String): List<WordLyricLine> {
        if (yrc.isBlank()) return emptyList()
        val result = mutableListOf<WordLyricLine>()
        for (line in yrc.split('\n')) {
            val words = mutableListOf<WordLyric>()
            for (m in WORD.findAll(line)) {
                val start = m.groupValues[1].toLongOrNull() ?: continue
                val dur = m.groupValues[2].toLongOrNull() ?: continue
                val text = m.groupValues[3]
                if (text.isNotBlank()) words.add(WordLyric(start, start + dur.coerceAtLeast(0L), text))
            }
            if (words.isNotEmpty()) {
                result.add(
                    WordLyricLine(
                        startMs = words.first().startMs,
                        endMs = words.last().endMs,
                        text = words.joinToString("") { it.text },
                        words = words
                    )
                )
            }
        }
        return result
    }
}
