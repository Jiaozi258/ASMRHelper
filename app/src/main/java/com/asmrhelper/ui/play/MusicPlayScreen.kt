package com.asmrhelper.ui.play

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.R
import com.asmrhelper.domain.model.PlaybackMode
import com.asmrhelper.domain.model.isMenuItemVisible
import com.asmrhelper.ui.components.AsmrDropdownMenu
import com.asmrhelper.ui.components.MenuItem
import com.asmrhelper.ui.components.ModeToggleButton
import com.asmrhelper.ui.play.folia.CenteredLyrics
import com.asmrhelper.ui.play.folia.CoverDuotoneBackground
import com.asmrhelper.ui.play.folia.FloatingControls
import com.asmrhelper.ui.play.folia.MusicVisualizer
import com.asmrhelper.ui.play.folia.VisualizerStyle
import com.asmrhelper.ui.playlist.PlaylistViewModel
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.DynamicIsland
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.ui.theme.appleClickable
import com.asmrhelper.util.LyricMode
import kotlinx.coroutines.delay

/**
 * MUSIC 模式 Folia 风格播放页。
 *
 * 布局：全屏双色调封面背景 → 顶部灵动岛 + 左上角淡出式模式按钮 + 右上角导航菜单
 * → 音频可视化 → 歌名/艺术家 → 居中歌词 → 底部悬浮控制条。
 * 音效器/切片/工具箱/歌单/定时/歌词编辑对话框自 `PlayScreenV2` 原样迁入。
 */
@Composable
fun MusicPlayScreen(
    onNavigateToPlaylist: () -> Unit = {},
    onNavigateToLibrary: (Int) -> Unit = {},
    onNavigateToBackground: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSlideshow: () -> Unit = {},
    onNavigateToTriggerPad: () -> Unit = {},
    onNavigateToSleepJournal: () -> Unit = {},
    onToggleMode: () -> Unit = {}
) {
    val viewModel: PlayViewModel = hiltViewModel()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val rawLyricsText by viewModel.rawLyricsText.collectAsStateWithLifecycle()
    val waveform by viewModel.waveformBytes.collectAsStateWithLifecycle()
    val fft by viewModel.fftMagnitudes.collectAsStateWithLifecycle()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showEffectsDialog by remember { mutableStateOf(false) }
    var showSliceDialog by remember { mutableStateOf(false) }
    var showToolboxDialog by remember { mutableStateOf(false) }
    var showBinauralDialog by remember { mutableStateOf(false) }
    var showNoiseDialog by remember { mutableStateOf(false) }
    var showAmbientDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }

    var visualizerStyle by remember { mutableStateOf(VisualizerStyle.BAR) }
    var lyricMode by remember { mutableStateOf(LyricMode.JUMP) }

    // 模式按钮 2 秒无交互淡出；任意触摸递增 tick 重置计时（Review Focus #6）。
    var interactionTick by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(interactionTick) {
        controlsVisible = true
        delay(2000)
        controlsVisible = false
    }
    val modeButtonAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "modeButtonAlpha"
    )

    val coverUrl = state.playerState.currentAudio?.coverUrl
    val progressMs = state.playerState.progressMs
    val durationMs = state.playerState.durationMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { interactionTick++ } }
    ) {
        CoverDuotoneBackground(coverUrl, Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // 顶部留白给灵动岛 + 模式按钮
            Spacer(Modifier.height(40.dp))

            // 音频可视化（点击循环切换 6 种风格，切换交叉淡入）
            Crossfade(targetState = visualizerStyle, label = "visualizer_crossfade") { style ->
                MusicVisualizer(
                    waveform = waveform,
                    fft = fft,
                    style = style,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .appleClickable { visualizerStyle = visualizerStyle.next() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 歌名 / 艺术家
            Text(
                text = state.displayTitle.ifEmpty { "未在播放" },
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            state.playerState.currentAudio?.artist?.let { artist ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // 居中歌词
            CenteredLyrics(
                lines = lyrics,
                progressMs = progressMs,
                mode = lyricMode,
                onToggleMode = {
                    lyricMode = if (lyricMode == LyricMode.JUMP) LyricMode.SCROLL else LyricMode.JUMP
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 底部悬浮控制条
            FloatingControls(
                isPlaying = state.playerState.isPlaying,
                progress = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f,
                durationMs = durationMs,
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.previous() },
                onNext = { viewModel.next() },
                onSeek = { viewModel.seekTo(it) },
                onMore = { showMoreDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }

        // 灵动岛「正在播放」胶囊
        DynamicIsland(
            title = state.displayTitle.ifEmpty { null },
            isPlaying = state.playerState.isPlaying,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
        )

        // 左上角模式切换按钮（2 秒淡出，触摸恢复）
        ModeToggleButton(
            mode = PlaybackMode.MUSIC,
            onToggle = { if (controlsVisible) onToggleMode() else interactionTick++ },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 6.dp, start = 16.dp)
                .alpha(modeButtonAlpha)
        )

        // 右上角导航菜单
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 4.dp, end = 16.dp)
        ) {
            AsmrDropdownMenu(
                items = listOf(
                    MenuItem("slideshow", "图片"),
                    MenuItem("favorites", "我的收藏"),
                    MenuItem("scan", "本地文件扫描"),
                    MenuItem("file_manager", "文件管理器"),
                    MenuItem("background", "背景图库"),
                    MenuItem("trigger_pad", "触发器面板"),
                    MenuItem("sleep_journal", "睡眠记录"),
                    MenuItem("video_audio", "视频音频"),
                    MenuItem("settings", "设置")
                ).filter { item -> PlaybackMode.MUSIC.isMenuItemVisible(item.id) },
                onItemClick = { item ->
                    when (item.id) {
                        "slideshow" -> onNavigateToSlideshow()
                        "favorites" -> onNavigateToLibrary(1)
                        "scan" -> onNavigateToLibrary(0)
                        "file_manager" -> onNavigateToLibrary(2)
                        "trigger_pad" -> onNavigateToTriggerPad()
                        "background" -> onNavigateToBackground()
                        "sleep_journal" -> onNavigateToSleepJournal()
                        "video_audio" -> onNavigateToLibrary(3)
                        "settings" -> onNavigateToSettings()
                    }
                }
            )
        }
    }

    // ── 更多菜单 ───────────────────────────────────────
    if (showMoreDialog) {
        AlertDialog(
            onDismissRequest = { showMoreDialog = false },
            title = { Text(stringResource(R.string.more), color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MoreRow(
                        label = stringResource(R.string.visualizer_style),
                        value = visualizerStyleLabel(visualizerStyle),
                        onClick = { visualizerStyle = visualizerStyle.next() }
                    )
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                    MoreRow(
                        label = stringResource(R.string.lyric_mode),
                        value = if (lyricMode == LyricMode.JUMP)
                            stringResource(R.string.lyric_mode_jump)
                        else stringResource(R.string.lyric_mode_scroll),
                        onClick = {
                            lyricMode = if (lyricMode == LyricMode.JUMP)
                                LyricMode.SCROLL else LyricMode.JUMP
                        }
                    )
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                    MoreRow(stringResource(R.string.menu_timer), "") { showMoreDialog = false; showTimerDialog = true }
                    MoreRow(stringResource(R.string.menu_lyrics_edit), "") { showMoreDialog = false; showLyricsDialog = true }
                    MoreRow(stringResource(R.string.menu_playlist), "") { showMoreDialog = false; showPlaylistDialog = true }
                    MoreRow(stringResource(R.string.menu_toolbox), "") { showMoreDialog = false; showToolboxDialog = true }
                    MoreRow(stringResource(R.string.menu_effects), "") { showMoreDialog = false; showEffectsDialog = true }
                    MoreRow(stringResource(R.string.menu_slice), "") { showMoreDialog = false; showSliceDialog = true }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoreDialog = false }) { Text("关闭", color = TextSecondary) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 定时对话框 ─────────────────────────────────────
    if (showTimerDialog) {
        var minutes by remember { mutableStateOf(30) }
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("睡眠定时", color = TextPrimary) },
            text = {
                Column {
                    Text("定时 ${minutes} 分钟后停止播放", color = TextSecondary, fontSize = 14.sp)
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = it.toInt() },
                        valueRange = 5f..120f,
                        steps = 22,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setTimerSeconds(minutes * 60); showTimerDialog = false }) {
                    Text("开始", color = LocalAccentColor.current)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelTimer(); showTimerDialog = false }) {
                    Text("取消定时", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 歌词编辑浮层 ───────────────────────────────────
    if (showLyricsDialog) {
        var lyricText by remember(showLyricsDialog) { mutableStateOf(rawLyricsText) }
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text("歌词", color = TextPrimary) },
            text = {
                Column {
                    Text("每行格式：[mm:ss.xx] 歌词内容", color = TextHint, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = lyricText,
                        onValueChange = { lyricText = it },
                        placeholder = { Text("例如：\n[00:00.00] 第一句歌词\n[00:12.34] 第二句歌词", color = TextHint) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveLyrics(lyricText)
                        showLyricsDialog = false
                    }
                ) {
                    Text("保存歌词", color = LocalAccentColor.current)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("关闭", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 添加到播放列表对话框 ───────────────────────────
    if (showPlaylistDialog) {
        val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
        var showCreateDialog by remember { mutableStateOf(false) }
        val currentAudio = state.playerState.currentAudio

        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("添加到播放列表", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("➕ 新建播放列表", color = LocalAccentColor.current, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f))
                    if (playlists.isEmpty()) {
                        Text("暂无播放列表", color = TextHint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentAudio?.let { playlistViewModel.addAudioToPlaylist(pl.id, it.id) }
                                    showPlaylistDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pl.name, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("关闭", color = TextSecondary) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )

        if (showCreateDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("新建播放列表", color = TextPrimary) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("播放列表名称", color = TextHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                playlistViewModel.createPlaylist(name)
                                showCreateDialog = false
                            }
                        },
                        enabled = name.isNotBlank()
                    ) { Text("创建", color = LocalAccentColor.current) }
                },
                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消", color = TextSecondary) } },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    // ── 音效器对话框 ───────────────────────────────────
    if (showEffectsDialog) {
        val eqLevels by viewModel.eqBandLevels.collectAsStateWithLifecycle()
        val currentScene by viewModel.currentSceneEffect.collectAsStateWithLifecycle()
        val loudness by viewModel.loudnessGain.collectAsStateWithLifecycle()
        val balance by viewModel.stereoBalance.collectAsStateWithLifecycle()
        val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
        val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
        val thresholdMode by viewModel.volumeThresholdMode.collectAsStateWithLifecycle()
        val minThresholdDb by viewModel.minThresholdDb.collectAsStateWithLifecycle()
        val maxThresholdDb by viewModel.maxThresholdDb.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = { showEffectsDialog = false },
            title = { Text("音效器", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .height(420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("场景效果", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.asmrhelper.player.SceneEffect.entries.forEach { scene ->
                            val selected = currentScene == scene
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .clickable { viewModel.applySceneEffect(scene) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(scene.label, color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("音量阈值", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("响度模式", "阈值模式").forEachIndexed { i, label ->
                            val selected = thresholdMode == i
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .clickable { viewModel.setVolumeThresholdMode(i) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                    if (thresholdMode == 0) {
                        Text("响度 ${if (loudness == 0) "关闭" else "+${loudness / 100}.${(loudness % 100) / 10}dB"}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Slider(
                            value = loudness.toFloat(),
                            onValueChange = { viewModel.setLoudnessGain(it.toInt()) },
                            valueRange = 0f..1000f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                        )
                    } else {
                        Text("最大阈值：${maxThresholdDb} dB（限制过响部分）", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Slider(
                            value = maxThresholdDb.toFloat(),
                            onValueChange = { viewModel.setMaxThresholdDb(it.toInt()) },
                            valueRange = -40f..0f,
                            steps = 39,
                            colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                        )
                        Text("最小阈值：${minThresholdDb} dB（抑制过弱噪声）", color = TextSecondary, fontSize = 13.sp)
                        Slider(
                            value = minThresholdDb.toFloat(),
                            onValueChange = { viewModel.setMinThresholdDb(it.toInt()) },
                            valueRange = -60f..0f,
                            steps = 59,
                            colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("变速 ${"%.2f".format(playbackSpeed)}x", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = playbackSpeed,
                        onValueChange = { viewModel.setPlaybackSpeed(it) },
                        valueRange = 0.5f..2f,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                    Text("变调 ${"%.2f".format(playbackPitch)}x", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = playbackPitch,
                        onValueChange = { viewModel.setPlaybackPitch(it) },
                        valueRange = 0.5f..2f,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("左右平衡 ${when { balance < -0.05f -> "偏左"; balance > 0.05f -> "偏右"; else -> "居中" }}", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = balance,
                        onValueChange = { viewModel.setStereoBalance(it) },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("均衡器（更多频段可在设置中调节）", color = TextHint, fontSize = 12.sp)
                    val bandLabels = listOf("低音", "中音", "高音")
                    eqLevels.take(3).forEachIndexed { i, level ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(bandLabels.getOrElse(i) { "频段$i" }, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = level,
                                onValueChange = { viewModel.setEqBand(i, it) },
                                valueRange = -10f..10f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEffectsDialog = false }) { Text("完成", color = LocalAccentColor.current) }
            },
            dismissButton = {},
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 切片对话框 ─────────────────────────────────────
    if (showSliceDialog) {
        val duration = state.playerState.durationMs
        var startMs by remember(duration) { mutableStateOf(0f) }
        var endMs by remember(duration) { mutableStateOf(duration.toFloat()) }
        AlertDialog(
            onDismissRequest = { showSliceDialog = false },
            title = { Text("切片播放（A-B 循环）", color = TextPrimary) },
            text = {
                Column {
                    Text("起点 A: ${formatTime(startMs.toLong())}", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = startMs,
                        onValueChange = { startMs = it.coerceAtMost(endMs) },
                        valueRange = 0f..(duration.takeIf { it > 0 } ?: 1L).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                    Text("终点 B: ${formatTime(endMs.toLong())}", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = endMs,
                        onValueChange = { endMs = it.coerceAtLeast(startMs) },
                        valueRange = 0f..(duration.takeIf { it > 0 } ?: 1L).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setSlice(startMs.toLong(), endMs.toLong()); showSliceDialog = false }) {
                    Text("开始切片", color = LocalAccentColor.current)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearSlice(); showSliceDialog = false }) {
                    Text("清除", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 工具箱对话框 ───────────────────────────────────
    if (showToolboxDialog) {
        AlertDialog(
            onDismissRequest = { showToolboxDialog = false },
            title = { Text("工具箱", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val noiseLabel = if (state.noiseActive) {
                        when (state.noiseType) { "PINK" -> "🌸 粉噪"; "BROWN" -> "🍂 棕噪"; else -> "🔊 白噪" }
                    } else "🔇 噪音生成"
                    val spatialLabel = when (state.spatialMode) {
                        "D3" -> "🎧 3D"; "SWEEP" -> "↔ 扫掠"; "CIRCLE" -> "🔄 环绕"; "WIDE" -> "🌐 扩展"
                        else -> "🎧 3D 空间音效"
                    }
                    val binauralLabel = state.binauralPreset?.name
                        ?.let { "🧠 双耳节拍（$it）" } ?: "🧠 双耳节拍"

                    val tools = listOf(
                        Triple("🌧 环境音", state.playerState.isBackgroundPlaying) { showAmbientDialog = true },
                        Triple(binauralLabel, state.binauralActive) { showBinauralDialog = true },
                        Triple(noiseLabel, state.noiseActive) { showNoiseDialog = true },
                        Triple(spatialLabel, state.spatialMode != "OFF") { viewModel.cycleSpatialMode() },
                        Triple("📳 触觉反馈", state.hapticEnabled) { viewModel.toggleHaptic() },
                        Triple("🌙 淡出", false) { showToolboxDialog = false; viewModel.fadeOut(5000L) },
                        Triple("⏹ 彻底停止", false) { showToolboxDialog = false; viewModel.stopPlayback() }
                    )
                    tools.forEach { (label, active, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appleClickable { action() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (active) "▶ $label" else label,
                                color = if (active) LocalAccentColor.current else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (active) {
                                Text("✓", color = LocalAccentColor.current, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showToolboxDialog = false }) { Text("关闭", color = TextSecondary) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 双耳节拍选择对话框 ─────────────────────────────
    if (showBinauralDialog) {
        AlertDialog(
            onDismissRequest = { showBinauralDialog = false },
            title = { Text("双耳节拍", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.asmrhelper.player.BinauralPreset.PRESETS.groupBy { it.category }.forEach { (category, presets) ->
                        Text(category, color = TextHint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        presets.forEach { preset ->
                            val isActive = state.binauralActive && state.binauralPreset == preset
                            TextButton(
                                onClick = { viewModel.toggleBinaural(preset) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isActive) "▶ ${preset.name}" else "${preset.name}  (${preset.description})",
                                    color = if (isActive) LocalAccentColor.current else TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    if (state.binauralActive) {
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("音量", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.binauralVolume,
                                onValueChange = { viewModel.setBinauralVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                            )
                            Text("${(state.binauralVolume * 100).toInt()}%", color = TextHint, fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { viewModel.stopBinaural(); showBinauralDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止节拍", color = ErrorRed, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBinauralDialog = false }) { Text("关闭", color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 噪音生成选择对话框 ─────────────────────────────
    if (showNoiseDialog) {
        AlertDialog(
            onDismissRequest = { showNoiseDialog = false },
            title = { Text("噪音生成", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "WHITE" to "白噪音 — 均匀频率，助专注",
                        "PINK" to "粉红噪音 — 低频更强，助睡眠",
                        "BROWN" to "棕色噪音 — 更深沉，助放松"
                    ).forEach { (type, desc) ->
                        val isActive = state.noiseActive && state.noiseType == type
                        TextButton(
                            onClick = { viewModel.toggleNoise(com.asmrhelper.player.NoiseType.valueOf(type)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isActive) "▶ $desc" else desc,
                                color = if (isActive) LocalAccentColor.current else TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    if (state.noiseActive) {
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("音量", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.noiseVolume,
                                onValueChange = { viewModel.setNoiseVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                            )
                            Text("${(state.noiseVolume * 100).toInt()}%", color = TextHint, fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { viewModel.stopNoise(); showNoiseDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止噪音", color = ErrorRed, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNoiseDialog = false }) { Text("关闭", color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── 环境音选择对话框 ───────────────────────────────
    if (showAmbientDialog) {
        AlertDialog(
            onDismissRequest = { showAmbientDialog = false },
            title = { Text("环境音选择", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("内置音效", color = TextHint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                    com.asmrhelper.domain.model.AmbientSource.BUILT_IN.forEach { source ->
                        val isBuiltInSelected = state.selectedAmbientPath == source.sourcePath
                        val isBuiltInPlaying = state.playerState.isBackgroundPlaying && isBuiltInSelected
                        TextButton(
                            onClick = { viewModel.playBuiltInAmbient(source); showAmbientDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(source.label, color = if (isBuiltInSelected) LocalAccentColor.current else TextSecondary, fontSize = 14.sp)
                                Text(
                                    text = when { isBuiltInPlaying -> "▶ 播放中"; isBuiltInSelected -> "已选"; else -> "" },
                                    color = if (isBuiltInPlaying) LocalAccentColor.current else TextHint,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    Text("导入的环境音", color = TextHint, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))

                    if (state.ambientAudios.isEmpty()) {
                        Text("暂无环境音，请在设置中导入", color = TextHint, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    state.ambientAudios.forEach { path ->
                        val name = path.substringAfterLast('/')
                        val isSelected = state.selectedAmbientPath == path
                        val isPlaying = state.playerState.isBackgroundPlaying && isSelected
                        TextButton(
                            onClick = {
                                if (isSelected && isPlaying) {
                                    viewModel.toggleBackground()
                                } else {
                                    viewModel.selectAmbientAudio(path)
                                }
                                showAmbientDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, color = if (isSelected) LocalAccentColor.current else TextSecondary, fontSize = 14.sp)
                                Text(
                                    text = when { isPlaying -> "▶ 播放中"; isSelected -> "已选"; else -> "" },
                                    color = if (isPlaying) LocalAccentColor.current else TextHint,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("循环播放", color = TextSecondary, fontSize = 14.sp)
                            Text("开启后环境音将反复播放", color = TextHint, fontSize = 11.sp)
                        }
                        Switch(
                            checked = state.playerState.ambientLoopEnabled,
                            onCheckedChange = { viewModel.toggleAmbientLoop() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LocalAccentColor.current,
                                checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f)
                            )
                        )
                    }

                    if (state.playerState.isBackgroundPlaying) {
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(
                            onClick = { viewModel.toggleBackground(); showAmbientDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止环境音", color = ErrorRed, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAmbientDialog = false }) { Text("关闭", color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/** 「更多」菜单里的一行：左侧 label、右侧当前值，点击执行 [onClick]。 */
@Composable
private fun MoreRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, color = LocalAccentColor.current, fontSize = 13.sp)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

/** 可视化风格循环到下一个。 */
private fun VisualizerStyle.next(): VisualizerStyle =
    VisualizerStyle.entries[(ordinal + 1) % VisualizerStyle.entries.size]

@Composable
private fun visualizerStyleLabel(style: VisualizerStyle): String = when (style) {
    VisualizerStyle.BAR -> stringResource(R.string.visualizer_bar)
    VisualizerStyle.WAVE -> stringResource(R.string.visualizer_wave)
    VisualizerStyle.DOTS -> stringResource(R.string.visualizer_dots)
    VisualizerStyle.RADIAL -> stringResource(R.string.visualizer_radial)
    VisualizerStyle.MIRROR -> stringResource(R.string.visualizer_mirror)
    VisualizerStyle.GLOW -> stringResource(R.string.visualizer_glow)
}
