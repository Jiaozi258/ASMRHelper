package com.asmrhelper.data.music

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.util.LrcLine
import com.asmrhelper.util.WordLyricLine
import com.asmrhelper.util.YrcParser

/** 在线歌词 → 显示用 LrcLine（按时间排序，与本地 LRC 路径对齐）。 */
fun RemoteLyric.toLrcLines(): List<LrcLine> =
    lines.map { LrcLine(it.timeMs, it.text) }.sortedBy { it.timeMs }

/** 在线歌词 → 逐词展示模型：有 yrc 逐词时间戳优先，否则退化为按行均分（words 留空）。 */
fun RemoteLyric.toWordLyricLines(): List<WordLyricLine> {
    val wordLines = YrcParser.parse(yrc)
    if (wordLines.isNotEmpty()) return wordLines
    return lines.mapIndexed { i, l ->
        val end = lines.getOrNull(i + 1)?.timeMs ?: (l.timeMs + 5000L)
        WordLyricLine(l.timeMs, end, l.text)
    }
}

/** 是否为可拉取在线歌词的远程歌曲：有来源，且 id 为正数或带字符串平台 id（QQ 的非数字 songmid）。 */
fun Audio.isRemoteSong(): Boolean = sourceId.isNotBlank() && (id > 0L || !sourceSongId.isNullOrBlank())
