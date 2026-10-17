package com.asmrhelper.data.music

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.util.LrcLine

/** 在线歌词 → 显示用 LrcLine（按时间排序，与本地 LRC 路径对齐）。 */
fun RemoteLyric.toLrcLines(): List<LrcLine> =
    lines.map { LrcLine(it.timeMs, it.text) }.sortedBy { it.timeMs }

/** 是否为可拉取在线歌词的远程歌曲：有来源，且 id 为正数或带字符串平台 id（QQ 的非数字 songmid）。 */
fun Audio.isRemoteSong(): Boolean = sourceId.isNotBlank() && (id > 0L || !sourceSongId.isNullOrBlank())
