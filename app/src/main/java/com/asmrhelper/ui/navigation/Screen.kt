package com.asmrhelper.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Play : Screen("play", "播放", Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
    data object Slideshow : Screen("slideshow", "图片", Icons.Filled.Image, Icons.Outlined.Image)
    data object Profile : Screen("profile", "主页", Icons.Filled.Person, Icons.Outlined.Person)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomNavItems by lazy { listOf(Play, Slideshow, Profile, Settings) }
    }
}

/**
 * 子页面路由 —— 仅在 Play 标签页内使用。
 * 当用户在 PlayScreen 的下拉菜单选择某个功能时，导航到对应的子页面。
 */
sealed class SubScreen(val route: String) {
    data object Playlist : SubScreen("playlist")
    data object Library : SubScreen("library")
    data object BackgroundGallery : SubScreen("background_gallery")
    data object TriggerPad : SubScreen("trigger_pad")
    data object SleepJournal : SubScreen("sleep_journal")
    data object History : SubScreen("history")
}
