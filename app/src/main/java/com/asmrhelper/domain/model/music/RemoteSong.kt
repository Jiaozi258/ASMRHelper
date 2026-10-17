package com.asmrhelper.domain.model.music

/** 在线平台歌曲，由「音源地址」返回并归一化。 */
data class RemoteSong(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val sourceId: String = ""
)

/** 单行歌词。 */
data class RemoteLyricLine(val timeMs: Long, val text: String)

/** 归一化后的歌词：带时间戳的行 + 原始文本。 */
data class RemoteLyric(
    val lines: List<RemoteLyricLine> = emptyList(),
    val rawText: String = ""
)

/** 在线歌单。 */
data class RemotePlaylist(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
    val songs: List<RemoteSong> = emptyList()
)

/** 排行榜。 */
data class RemoteChart(
    val id: String,
    val name: String,
    val songs: List<RemoteSong> = emptyList()
)

/** 扫码登录状态机。 */
sealed class QrLoginState {
    data class Waiting(val key: String, val qrContent: String) : QrLoginState()
    data class Scanned(val key: String) : QrLoginState()
    data class Confirmed(val username: String) : QrLoginState()
    data class Expired(val key: String) : QrLoginState()
    data class Failed(val message: String) : QrLoginState()
}
