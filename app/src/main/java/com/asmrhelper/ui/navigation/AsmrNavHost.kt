package com.asmrhelper.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.asmrhelper.ui.background.BackgroundGalleryScreen
import com.asmrhelper.ui.components.BatteryOptimizationDialog
import com.asmrhelper.ui.components.MiniPlayer
import com.asmrhelper.ui.discover.DiscoverScreen
import com.asmrhelper.ui.history.HistoryScreen
import com.asmrhelper.ui.library.LibraryScreen
import com.asmrhelper.ui.play.PlayScreenV2
import com.asmrhelper.ui.play.PlayViewModel
import com.asmrhelper.ui.playlist.PlaylistScreen
import com.asmrhelper.ui.profile.ProfileScreen
import com.asmrhelper.ui.settings.SettingsScreen
import com.asmrhelper.ui.slideshow.ImageSlideshowContent
import com.asmrhelper.ui.sleep.SleepJournalScreen
import com.asmrhelper.ui.theme.DarkAppColors
import com.asmrhelper.ui.theme.LocalAppColors
import com.asmrhelper.ui.triggerpad.TriggerPadScreen
import com.asmrhelper.util.BatteryOptimizationHelper
import com.asmrhelper.util.ShareReceiver
import com.asmrhelper.util.ShortcutReceiver
import kotlinx.coroutines.launch

@Composable
fun AsmrNavHost(modifier: Modifier = Modifier) {
    var playSubScreen by remember { mutableStateOf<SubScreen?>(null) }
    var libraryInitialTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 打开播放页子页面：统一先把 Pager 滑回播放页（第 0 页）
    fun openSubScreen(sub: SubScreen?) {
        playSubScreen = sub
        if (sub != null) scope.launch { pagerState.scrollToPage(0) }
    }

    // Observe share intents and auto-navigate to video audio tab
    val shareUrl by ShareReceiver.pendingUrl.collectAsStateWithLifecycle()
    LaunchedEffect(shareUrl) {
        if (shareUrl.isNotEmpty()) {
            libraryInitialTab = 3
            openSubScreen(SubScreen.Library)
        }
    }

    val playViewModel: PlayViewModel = hiltViewModel()

    // Observe app shortcut actions and auto-navigate
    val shortcutAction by ShortcutReceiver.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            "resume" -> {
                openSubScreen(null)
                val audio = playViewModel.loadLastPlayback()
                if (audio != null) {
                    playViewModel.play(audio)
                }
            }
            "timer30" -> {
                openSubScreen(null)
                playViewModel.setTimerSeconds(30 * 60)
                val audio = playViewModel.loadLastPlayback()
                if (audio != null) {
                    playViewModel.play(audio)
                }
            }
            "favorites" -> {
                libraryInitialTab = 1 // "我的收藏" tab
                openSubScreen(SubScreen.Library)
            }
            "history" -> openSubScreen(SubScreen.History)
        }
        if (shortcutAction.isNotEmpty()) {
            ShortcutReceiver.consume()
        }
    }

    val playUiState by playViewModel.uiState.collectAsStateWithLifecycle()

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            // ── 播放页 ──────────────────────────────────
            0 -> PlayPage(
                playSubScreen = playSubScreen,
                libraryInitialTab = libraryInitialTab,
                playViewModel = playViewModel,
                playUiState = playUiState,
                onOpenSubScreen = ::openSubScreen,
                onSetLibraryTab = { libraryInitialTab = it }
            )

            // ── 发现页 ──────────────────────────────────
            1 -> DiscoverScreen(modifier = Modifier.fillMaxSize())

            // ── 主页 ────────────────────────────────────
            2 -> ProfileScreen(onNavigateToSettings = { openSubScreen(SubScreen.Settings) })
        }
    }

    // ── Battery optimization whitelist guidance ────────────
    // On Chinese ROMs, a properly implemented foreground service with
    // WakeLock is still killed ~10 min after screen-off unless the user
    // explicitly whitelists this app. Show this dialog at most a few times.
    val showBatteryDialog = remember { mutableStateOf(BatteryOptimizationHelper.shouldShowDialog(context)) }
    if (showBatteryDialog.value) {
        BatteryOptimizationDialog(onDismiss = { showBatteryDialog.value = false })
    }
}

/** 播放页内容：无子页面时显示沉浸播放页，否则显示对应子页面。 */
@Composable
private fun PlayPage(
    playSubScreen: SubScreen?,
    libraryInitialTab: Int,
    playViewModel: PlayViewModel,
    playUiState: com.asmrhelper.ui.play.PlayUiState,
    onOpenSubScreen: (SubScreen?) -> Unit,
    onSetLibraryTab: (Int) -> Unit
) {
    val context = LocalContext.current

    when (playSubScreen) {
        null -> {
            // 沉浸式播放页始终深色（暗色壁纸 + 浅色文字），不随全局浅色/深色主题切换
            CompositionLocalProvider(LocalAppColors provides DarkAppColors) {
                PlayScreenV2(
                    onNavigateToPlaylist = { onOpenSubScreen(SubScreen.Playlist) },
                    onNavigateToLibrary = { tabIndex ->
                        onSetLibraryTab(tabIndex)
                        onOpenSubScreen(SubScreen.Library)
                    },
                    onNavigateToBackground = { onOpenSubScreen(SubScreen.BackgroundGallery) },
                    onNavigateToSettings = { onOpenSubScreen(SubScreen.Settings) },
                    onNavigateToSlideshow = { onOpenSubScreen(SubScreen.Slideshow) },
                    onNavigateToTriggerPad = { onOpenSubScreen(SubScreen.TriggerPad) },
                    onNavigateToSleepJournal = { onOpenSubScreen(SubScreen.SleepJournal) }
                )
            }
        }

        SubScreen.Playlist -> {
            val audio = playUiState.playerState.currentAudio
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    PlaylistScreen(
                        onBack = { onOpenSubScreen(null) },
                        onPlayAudio = { audio -> playViewModel.play(audio) },
                        onPlayAll = { audios -> playViewModel.playPlaylist(audios) }
                    )
                }
                if (audio != null) {
                    MiniPlayer(
                        title = audio.title,
                        isPlaying = playUiState.playerState.isPlaying,
                        progress = if (playUiState.playerState.durationMs > 0)
                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                        else 0f,
                        onPlayPause = { playViewModel.togglePlayPause() },
                        onClick = { onOpenSubScreen(null) }
                    )
                }
            }
        }

        SubScreen.Library -> {
            val audio = playUiState.playerState.currentAudio
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    LibraryScreen(
                        onBack = { onOpenSubScreen(null) },
                        onPlayAudio = { a -> playViewModel.play(a) },
                        currentPlayingAudioId = audio?.id,
                        initialTabIndex = libraryInitialTab,
                        onSetBackground = { a ->
                            playViewModel.setBackgroundAudio(a.filePath)
                            Toast.makeText(context, "已设为环境音: ${a.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                if (audio != null) {
                    MiniPlayer(
                        title = audio.title,
                        isPlaying = playUiState.playerState.isPlaying,
                        progress = if (playUiState.playerState.durationMs > 0)
                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                        else 0f,
                        onPlayPause = { playViewModel.togglePlayPause() },
                        onClick = { onOpenSubScreen(null) }
                    )
                }
            }
        }

        SubScreen.BackgroundGallery -> {
            val audio = playUiState.playerState.currentAudio
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    BackgroundGalleryScreen(onBack = { onOpenSubScreen(null) })
                }
                if (audio != null) {
                    MiniPlayer(
                        title = audio.title,
                        isPlaying = playUiState.playerState.isPlaying,
                        progress = if (playUiState.playerState.durationMs > 0)
                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                        else 0f,
                        onPlayPause = { playViewModel.togglePlayPause() },
                        onClick = { onOpenSubScreen(null) }
                    )
                }
            }
        }

        SubScreen.TriggerPad -> {
            val audio = playUiState.playerState.currentAudio
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    TriggerPadScreen(onBack = { onOpenSubScreen(null) })
                }
                if (audio != null) {
                    MiniPlayer(
                        title = audio.title,
                        isPlaying = playUiState.playerState.isPlaying,
                        progress = if (playUiState.playerState.durationMs > 0)
                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                        else 0f,
                        onPlayPause = { playViewModel.togglePlayPause() },
                        onClick = { onOpenSubScreen(null) }
                    )
                }
            }
        }

        SubScreen.SleepJournal -> {
            val audio = playUiState.playerState.currentAudio
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    SleepJournalScreen(onBack = { onOpenSubScreen(null) })
                }
                if (audio != null) {
                    MiniPlayer(
                        title = audio.title,
                        isPlaying = playUiState.playerState.isPlaying,
                        progress = if (playUiState.playerState.durationMs > 0)
                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                        else 0f,
                        onPlayPause = { playViewModel.togglePlayPause() },
                        onClick = { onOpenSubScreen(null) }
                    )
                }
            }
        }

        SubScreen.Slideshow -> ImageSlideshowContent(onBack = { onOpenSubScreen(null) })

        SubScreen.Settings -> SettingsScreen(
            onBack = { onOpenSubScreen(null) },
            onNavigateToHistory = { onOpenSubScreen(SubScreen.History) }
        )

        SubScreen.History -> HistoryScreen(
            onBack = { onOpenSubScreen(SubScreen.Settings) },
            onPlayAudio = { audio -> playViewModel.play(audio) }
        )
    }
}
