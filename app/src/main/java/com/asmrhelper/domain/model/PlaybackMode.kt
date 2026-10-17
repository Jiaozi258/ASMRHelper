package com.asmrhelper.domain.model

/**
 * 播放模式：ASMR（默认，含全部 ASMR 特色功能）与 MUSIC（纯音乐，隐藏 ASMR 专属功能）。
 */
enum class PlaybackMode { ASMR, MUSIC }

/** 仅在 ASMR 模式可见的播放页菜单项（MUSIC 模式隐藏）。 */
val ASMR_ONLY_MENU_IDS = setOf("trigger_pad", "sleep_journal")

/** 给定菜单项在当前模式下是否可见。ASMR 全可见；MUSIC 隐藏 ASMR 专属项。 */
fun PlaybackMode.isMenuItemVisible(itemId: String): Boolean =
    this == PlaybackMode.ASMR || itemId !in ASMR_ONLY_MENU_IDS
