package com.asmrhelper.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.asmrhelper.domain.model.PlaybackMode
import com.asmrhelper.ui.background.BackgroundGalleryScreen
import com.asmrhelper.ui.components.BatteryOptimizationDialog
import com.asmrhelper.ui.components.MiniPlayer
import com.asmrhelper.ui.components.ModeToggleButton
import com.asmrhelper.ui.discover.DiscoverScreen
import com.asmrhelper.ui.history.HistoryScreen
import com.asmrhelper.ui.library.LibraryScreen
import com.asmrhelper.ui.play.MusicPlayScreen
import com.asmrhelper.ui.play.PlayScreen
import com.asmrhelper.ui.play.PlayViewModel
import com.asmrhelper.ui.playlist.PlaylistScreen
import com.asmrhelper.ui.profile.ProfileScreen
import com.asmrhelper.ui.settings.MusicSourceSettingsScreen
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
    var settingsFromProfile by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 打开播放页子页面：统一先把 Pager 滑回播放页（第 0 页）
    fun openSubScreen(sub: SubScreen?) {
        playSubScreen = sub
        if (sub != null) scope.launch { pagerState.scrollToPage(0) }
    }

    // 从播放页菜单打开设置：退出时留在播放页
    fun openSettingsFromPlay() {
        settingsFromProfile = false
        openSubScreen(SubScreen.Settings)
    }

    // 从个人页打开设置：退出时回到个人页
    fun openSettingsFromProfile() {
        settingsFromProfile = true
        openSubScreen(SubScreen.Settings)
    }

    // 设置页退出：按来源回到播放页或个人页
    fun backFromSettings() {
        playSubScreen = null
        if (settingsFromProfile) scope.launch { pagerState.scrollToPage(2) }
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
                onSetLibraryTab = { libraryInitialTab = it },
                onOpenSettings = { openSettingsFromPlay() },
                onBackFromSettings = { backFromSettings() }
            )

            // ── 发现页 ──────────────────────────────────
            1 -> DiscoverScreen(
                modifier = Modifier.fillMaxSize(),
                onPlayRequested = { scope.launch { pagerState.scrollToPage(0) } }
            )

            // ── 主页 ────────────────────────────────────
            2 -> ProfileScreen(onNavigateToSettings = { openSettingsFromProfile() })
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
    onSetLibraryTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBackFromSettings: () -> Unit
) {
    val context = LocalContext.current
    val playbackMode by playViewModel.playbackMode.collectAsStateWithLifecycle()
    val mode = PlaybackMode.entries.getOrElse(playbackMode) { PlaybackMode.ASMR }

    when (playSubScreen) {
        null -> {
            // 沉浸式播放页始终深色（暗色壁纸 + 浅色文字），不随全局浅色/深色主题切换
            CompositionLocalProvider(LocalAppColors provides DarkAppColors) {
                val navigateToPlaylist = { onOpenSubScreen(SubScreen.Playlist) }
                val navigateToLibrary = { tabIndex: Int ->
                    onSetLibraryTab(tabIndex)
                    onOpenSubScreen(SubScreen.Library)
                }
                val navigateToBackground = { onOpenSubScreen(SubScreen.BackgroundGallery) }
                val navigateToSlideshow = { onOpenSubScreen(SubScreen.Slideshow) }
                val navigateToTriggerPad = { onOpenSubScreen(SubScreen.TriggerPad) }
                val navigateToSleepJournal = { onOpenSubScreen(SubScreen.SleepJournal) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 两套播放页淡入淡出切换，避免模式切换时突兀跳变
                    Crossfade(targetState = mode, label = "play_mode") { currentMode ->
                        if (currentMode == PlaybackMode.ASMR) {
                            // ASMR 模式：经典播放页（环境音/白噪音/催眠/双耳节拍，无歌词无工具箱）
                            PlayScreen(
                                onNavigateToPlaylist = navigateToPlaylist,
                                onNavigateToLibrary = navigateToLibrary,
                                onNavigateToBackground = navigateToBackground,
                                onNavigateToSettings = onOpenSettings,
                                onNavigateToSlideshow = navigateToSlideshow,
                                onNavigateToTriggerPad = navigateToTriggerPad,
                                onNavigateToSleepJournal = navigateToSleepJournal
                            )
                        } else {
                            // MUSIC 模式：Folia 沉浸式播放页（双色调封面/可视化/居中歌词/悬浮控制）
                            MusicPlayScreen(
                                onNavigateToPlaylist = navigateToPlaylist,
                                onNavigateToLibrary = navigateToLibrary,
                                onNavigateToBackground = navigateToBackground,
                                onNavigateToSettings = onOpenSettings,
                                onNavigateToSlideshow = navigateToSlideshow,
                                onNavigateToTriggerPad = navigateToTriggerPad,
                                onNavigateToSleepJournal = navigateToSleepJournal,
                                onToggleMode = { playViewModel.setPlaybackMode(PlaybackMode.ASMR.ordinal) }
                            )
                        }
                    }
                    // 左上角模式切换按钮：仅 ASMR 时由播放页统一叠加（常显）；
                    // MUSIC 由 MusicPlayScreen 自绘「2 秒淡出」版，不在此叠加。
                    if (mode == PlaybackMode.ASMR) {
                        ModeToggleButton(
                            mode = mode,
                            onToggle = {
                                val next = (mode.ordinal + 1) % PlaybackMode.entries.size
                                playViewModel.setPlaybackMode(next)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(top = 6.dp, start = 16.dp)
                        )
                    }
                }
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
            onBack = onBackFromSettings,
            onNavigateToHistory = { onOpenSubScreen(SubScreen.History) },
            onNavigateToMusicSources = { onOpenSubScreen(SubScreen.MusicSources) }
        )

        SubScreen.MusicSources -> MusicSourceSettingsScreen(
            onBack = { onOpenSubScreen(SubScreen.Settings) }
        )

        SubScreen.History -> HistoryScreen(
            onBack = { onOpenSubScreen(SubScreen.Settings) },
            onPlayAudio = { audio -> playViewModel.play(audio) }
        )
    }
}
