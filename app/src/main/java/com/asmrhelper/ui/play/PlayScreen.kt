package com.asmrhelper.ui.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.random.Random
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.player.BinauralPreset
import com.asmrhelper.ui.components.AsmrDropdownMenu
import com.asmrhelper.ui.components.HypnosisBackground
import com.asmrhelper.ui.components.HypnosisBgType
import com.asmrhelper.ui.components.MenuItem
import com.asmrhelper.ui.components.PlayPauseButton
import com.asmrhelper.ui.settings.SettingsViewModel
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

@Composable
fun PlayScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayViewModel = hiltViewModel(),
    onNavigateToPlaylist: () -> Unit = {},
    onNavigateToLibrary: (Int) -> Unit = {},
    onNavigateToBackground: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTriggerPad: () -> Unit = {},
    onNavigateToSleepJournal: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimerDialog by remember { mutableStateOf(false) }
    var breathingGuide by remember { mutableStateOf(false) }
    var showBinauralDialog by remember { mutableStateOf(false) }
    var showSceneDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showNoiseDialog by remember { mutableStateOf(false) }
    var showAmbientDialog by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsViewModel.bgColorOptions.getOrElse(state.backgroundColorIndex) { SettingsViewModel.bgColorOptions[0] }.second)
    ) {
        // Hypnosis background
        if (state.hypnosisEnabled) {
            HypnosisBackground(
                type = HypnosisBgType.fromIndex(state.hypnosisBgType),
                modifier = Modifier.fillMaxSize()
            )
        }

        val showUi = !state.hypnosisEnabled || state.hypnosisUiVisible
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(600))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDragX > 150f) {
                                    viewModel.previous()
                                } else if (totalDragX < -150f) {
                                    viewModel.next()
                                }
                                totalDragX = 0f
                            },
                            onDragCancel = { totalDragX = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragX += dragAmount
                            }
                        )
                    }
                    .pointerInput(state.hypnosisEnabled) {
                        if (state.hypnosisEnabled) {
                            detectTapGestures { viewModel.showHypnosisUi() }
                        }
                    }
            ) {
        // 背景图 + 暗色遮罩
        state.backgroundImagePath?.let { path ->
            Crossfade(targetState = path) { imagePath ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imagePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                    )
                }
            }
        }

        // 右上角下拉菜单
        AsmrDropdownMenu(
            items = listOf(
                MenuItem("playlist", "播放列表"),
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
                    "playlist" -> onNavigateToPlaylist()
                    "favorites" -> onNavigateToLibrary(1)
                    "scan" -> onNavigateToLibrary(0)
                    "file_manager" -> onNavigateToLibrary(2)
                    "trigger_pad" -> onNavigateToTriggerPad()
                    "background" -> onNavigateToBackground()
                    "sleep_journal" -> onNavigateToSleepJournal()
                    "video_audio" -> onNavigateToLibrary(3)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 音频可视化（设置中启用后显示在播放界面上方）
        val visualizerOn by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
        if (visualizerOn) {
            val waveformBytes by viewModel.waveformBytes.collectAsStateWithLifecycle()
            SoundCloudWaveform(
                waveformBytes = waveformBytes,
                isPlaying = state.playerState.isPlaying,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }

        // 音量触发特效动画
        val triggerEnabled by viewModel.volumeTriggerEnabled.collectAsStateWithLifecycle()
        if (triggerEnabled) {
            val triggerParticleCount by viewModel.triggerParticleCount.collectAsStateWithLifecycle()
            TriggerEffectOverlay(
                triggerFlow = viewModel.triggerEffect,
                particleCount = triggerParticleCount,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 中部播放控制区
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 音频标题
            AnimatedVisibility(
                visible = state.displayTitle.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.displayTitle.ifEmpty { "未在播放" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 艺术家
            state.playerState.currentAudio?.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            } ?: Spacer(modifier = Modifier.height(8.dp))

            // 循环模式 + 定时器行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 循环模式按钮
                val loopLabel = when (state.playerState.loopMode) {
                    LoopMode.NONE -> "→ 播完即止"
                    LoopMode.SINGLE -> "🔁 单曲循环"
                    LoopMode.LIST -> "🔁 列表循环"
                }
                TextButton(onClick = { viewModel.cycleLoopMode() }) {
                    Text(
                        text = loopLabel,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 定时器 / 番茄钟按钮（始终可见）
                val pomodoroActive = state.pomodoroActive
                val timerActive = state.timerActive
                TextButton(
                    onClick = {
                        if (pomodoroActive) {
                            viewModel.cancelPomodoro()
                        } else if (timerActive) {
                            viewModel.cancelTimer()
                        } else {
                            showTimerDialog = true
                        }
                    }
                ) {
                    val label = if (pomodoroActive) {
                        val min = state.pomodoroRemainingMs / 60000
                        val sec = (state.pomodoroRemainingMs % 60000) / 1000
                        val phase = if (state.pomodoroIsFocus) "🍅" else "☕"
                        "$phase %02d:%02d".format(min, sec)
                    } else if (timerActive) {
                        val min = state.timerRemainingMs / 60000
                        val sec = (state.timerRemainingMs % 60000) / 1000
                        "⏱ %02d:%02d".format(min, sec)
                    } else {
                        "⏱ 定时"
                    }
                    Text(
                        text = label,
                        color = if (pomodoroActive || timerActive) AccentPurple else TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 播放控制行：上一首 | 播放/暂停 | 下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(40.dp),
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                PlayPauseButton(
                    isPlaying = state.playerState.isPlaying,
                    onClick = { viewModel.togglePlayPause() }
                )

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(onClick = { viewModel.next() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(40.dp),
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 环境音 + 呼吸引导 + 双耳节拍
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { showAmbientDialog = true }) {
                    Text(
                        text = "🌧 环境音",
                        color = if (state.playerState.isBackgroundPlaying) AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { breathingGuide = !breathingGuide }) {
                    Text(
                        text = "🫁 呼吸",
                        color = if (breathingGuide) AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { showBinauralDialog = true }) {
                    Text(
                        text = "🧠 双耳",
                        color = if (state.binauralActive) AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 噪音 + 空间音效 + 触觉反馈
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val noiseLabel = if (state.noiseActive) {
                    when (state.noiseType) { "PINK" -> "🌸 粉噪"; "BROWN" -> "🍂 棕噪"; else -> "🔊 白噪" }
                } else "🔇 噪音"
                TextButton(onClick = { showNoiseDialog = true }) {
                    Text(
                        text = noiseLabel,
                        color = if (state.noiseActive) AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val spatialLabel = when (state.spatialMode) {
                    "SWEEP" -> "↔ 扫掠"; "CIRCLE" -> "🔄 环绕"; "WIDE" -> "🌐 扩展"; else -> "🎧 3D"
                }
                TextButton(onClick = { viewModel.cycleSpatialMode() }) {
                    Text(
                        text = spatialLabel,
                        color = if (state.spatialMode != "OFF") AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { viewModel.toggleHaptic() }) {
                    Text(
                        text = "📳 触觉",
                        color = if (state.hapticEnabled) AccentPurple else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // 场景 + 书签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { showSceneDialog = true }) {
                    Text("🎬 场景", color = TextHint, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { showBookmarkDialog = true }) {
                    val bmCount = state.bookmarks.size
                    Text(
                        text = if (bmCount > 0) "🔖 书签($bmCount)" else "🔖 书签",
                        color = TextHint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { viewModel.fadeOut(5000L) }) {
                    Text("🌙 淡出", color = TextHint, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 呼吸引导动画叠加层
        if (breathingGuide) {
            BreathingOverlay()
        }

        // 底部进度条区域
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 90.dp)
        ) {
            // 可拖动进度条（local drag state for smooth seeking）
            val progressFraction = if (state.playerState.durationMs > 0)
                state.playerState.progressMs.toFloat() / state.playerState.durationMs else 0f
            val isPlaying = state.playerState.isPlaying
            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(progressFraction) }

            Slider(
                value = if (isDragging) dragFraction else progressFraction,
                onValueChange = { fraction ->
                    isDragging = true
                    dragFraction = fraction
                },
                onValueChangeFinished = {
                    isDragging = false
                    val positionMs = (dragFraction * state.playerState.durationMs).toLong()
                    viewModel.seekTo(positionMs)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentPurple,
                    activeTrackColor = AccentPurple,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            val waveformBytesBottom by viewModel.waveformBytes.collectAsStateWithLifecycle()
            SoundCloudWaveform(
                waveformBytes = waveformBytesBottom,
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 时间显示行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(state.playerState.progressMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = formatDuration(state.playerState.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
        }
        }

        // Hypnosis tap overlay (tap to show UI)
        if (state.hypnosisEnabled && !state.hypnosisUiVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.showHypnosisUi() }
            )
        }
    }

    // 定时器选择对话框
    if (showTimerDialog) {
        var showCustomPicker by remember { mutableStateOf(false) }
        var showPomodoroCustom by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = {
                Text("定时器", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Sleep Timer ──
                    Text("睡眠定时", color = TextHint, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))

                    if (showCustomPicker) {
                        // Custom time: hours + minutes sliders
                        var customHours by remember { mutableIntStateOf(0) }
                        var customMinutes by remember { mutableIntStateOf(15) }
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("小时", color = TextSecondary, fontSize = 13.sp)
                                Slider(value = customHours.toFloat(), onValueChange = { customHours = it.toInt() },
                                    valueRange = 0f..3f, steps = 2,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                                Text("${customHours}h", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(36.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("分钟", color = TextSecondary, fontSize = 13.sp)
                                Slider(value = customMinutes.toFloat(), onValueChange = { customMinutes = it.toInt() },
                                    valueRange = 0f..59f, steps = 58,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                                Text("${customMinutes}m", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showCustomPicker = false }) {
                                    Text("取消", color = TextSecondary)
                                }
                                Button(onClick = {
                                    val total = customHours * 3600 + customMinutes * 60
                                    if (total > 0 && total <= 10800) {
                                        viewModel.setTimerSeconds(total)
                                        showTimerDialog = false
                                    }
                                }, enabled = (customHours * 60 + customMinutes) > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                                    Text("开始")
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTimerChip("15分") { viewModel.setTimerSeconds(15 * 60); showTimerDialog = false }
                            QuickTimerChip("30分") { viewModel.setTimerSeconds(30 * 60); showTimerDialog = false }
                            QuickTimerChip("45分") { viewModel.setTimerSeconds(45 * 60); showTimerDialog = false }
                            QuickTimerChip("60分") { viewModel.setTimerSeconds(60 * 60); showTimerDialog = false }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { showCustomPicker = true }) {
                            Text("⏱ 自定义...", color = AccentPurple, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        TimerOption(
                            text = if (state.stopAfterCurrent) "✓ 播完当前停止" else "播完当前停止",
                            color = if (state.stopAfterCurrent) AccentPurple else TextSecondary
                        ) {
                            viewModel.toggleStopAfterCurrent(); showTimerDialog = false
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Pomodoro ──
                    Text("番茄钟", color = TextHint, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))

                    if (showPomodoroCustom) {
                        var focusMin by remember { mutableIntStateOf(state.pomodoroCustomFocusMin) }
                        var breakMin by remember { mutableIntStateOf(state.pomodoroCustomBreakMin) }
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text("专注时长: ${focusMin}分钟", color = TextSecondary, fontSize = 13.sp)
                            Slider(value = focusMin.toFloat(), onValueChange = { focusMin = it.toInt() },
                                valueRange = 1f..120f, steps = 118,
                                colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                            Spacer(Modifier.height(8.dp))
                            Text("休息时长: ${breakMin}分钟", color = TextSecondary, fontSize = 13.sp)
                            Slider(value = breakMin.toFloat(), onValueChange = { breakMin = it.toInt() },
                                valueRange = 1f..30f, steps = 28,
                                colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showPomodoroCustom = false }) {
                                    Text("取消", color = TextSecondary)
                                }
                                Button(onClick = {
                                    viewModel.updatePomodoroCustomFocus(focusMin)
                                    viewModel.updatePomodoroCustomBreak(breakMin)
                                    viewModel.startPomodoro(focusMin, breakMin)
                                    showTimerDialog = false
                                }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                                    Text("🍅 开始")
                                }
                            }
                        }
                    } else {
                        TimerOption("🍅 ${state.pomodoroCustomFocusMin}分钟专注 + ${state.pomodoroCustomBreakMin}分钟休息") {
                            viewModel.startPomodoro(state.pomodoroCustomFocusMin, state.pomodoroCustomBreakMin)
                            showTimerDialog = false
                        }
                        TextButton(onClick = { showPomodoroCustom = true }) {
                            Text("⚙ 自定义番茄钟...", color = AccentPurple, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 双耳节拍选择对话框
    if (showBinauralDialog) {
        AlertDialog(
            onDismissRequest = { showBinauralDialog = false },
            title = {
                Text(
                    text = "双耳节拍",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val presetsByCategory = BinauralPreset.PRESETS.groupBy { it.category }
                    presetsByCategory.forEach { (category, presets) ->
                        Text(
                            text = category,
                            color = TextHint,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        presets.forEach { preset ->
                            val isActive = state.binauralActive && state.binauralPreset == preset
                            TextButton(
                                onClick = {
                                    viewModel.toggleBinaural(preset)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isActive) "▶ ${preset.name}" else "${preset.name}  (${preset.description})",
                                    color = if (isActive) AccentPurple else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    // 停止按钮
                    if (state.binauralActive) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("音量", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.binauralVolume,
                                onValueChange = { viewModel.setBinauralVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentPurple,
                                    activeTrackColor = AccentPurple,
                                    inactiveTrackColor = DarkSurfaceVariant
                                )
                            )
                            Text(
                                "${(state.binauralVolume * 100).toInt()}%",
                                color = TextHint,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.stopBinaural()
                                showBinauralDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止节拍", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBinauralDialog = false }) {
                    Text("关闭", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 噪音类型选择对话框
    if (showNoiseDialog) {
        AlertDialog(
            onDismissRequest = { showNoiseDialog = false },
            title = { Text("噪音生成", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "WHITE" to "白噪音 — 均匀频率，助专注",
                        "PINK" to "粉红噪音 — 低频更强，助睡眠",
                        "BROWN" to "棕色噪音 — 更深沉，助放松"
                    ).forEach { (type, desc) ->
                        val isActive = state.noiseActive && state.noiseType == type
                        TextButton(
                            onClick = {
                                viewModel.toggleNoise(com.asmrhelper.player.NoiseType.valueOf(type))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isActive) "▶ $desc" else desc,
                                color = if (isActive) AccentPurple else TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (state.noiseActive) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("音量", color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.noiseVolume,
                                onValueChange = { viewModel.setNoiseVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentPurple,
                                    activeTrackColor = AccentPurple,
                                    inactiveTrackColor = DarkSurfaceVariant
                                )
                            )
                            Text(
                                "${(state.noiseVolume * 100).toInt()}%",
                                color = TextHint,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { viewModel.stopNoise(); showNoiseDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止噪音", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
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

    // 环境音选择对话框
    if (showAmbientDialog) {
        AlertDialog(
            onDismissRequest = { showAmbientDialog = false },
            title = { Text("环境音选择", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.ambientAudios.isEmpty()) {
                        Text(
                            "暂无环境音，请在设置中导入",
                            color = TextHint,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
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
                                Text(
                                    text = name,
                                    color = if (isSelected) AccentPurple else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when {
                                        isPlaying -> "▶ 播放中"
                                        isSelected -> "已选"
                                        else -> ""
                                    },
                                    color = if (isPlaying) AccentPurple else TextHint,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (state.playerState.isBackgroundPlaying) {
                        HorizontalDivider(
                            color = DarkSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                viewModel.toggleBackground()
                                showAmbientDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("■ 停止环境音", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
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

    // 场景管理对话框
    if (showSceneDialog) {
        var sceneName by remember { mutableStateOf("") }
        var sceneTab by remember { mutableIntStateOf(0) } // 0=save, 1=load
        AlertDialog(
            onDismissRequest = { showSceneDialog = false },
            title = { Text("场景管理", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { sceneTab = 0 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "保存场景",
                                color = if (sceneTab == 0) AccentPurple else TextSecondary,
                                fontWeight = if (sceneTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        TextButton(
                            onClick = { sceneTab = 1 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "加载场景",
                                color = if (sceneTab == 1) AccentPurple else TextSecondary,
                                fontWeight = if (sceneTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (sceneTab == 0) {
                        // Save tab
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (sceneName.isEmpty()) {
                                Text("场景名称", color = TextHint, fontSize = 14.sp)
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = sceneName,
                                onValueChange = { sceneName = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (sceneName.isNotEmpty()) {
                                    viewModel.saveCurrentScene(sceneName)
                                    showSceneDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Text("保存当前状态")
                        }
                    } else {
                        // Load tab
                        if (state.scenes.isEmpty()) {
                            Text("暂无保存的场景", color = TextHint, fontSize = 14.sp)
                        }
                        state.scenes.forEach { scene ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(scene.name, color = TextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = listOfNotNull(
                                            if (scene.timerMinutes > 0) "${scene.timerMinutes}分" else null,
                                            if (scene.noiseType.isNotEmpty() && scene.noiseType != "WHITE") scene.noiseType else null,
                                            if (scene.visualizerEnabled) "可视化" else null
                                        ).joinToString(" · ").ifEmpty { "无额外设置" },
                                        color = TextHint,
                                        fontSize = 11.sp
                                    )
                                }
                                Row {
                                    IconButton(onClick = { viewModel.applyScene(scene); showSceneDialog = false }) {
                                        Text("▶", color = AccentPurple)
                                    }
                                    IconButton(onClick = { viewModel.deleteScene(scene) }) {
                                        Text("✕", color = ErrorRed, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSceneDialog = false }) { Text("关闭", color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 书签管理对话框
    if (showBookmarkDialog) {
        var newBookmarkName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("音频书签", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Add bookmark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (newBookmarkName.isEmpty()) {
                                Text("书签名", color = TextHint, fontSize = 14.sp)
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = newBookmarkName,
                                onValueChange = { newBookmarkName = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (newBookmarkName.isNotEmpty()) {
                                viewModel.addBookmark(newBookmarkName)
                                newBookmarkName = ""
                            }
                        }) {
                            Text("添加", color = AccentPurple)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (state.bookmarks.isEmpty()) {
                        Text("暂无书签", color = TextHint, fontSize = 14.sp)
                    }
                    state.bookmarks.forEach { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.jumpToBookmark(bm.positionMs); showBookmarkDialog = false }
                            ) {
                                Text(bm.name, color = TextPrimary, fontSize = 14.sp)
                                Text(
                                    text = formatDuration(bm.positionMs),
                                    color = AccentPurple,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { viewModel.deleteBookmark(bm) }) {
                                Text("✕", color = ErrorRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBookmarkDialog = false }) { Text("关闭", color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun QuickTimerChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TimerOption(
    text: String,
    color: Color = TextSecondary,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SoundCloudWaveform(
    waveformBytes: ByteArray?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth interpolation state — lerp toward latest waveform data
    val currentData = remember { mutableStateOf(FloatArray(0)) }
    val targetData = remember { mutableStateOf(FloatArray(0)) }

    // Convert raw ByteArray to normalized floats when new data arrives
    LaunchedEffect(waveformBytes) {
        if (waveformBytes != null && waveformBytes!!.isNotEmpty()) {
            val floats = FloatArray(waveformBytes!!.size)
            for (i in floats.indices) {
                // Byte 0-255 → float -1.0 to 1.0
                floats[i] = ((waveformBytes!![i].toInt() and 0xFF) - 128) / 128f
            }
            targetData.value = floats
        }
    }

    // Smooth animation: lerp current → target each frame
    LaunchedEffect(Unit) {
        while (true) {
            val cur = currentData.value
            val tgt = targetData.value
            if (cur.size != tgt.size && tgt.isNotEmpty()) {
                currentData.value = tgt.copyOf()
            } else if (cur.isNotEmpty() && tgt.isNotEmpty()) {
                val smoothed = FloatArray(cur.size)
                for (i in smoothed.indices) {
                    if (i < tgt.size) {
                        smoothed[i] = cur[i] + (tgt[i] - cur[i]) * 0.35f
                    }
                }
                currentData.value = smoothed
            }
            delay(16L) // ~60fps
        }
    }

    val data = currentData.value

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val centerY = size.height / 2f
        val maxAmp = size.height / 2f - 2.dp.toPx()

        // Draw center line (faint guide)
        drawLine(
            color = Color(0xFFBB86FC).copy(alpha = 0.15f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (!isPlaying && data.all { kotlin.math.abs(it) < 0.02f }) return@Canvas

        // Build path: wave oscillates around centerY
        val path = Path()
        val step = size.width / data.size.coerceAtLeast(1)

        path.moveTo(0f, centerY)
        for (i in data.indices) {
            val x = i * step
            // Apply exponential scaling so quiet parts sit near center
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.abs(normalized).pow(0.7f)
            val y = centerY + scaled * maxAmp
            path.lineTo(x, y)
        }
        // Complete the symmetric shape (mirror below center line)
        for (i in data.indices.reversed()) {
            val x = i * step
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.abs(normalized).pow(0.7f)
            val y = centerY - scaled * maxAmp  // mirror
            path.lineTo(x, y)
        }
        path.close()

        // Fill the waveform with gradient
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFBB86FC).copy(alpha = 0.6f),
                    Color(0xFFBB86FC).copy(alpha = 0.15f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        // Draw the top edge line (bright)
        val edgePath = Path()
        edgePath.moveTo(0f, centerY)
        for (i in data.indices) {
            val x = i * step
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.abs(normalized).pow(0.7f)
            val y = centerY + scaled * maxAmp
            edgePath.lineTo(x, y)
        }
        drawPath(
            path = edgePath,
            color = Color(0xFFBB86FC).copy(alpha = 0.9f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

@Composable
private fun BreathingOverlay() {
    val radiusFraction = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0.15f) }

    LaunchedEffect(Unit) {
        while (true) {
            radiusFraction.animateTo(1.0f, tween(4000))
            radiusFraction.animateTo(0.3f, tween(4000))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(0.35f, tween(4000))
            alpha.animateTo(0.15f, tween(4000))
        }
    }

    val currentRadius = radiusFraction.value
    val currentAlpha = alpha.value

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val baseRadius = 120.dp
        Canvas(
            modifier = Modifier
                .size(baseRadius * 2)
                .graphicsLayer {
                    scaleX = currentRadius
                    scaleY = currentRadius
                    this.alpha = currentAlpha
                }
        ) {
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = size.minDimension / 2
            )
        }

        // Phase label
        val phaseText = when {
            currentRadius > 0.8f -> "吸气"
            currentRadius < 0.4f -> "呼气"
            else -> "屏息"
        }
        Text(
            text = phaseText,
            color = TextPrimary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun TriggerEffectOverlay(
    triggerFlow: SharedFlow<TriggerEffectConfig>,
    particleCount: Int = 16,
    modifier: Modifier = Modifier
) {
    var particles by remember { mutableStateOf(listOf<TriggerParticle>()) }
    var frameTick by remember { mutableStateOf(0L) }

    // Keep particleCount reactive inside long-lived LaunchedEffect
    val currentParticleCount by rememberUpdatedState(particleCount)

    // Frame clock for continuous animation
    LaunchedEffect(Unit) {
        while (true) {
            frameTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(16) // ~60fps
        }
    }

    LaunchedEffect(Unit) {
        triggerFlow.collect { config ->
            val now = System.currentTimeMillis()
            val count = currentParticleCount
            val newParticles = (0 until count).map { i ->
                val angle = when (config.animationType) {
                    2 -> (-90f + (Random.nextFloat() * 40f - 20f)) // fountain: mostly upward
                    else -> (i * (360f / count.toFloat()))
                }
                TriggerParticle(
                    id = now.toInt() + i,
                    x = (35..65).random().toFloat() / 100f,
                    y = if (config.animationType == 2) 0.85f else (0.3f + (Random.nextFloat() * 0.2f)).toFloat(),
                    angle = angle,
                    speed = (0.5f + Random.nextFloat() * 1.5f),
                    color = Color(config.color),
                    emoji = config.emoji,
                    animationType = config.animationType,
                    createdAt = now
                )
            }
            particles = (particles + newParticles).filter { now - it.createdAt < 2000 }
        }
    }

    // Read frameTick to force recomposition on every frame
    val now = frameTick
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val elapsed = (now - p.createdAt).toFloat() / 2000f
            if (elapsed >= 1f) return@forEach
            val alpha = (1f - elapsed).coerceIn(0f, 1f)
            val particleSize = when (p.animationType) {
                1 -> (10f + 6f * kotlin.math.abs(kotlin.math.sin(elapsed * 20f).toFloat())) // flash oscillates
                else -> 7f * (1f - elapsed * 0.5f)
            }

            val (px, py) = when (p.animationType) {
                1 -> {
                    val drift = elapsed * 30f
                    val rad = Math.toRadians(p.angle.toDouble())
                    Offset(
                        size.width * p.x + (drift * kotlin.math.cos(rad)).toFloat(),
                        size.height * p.y + (drift * kotlin.math.sin(rad)).toFloat()
                    )
                }
                2 -> {
                    val t = elapsed
                    val rad = Math.toRadians(p.angle.toDouble())
                    val vx = kotlin.math.cos(rad).toFloat() * p.speed * 200f
                    val vy = -kotlin.math.sin(rad).toFloat() * p.speed * 300f
                    val gravity = 400f * t * t
                    Offset(
                        size.width * p.x + vx * t,
                        size.height * p.y + vy * t + gravity
                    )
                }
                else -> {
                    val dist = elapsed * 150f * p.speed
                    val rad = Math.toRadians(p.angle.toDouble())
                    Offset(
                        size.width * p.x + (dist * kotlin.math.cos(rad)).toFloat(),
                        size.height * p.y + (dist * kotlin.math.sin(rad)).toFloat()
                    )
                }
            }

            if (p.emoji.isNotEmpty()) {
                // Draw glow behind emoji
                drawCircle(p.color.copy(alpha = alpha * 0.3f), particleSize * 2f, Offset(px, py))
                // Draw the actual emoji text
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(p.emoji),
                    style = TextStyle(fontSize = 20.sp)
                )
                drawText(
                    textLayoutResult,
                    topLeft = Offset(
                        px - textLayoutResult.size.width / 2f,
                        py - textLayoutResult.size.height / 2f
                    ),
                    alpha = alpha
                )
            } else {
                drawCircle(p.color.copy(alpha = alpha), particleSize, Offset(px, py))
            }
        }
    }
}

private data class TriggerParticle(
    val id: Int,
    val x: Float,
    val y: Float,
    val angle: Float,
    val speed: Float,
    val color: Color,
    val emoji: String,
    val animationType: Int,
    val createdAt: Long
)
