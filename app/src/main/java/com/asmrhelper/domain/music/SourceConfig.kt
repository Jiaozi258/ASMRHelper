package com.asmrhelper.domain.music

/**
 * 一个「音源地址」的配置。内置平台只是预填了默认地址（可改），自定义源由用户新增。
 *
 * 注意：默认 baseUrl 为空串占位——真实「音源地址」由用户在音源设置里填写，
 * 发现页在未配置时显示友好空态（见设计文档 §5.3 的「音源落地=音源地址」决策）。
 */
data class SourceConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val isCustom: Boolean = false
)

/** 内置平台预设（网易云 / QQ / 酷狗 / Spotify）。 */
object SourcePresets {
    val NETEASE = SourceConfig("netease", "网易云音乐", "")
    val QQ = SourceConfig("qq", "QQ音乐", "")
    val KUGO = SourceConfig("kugou", "酷狗音乐", "")
    val SPOTIFY = SourceConfig("spotify", "Spotify", "")

    val ALL = listOf(NETEASE, QQ, KUGO, SPOTIFY)
}
