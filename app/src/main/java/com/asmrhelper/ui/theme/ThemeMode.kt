package com.asmrhelper.ui.theme

/**
 * 主题模式三态：跟随系统（默认）、浅色（白天）、深色（夜晚）。
 */
enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

/** 根据系统当前是否深色，解析出最终是否使用深色主题。 */
fun ThemeMode.resolveDark(isSystemDark: Boolean): Boolean =
    when (this) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

/**
 * 一次性迁移：把旧版布尔 `dark_theme` 映射为新三态。
 * true（老深色用户）→ DARK；false → LIGHT；从未保存过（null）→ FOLLOW_SYSTEM。
 */
fun migrateDarkThemeToThemeMode(saved: Boolean?): ThemeMode =
    when (saved) {
        true -> ThemeMode.DARK
        false -> ThemeMode.LIGHT
        null -> ThemeMode.FOLLOW_SYSTEM
    }
