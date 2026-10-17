package com.asmrhelper.domain.music

import androidx.annotation.StringRes
import com.asmrhelper.R

/**
 * 一个在线音乐平台（原生实现）。不再有「音源地址」概念——每个平台由
 * 对应的原生 `MusicSource`（如 `NeteaseMusicSource`）直接请求其官方接口。
 */
data class SourceConfig(
    val id: String,
    @StringRes val displayNameRes: Int
)

/** 内置平台预设（原生实现：网易云；QQ 二期待做）。 */
object SourcePresets {
    val NETEASE = SourceConfig("netease", R.string.source_netease)
    val QQ = SourceConfig("qq", R.string.source_qq)

    val ALL = listOf(NETEASE, QQ)
}
