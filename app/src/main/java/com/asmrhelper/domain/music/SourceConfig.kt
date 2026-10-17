package com.asmrhelper.domain.music

/**
 * 一个在线音乐平台（原生实现）。不再有「音源地址」概念——每个平台由
 * 对应的原生 `MusicSource`（如 `NeteaseMusicSource`）直接请求其官方接口。
 */
data class SourceConfig(
    val id: String,
    val displayName: String
)

/** 内置平台预设（原生实现：网易云；QQ 二期待做）。 */
object SourcePresets {
    val NETEASE = SourceConfig("netease", "网易云音乐")
    val QQ = SourceConfig("qq", "QQ音乐")

    val ALL = listOf(NETEASE, QQ)
}
