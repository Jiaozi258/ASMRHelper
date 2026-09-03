package com.asmrhelper.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.ui.components.AsmrDropdownMenu
import com.asmrhelper.ui.components.MenuItem
import com.asmrhelper.ui.components.PlayPauseButton
import com.asmrhelper.ui.playlist.PlaylistViewModel
import com.asmrhelper.ui.settings.SettingsViewModel
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

/**
 * 第二种播放状态（新版沉浸式播放界面）。
 *
 * 布局自上而下：
 * 1. 顶部栏：定时 + 播放列表 + 三点菜单
 * 2. 歌词区域（占上三分之一）：默认"暂无歌词"，点击弹出添加歌词浮层
 * 3. 音频名称
 * 4. 进度条（点状滑块）+ 当前时长 / 总时长
 * 5. 功能图标栏：收藏 / 添加到播放列表 / 工具箱 / 音效器 / 切片
 * 6. 底部控制栏：播放顺序 / 上一曲 / 播放暂停 / 下一曲 / 快进15s
 */
@Composable
fun PlayScreenV2(
    onNavigateToPlaylist: () -> Unit = {},
    onNavigateToLibrary: (Int) -> Unit = {},
    onNavigateToBackground: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTriggerPad: () -> Unit = {},
    onNavigateToSleepJournal: () -> Unit = {}
) {
    val viewModel: PlayViewModel = hiltViewModel()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorite by viewModel.currentFavorite.collectAsStateWithLifecycle()

    var showTimerDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showEffectsDialog by remember { mutableStateOf(false) }
    var showSliceDialog by remember { mutableStateOf(false) }
    var showToolboxDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsViewModel.bgColorOptions.getOrElse(state.backgroundColorIndex) { SettingsViewModel.bgColorOptions[0] }.second)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // ── 1. 顶部栏 ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timerActive = state.timerActive
                val timerLabel = if (timerActive) {
                    val min = state.timerRemainingMs / 60000
                    val sec = (state.timerRemainingMs % 60000) / 1000
                    "⏱ %02d:%02d".format(min, sec)
                } else "⏱ 定时"
                TextButton(onClick = { showTimerDialog = true }) {
                    Text(timerLabel, color = if (timerActive) LocalAccentColor.current else TextSecondary, fontSize = 13.sp)
                }
                TextButton(onClick = { onNavigateToPlaylist() }) {
                    Text("📃 播放列表", color = TextSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    AsmrDropdownMenu(
                        items = listOf(
                            MenuItem("favorites", "我的收藏"),
                            MenuItem("scan", "本地文件扫描"),
                            MenuItem("file_manager", "文件管理器"),
                            MenuItem("background", "背景图库"),
                            MenuItem("trigger_pad", "触发器面板"),
                            MenuItem("sleep_journal", "睡眠记录"),
                            MenuItem("video_audio", "视频音频")
                        ),
                        onItemClick = { item ->
                            when (item.id) {
                                "favorites" -> onNavigateToLibrary(1)
                                "scan" -> onNavigateToLibrary(0)
                                "file_manager" -> onNavigateToLibrary(2)
                                "trigger_pad" -> onNavigateToTriggerPad()
                                "background" -> onNavigateToBackground()
                                "sleep_journal" -> onNavigateToSleepJournal()
                                "video_audio" -> onNavigateToLibrary(3)
                            }
                        }
                    )
                }
            }

            // ── 2. 歌词区域（上三分之一） ─────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { showLyricsDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无歌词",
                        color = TextHint,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点击添加歌词",
                        color = TextHint.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            // ── 3. 音频名称 ───────────────────────────────
            Text(
                text = state.displayTitle.ifEmpty { "未在播放" },
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            state.playerState.currentAudio?.artist?.let { artist ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = artist,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 4. 进度条 + 时间 ──────────────────────────
            val progress = state.playerState.progressMs
            val duration = state.playerState.durationMs
            var isDragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableStateOf(0f) }
            Slider(
                value = if (isDragging) dragValue
                else if (duration > 0) progress.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = { isDragging = true; dragValue = it },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo(dragValue.toLong())
                },
                valueRange = 0f..(duration.takeIf { it > 0 } ?: 1L).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = LocalAccentColor.current,
                    activeTrackColor = LocalAccentColor.current,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(progress), color = TextHint, fontSize = 12.sp)
                Text(formatTime(duration), color = TextHint, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            // ── 5. 功能图标栏 ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 收藏
                FunctionIcon(
                    icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = "收藏",
                    tint = if (favorite) ErrorRed else TextSecondary,
                    onClick = { viewModel.toggleFavorite() }
                )
                // 添加到播放列表
                FunctionIcon(
                    icon = Icons.Filled.PlaylistAdd,
                    label = "歌单",
                    onClick = { showPlaylistDialog = true }
                )
                // 工具箱（收纳九个小功能）
                FunctionIcon(
                    icon = Icons.Filled.Handyman,
                    label = "工具箱",
                    onClick = { showToolboxDialog = true }
                )
                // 音效器
                FunctionIcon(
                    icon = Icons.Filled.Tune,
                    label = "音效",
                    onClick = { showEffectsDialog = true }
                )
                // 切片
                FunctionIcon(
                    icon = Icons.Filled.ContentCut,
                    label = "切片",
                    onClick = { showSliceDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 6. 底部控制栏 ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 播放顺序
                val loopIcon = when (state.playerState.loopMode) {
                    LoopMode.NONE -> Icons.Filled.Repeat
                    LoopMode.SINGLE -> Icons.Filled.RepeatOne
                    LoopMode.LIST -> Icons.Filled.Repeat
                    LoopMode.SHUFFLE -> Icons.Filled.Shuffle
                }
                IconButton(onClick = { viewModel.cycleLoopMode() }) {
                    Icon(
                        loopIcon,
                        contentDescription = "播放顺序",
                        tint = if (state.playerState.loopMode != LoopMode.NONE) LocalAccentColor.current else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 上一曲
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, "上一曲", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }
                // 播放/暂停
                PlayPauseButton(
                    isPlaying = state.playerState.isPlaying,
                    onClick = { viewModel.togglePlayPause() }
                )
                // 下一曲
                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Filled.SkipNext, "下一曲", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }
                // 快进15s
                IconButton(onClick = { viewModel.forward15s() }) {
                    Icon(Icons.Filled.Forward30, "快进15秒", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
            }
        }
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

    // ── 歌词浮层 ───────────────────────────────────────
    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text("歌词", color = TextPrimary) },
            text = {
                Column {
                    Text("当前音频暂无歌词", color = TextHint, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("可在设置 → 歌词设置中调整字体大小、阴影、行间距等", color = TextHint.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("添加歌词", color = LocalAccentColor.current)
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
        // 用真实状态而非本地 remember(1f)：否则重开对话框会复位到 1.0x
        val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
        val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = { showEffectsDialog = false },
            title = { Text("音效器", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .height(420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 场景效果
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

                    // 响度（音量阈值 - 响度模式）
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("响度 ${if (loudness == 0) "关闭" else "+${loudness / 100}.${(loudness % 100) / 10}dB"}", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = loudness.toFloat(),
                        onValueChange = { viewModel.setLoudnessGain(it.toInt()) },
                        valueRange = 0f..1000f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )

                    // 变速变调
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

                    // 立体声左右平衡
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    Text("左右平衡 ${when { balance < -0.05f -> "偏左"; balance > 0.05f -> "偏右"; else -> "居中" }}", color = TextSecondary, fontSize = 13.sp)
                    Slider(
                        value = balance,
                        onValueChange = { viewModel.setStereoBalance(it) },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )

                    // 均衡器
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
        // 用 duration 作为 key，避免在未加载（duration=0）或切歌时滑杆卡在 0
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
                    val tools = listOf(
                        "🌧 环境音" to { viewModel.toggleBackground() },
                        "🧠 双耳节拍" to { viewModel.toggleBinaural(com.asmrhelper.player.BinauralPreset.PRESETS.first()) },
                        "🔇 噪音生成" to { viewModel.toggleNoise() },
                        "🎧 3D 空间音效" to { viewModel.cycleSpatialMode() },
                        "📳 触觉反馈" to { viewModel.toggleHaptic() },
                        "🌙 淡出" to { viewModel.fadeOut(5000L) }
                    )
                    tools.forEach { (label, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showToolboxDialog = false; action() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = TextPrimary, fontSize = 14.sp)
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
}

// ── 工具箱对话框（收纳九个小功能） ────────────────────
@Composable
private fun FunctionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = TextSecondary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
