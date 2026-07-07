package com.asmrhelper.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.ui.background.BackgroundGalleryScreen
import com.asmrhelper.ui.components.MiniPlayer
import com.asmrhelper.ui.library.LibraryScreen
import com.asmrhelper.ui.play.PlayScreen
import com.asmrhelper.ui.play.PlayViewModel
import com.asmrhelper.ui.playlist.PlaylistScreen
import com.asmrhelper.ui.profile.ProfileScreen
import com.asmrhelper.ui.settings.SettingsScreen
import com.asmrhelper.ui.slideshow.ImageSlideshowContent
import com.asmrhelper.ui.history.HistoryScreen
import com.asmrhelper.ui.sleep.SleepJournalScreen
import com.asmrhelper.ui.triggerpad.TriggerPadScreen
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.util.ShareReceiver
import com.asmrhelper.util.ShortcutReceiver

@Composable
fun AsmrNavHost(modifier: Modifier = Modifier) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Play) }
    var playSubScreen by remember { mutableStateOf<SubScreen?>(null) }
    var settingsSubScreen by remember { mutableStateOf<SubScreen?>(null) }
    var libraryInitialTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Observe share intents and auto-navigate to video audio tab
    val shareUrl by ShareReceiver.pendingUrl.collectAsStateWithLifecycle()
    LaunchedEffect(shareUrl) {
        if (shareUrl.isNotEmpty()) {
            libraryInitialTab = 3
            currentScreen = Screen.Play
            playSubScreen = SubScreen.Library
        }
    }

    val playViewModel: PlayViewModel = hiltViewModel()

    // Observe app shortcut actions and auto-navigate
    val shortcutAction by ShortcutReceiver.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            "resume" -> {
                currentScreen = Screen.Play
                playSubScreen = null
                val audio = playViewModel.loadLastPlayback()
                if (audio != null) {
                    playViewModel.play(audio)
                }
            }
            "timer30" -> {
                currentScreen = Screen.Play
                playSubScreen = null
                playViewModel.setTimerSeconds(30 * 60)
                val audio = playViewModel.loadLastPlayback()
                if (audio != null) {
                    playViewModel.play(audio)
                }
            }
            "favorites" -> {
                currentScreen = Screen.Play
                libraryInitialTab = 1 // "我的收藏" tab
                playSubScreen = SubScreen.Library
            }
            "history" -> {
                currentScreen = Screen.Settings
                settingsSubScreen = SubScreen.History
            }
        }
        if (shortcutAction.isNotEmpty()) {
            ShortcutReceiver.consume()
        }
    }
    val playUiState by playViewModel.uiState.collectAsStateWithLifecycle()

    val showBottomBar = !(currentScreen is Screen.Play && playSubScreen != null)
        && !(currentScreen is Screen.Settings && settingsSubScreen != null)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentScreen.route,
                    onNavigate = { screen ->
                        currentScreen = screen
                        playSubScreen = null // 切换标签时重置子页面
                    }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val screenOrder = listOf("play", "slideshow", "profile", "settings")
                val fromIdx = screenOrder.indexOf(initialState.route)
                val toIdx = screenOrder.indexOf(targetState.route)
                val direction = if (toIdx >= fromIdx) 1 else -1
                (slideInHorizontally { it * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it * direction } + fadeOut())
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.Play -> {
                    when (playSubScreen) {
                        null -> PlayScreen(
                            onNavigateToPlaylist = { playSubScreen = SubScreen.Playlist },
                            onNavigateToLibrary = { tabIndex ->
                                libraryInitialTab = tabIndex
                                playSubScreen = SubScreen.Library
                            },
                            onNavigateToBackground = { playSubScreen = SubScreen.BackgroundGallery },
                            onNavigateToSettings = { currentScreen = Screen.Settings },
                            onNavigateToTriggerPad = { playSubScreen = SubScreen.TriggerPad },
                            onNavigateToSleepJournal = { playSubScreen = SubScreen.SleepJournal }
                        )

                        SubScreen.Playlist -> {
                            val audio = playUiState.playerState.currentAudio
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PlaylistScreen(
                                        onBack = { playSubScreen = null },
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
                                        onClick = { playSubScreen = null }
                                    )
                                }
                            }
                        }

                        SubScreen.Library -> {
                            val audio = playUiState.playerState.currentAudio
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LibraryScreen(
                                        onBack = { playSubScreen = null },
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
                                        onClick = { playSubScreen = null }
                                    )
                                }
                            }
                        }

                        SubScreen.BackgroundGallery -> {
                            val audio = playUiState.playerState.currentAudio
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    BackgroundGalleryScreen(
                                        onBack = { playSubScreen = null }
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
                                        onClick = { playSubScreen = null }
                                    )
                                }
                            }
                        }

                        SubScreen.TriggerPad -> {
                            val audio = playUiState.playerState.currentAudio
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TriggerPadScreen(onBack = { playSubScreen = null })
                                }
                                if (audio != null) {
                                    MiniPlayer(
                                        title = audio.title,
                                        isPlaying = playUiState.playerState.isPlaying,
                                        progress = if (playUiState.playerState.durationMs > 0)
                                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                                        else 0f,
                                        onPlayPause = { playViewModel.togglePlayPause() },
                                        onClick = { playSubScreen = null }
                                    )
                                }
                            }
                        }

                        SubScreen.SleepJournal -> {
                            val audio = playUiState.playerState.currentAudio
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SleepJournalScreen(onBack = { playSubScreen = null })
                                }
                                if (audio != null) {
                                    MiniPlayer(
                                        title = audio.title,
                                        isPlaying = playUiState.playerState.isPlaying,
                                        progress = if (playUiState.playerState.durationMs > 0)
                                            playUiState.playerState.progressMs.toFloat() / playUiState.playerState.durationMs
                                        else 0f,
                                        onPlayPause = { playViewModel.togglePlayPause() },
                                        onClick = { playSubScreen = null }
                                    )
                                }
                            }
                        }

                        SubScreen.History -> {
                            // History is handled in Settings tab, not Play
                            PlayScreen(
                                onNavigateToPlaylist = { playSubScreen = SubScreen.Playlist },
                                onNavigateToLibrary = { tabIndex ->
                                    libraryInitialTab = tabIndex
                                    playSubScreen = SubScreen.Library
                                },
                                onNavigateToBackground = { playSubScreen = SubScreen.BackgroundGallery },
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                onNavigateToTriggerPad = { playSubScreen = SubScreen.TriggerPad },
                                onNavigateToSleepJournal = { playSubScreen = SubScreen.SleepJournal }
                            )
                        }
                    }
                }

                Screen.Profile -> ProfileScreen()
                Screen.Slideshow -> ImageSlideshowContent()
                Screen.Settings -> {
                    when (settingsSubScreen) {
                        null -> SettingsScreen(
                            onNavigateToHistory = { settingsSubScreen = SubScreen.History }
                        )
                        SubScreen.History -> HistoryScreen(
                            onBack = { settingsSubScreen = null },
                            onPlayAudio = { audio -> playViewModel.play(audio) }
                        )
                        else -> SettingsScreen(
                            onNavigateToHistory = { settingsSubScreen = SubScreen.History }
                        )
                    }
                }
            }
        }
    }
}
