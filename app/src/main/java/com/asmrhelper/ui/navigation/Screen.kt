package com.asmrhelper.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.asmrhelper.R

/**
 * 顶层三页：播放 / 发现 / 主页。
 * 底部导航已移除，改为横向滑动切换（见 AsmrNavHost）。
 */
sealed class Screen(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Play : Screen("play", R.string.nav_play, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
    data object Discover : Screen("discover", R.string.nav_discover, Icons.Filled.Explore, Icons.Outlined.Explore)
    data object Profile : Screen("profile", R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * 子页面路由 —— 仅在播放页内使用。
 * 当用户在播放页菜单选择某个功能时，导航到对应的子页面。
 * 图片（Slideshow）与设置（Settings）从底部导航迁入此处。
 */
sealed class SubScreen(val route: String) {
    data object Playlist : SubScreen("playlist")
    data object Library : SubScreen("library")
    data object BackgroundGallery : SubScreen("background_gallery")
    data object TriggerPad : SubScreen("trigger_pad")
    data object SleepJournal : SubScreen("sleep_journal")
    data object History : SubScreen("history")
    data object Slideshow : SubScreen("slideshow")
    data object Settings : SubScreen("settings")
}
