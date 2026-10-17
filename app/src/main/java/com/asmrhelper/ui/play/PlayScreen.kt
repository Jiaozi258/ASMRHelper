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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.stringResource
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
import com.asmrhelper.R
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.player.BinauralPreset
import com.asmrhelper.ui.components.AsmrDropdownMenu
import com.asmrhelper.ui.components.HypnosisBackground
import com.asmrhelper.ui.components.HypnosisBgType
import com.asmrhelper.ui.components.MenuItem
import com.asmrhelper.ui.components.PlayPauseButton
import com.asmrhelper.ui.settings.SettingsViewModel
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DynamicIsland
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
    onNavigateToSlideshow: () -> Unit = {},
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

        // Ambiance particle effects (behind all UI)
        val effectsEnabled by viewModel.ambianceEffectsEnabled.collectAsStateWithLifecycle()
        if (effectsEnabled) {
            com.asmrhelper.ui.components.AmbianceOverlay(
                isPlaying = state.playerState.isPlaying,
                accentColor = LocalAccentColor.current,
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

        // 灵动岛「正在播放」胶囊（切歌/恢复播放时展开）
        DynamicIsland(
            title = state.displayTitle.ifEmpty { null },
            isPlaying = state.playerState.isPlaying,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
        )

        // 右上角下拉菜单
        AsmrDropdownMenu(
            items = listOf(
                MenuItem("playlist", stringResource(R.string.play_menu_playlist)),
                MenuItem("favorites", stringResource(R.string.play_menu_favorites)),
                MenuItem("scan", stringResource(R.string.play_menu_scan)),
                MenuItem("file_manager", stringResource(R.string.play_menu_file_manager)),
                MenuItem("background", stringResource(R.string.play_menu_background)),
                MenuItem("slideshow", stringResource(R.string.play_menu_images)),
                MenuItem("trigger_pad", stringResource(R.string.play_menu_trigger_pad)),
                MenuItem("sleep_journal", stringResource(R.string.play_menu_sleep_journal)),
                MenuItem("video_audio", stringResource(R.string.play_menu_video_audio)),
                MenuItem("settings", stringResource(R.string.play_menu_settings))
            ),
            onItemClick = { item ->
                when (item.id) {
                    "playlist" -> onNavigateToPlaylist()
                    "favorites" -> onNavigateToLibrary(1)
                    "scan" -> onNavigateToLibrary(0)
                    "file_manager" -> onNavigateToLibrary(2)
                    "trigger_pad" -> onNavigateToTriggerPad()
                    "background" -> onNavigateToBackground()
                    "slideshow" -> onNavigateToSlideshow()
                    "sleep_journal" -> onNavigateToSleepJournal()
                    "video_audio" -> onNavigateToLibrary(3)
                    "settings" -> onNavigateToSettings()
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
        )

        // 音频可视化（始终显示在播放界面的最上方）
        val visualizerOn by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
        if (visualizerOn) {
            val waveformBytes by viewModel.waveformBytes.collectAsStateWithLifecycle()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    // 移除黑色背景条，让波形直接叠加在背景上，为粒子特效腾出空间
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
                    ))
            ) {
                SoundCloudWaveform(
                    waveformBytes = waveformBytes,
                    isPlaying = state.playerState.isPlaying,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                )
            }
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
                    text = state.displayTitle.ifEmpty { stringResource(R.string.play_not_playing) },
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
                    LoopMode.NONE -> stringResource(R.string.play_loop_none)
                    LoopMode.SINGLE -> stringResource(R.string.play_loop_single)
                    LoopMode.LIST -> stringResource(R.string.play_loop_list)
                    LoopMode.SHUFFLE -> stringResource(R.string.play_loop_shuffle)
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
                        stringResource(R.string.play_timer)
                    }
                    Text(
                        text = label,
                        color = if (pomodoroActive || timerActive) LocalAccentColor.current else TextSecondary,
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
                        contentDescription = stringResource(R.string.play_previous),
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
                        contentDescription = stringResource(R.string.play_next),
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
                        text = stringResource(R.string.play_ambient),
                        color = if (state.playerState.isBackgroundPlaying) LocalAccentColor.current else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { breathingGuide = !breathingGuide }) {
                    Text(
                        text = stringResource(R.string.play_breathing),
                        color = if (breathingGuide) LocalAccentColor.current else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { showBinauralDialog = true }) {
                    Text(
                        text = stringResource(R.string.play_binaural),
                        color = if (state.binauralActive) LocalAccentColor.current else TextHint,
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
                    when (state.noiseType) {
                        "PINK" -> stringResource(R.string.play_noise_pink)
                        "BROWN" -> stringResource(R.string.play_noise_brown)
                        else -> stringResource(R.string.play_noise_white)
                    }
                } else stringResource(R.string.play_noise_off)
                TextButton(onClick = { showNoiseDialog = true }) {
                    Text(
                        text = noiseLabel,
                        color = if (state.noiseActive) LocalAccentColor.current else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val spatialLabel = when (state.spatialMode) {
                    "D3" -> stringResource(R.string.play_spatial_3d)
                    "SWEEP" -> stringResource(R.string.play_spatial_sweep)
                    "CIRCLE" -> stringResource(R.string.play_spatial_circle)
                    "WIDE" -> stringResource(R.string.play_spatial_wide)
                    else -> stringResource(R.string.play_spatial_off)
                }
                TextButton(onClick = { viewModel.cycleSpatialMode() }) {
                    Text(
                        text = spatialLabel,
                        color = if (state.spatialMode != "OFF") LocalAccentColor.current else TextHint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { viewModel.toggleHaptic() }) {
                    Text(
                        text = stringResource(R.string.play_haptic),
                        color = if (state.hapticEnabled) LocalAccentColor.current else TextHint,
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
                    Text(stringResource(R.string.play_scene), color = TextHint, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { showBookmarkDialog = true }) {
                    val bmCount = state.bookmarks.size
                    Text(
                        text = if (bmCount > 0) stringResource(R.string.play_bookmark_count, bmCount) else stringResource(R.string.play_bookmark),
                        color = TextHint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = { viewModel.fadeOut(5000L) }) {
                    Text(stringResource(R.string.play_fade_out), color = TextHint, style = MaterialTheme.typography.labelSmall)
                }
            }

            // ── 均衡器已移至设置界面 ──
            // （此前均衡器面板会遮挡播放进度条，现已迁移到 设置 → 均衡器）
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
                .padding(bottom = 20.dp)
        ) {
            // 可拖动进度条（local drag state for smooth seeking）
            // 钳制到 0..1：缓冲/VBR/取整可能让 progressMs 略大于 durationMs，
            // 超出会触发 Slider 的 IllegalArgumentException 崩溃。
            val progressFraction = if (state.playerState.durationMs > 0)
                (state.playerState.progressMs.toFloat() / state.playerState.durationMs).coerceIn(0f, 1f)
            else 0f
            val isPlaying = state.playerState.isPlaying
            var isDragging by remember { mutableStateOf(false) }
            var dragFraction by remember { mutableFloatStateOf(progressFraction) }

            // 拖拽时显示目标时间点
            if (isDragging) {
                val dragMs = (dragFraction * state.playerState.durationMs).toLong()
                Text(
                    text = stringResource(R.string.play_drag_to, formatDuration(dragMs)),
                    color = LocalAccentColor.current,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

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
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = LocalAccentColor.current,
                    activeTrackColor = LocalAccentColor.current,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

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

            Spacer(modifier = Modifier.height(2.dp))
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
                Text(stringResource(R.string.play_timer_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Sleep Timer ──
                    Text(stringResource(R.string.play_sleep_timer), color = TextHint, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))

                    if (showCustomPicker) {
                        // Custom time: hours + minutes sliders
                        var customHours by remember { mutableIntStateOf(0) }
                        var customMinutes by remember { mutableIntStateOf(15) }
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.play_hours), color = TextSecondary, fontSize = 13.sp)
                                Slider(value = customHours.toFloat(), onValueChange = { customHours = it.toInt() },
                                    valueRange = 0f..3f, steps = 2,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current))
                                Text("${customHours}h", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(36.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.play_minutes), color = TextSecondary, fontSize = 13.sp)
                                Slider(value = customMinutes.toFloat(), onValueChange = { customMinutes = it.toInt() },
                                    valueRange = 0f..59f, steps = 58,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current))
                                Text("${customMinutes}m", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showCustomPicker = false }) {
                                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                                }
                                Button(onClick = {
                                    val total = customHours * 3600 + customMinutes * 60
                                    if (total > 0 && total <= 10800) {
                                        viewModel.setTimerSeconds(total)
                                        showTimerDialog = false
                                    }
                                }, enabled = (customHours * 60 + customMinutes) > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)) {
                                    Text(stringResource(R.string.common_start))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTimerChip(stringResource(R.string.play_timer_15)) { viewModel.setTimerSeconds(15 * 60); showTimerDialog = false }
                            QuickTimerChip(stringResource(R.string.play_timer_30)) { viewModel.setTimerSeconds(30 * 60); showTimerDialog = false }
                            QuickTimerChip(stringResource(R.string.play_timer_45)) { viewModel.setTimerSeconds(45 * 60); showTimerDialog = false }
                            QuickTimerChip(stringResource(R.string.play_timer_60)) { viewModel.setTimerSeconds(60 * 60); showTimerDialog = false }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { showCustomPicker = true }) {
                            Text(stringResource(R.string.play_timer_custom), color = LocalAccentColor.current, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        TimerOption(
                            text = if (state.stopAfterCurrent) stringResource(R.string.play_stop_after_current_checked) else stringResource(R.string.play_stop_after_current),
                            color = if (state.stopAfterCurrent) LocalAccentColor.current else TextSecondary
                        ) {
                            viewModel.toggleStopAfterCurrent(); showTimerDialog = false
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Pomodoro ──
                    Text(stringResource(R.string.play_pomodoro), color = TextHint, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))

                    if (showPomodoroCustom) {
                        var focusMin by remember { mutableIntStateOf(state.pomodoroCustomFocusMin) }
                        var breakMin by remember { mutableIntStateOf(state.pomodoroCustomBreakMin) }
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(stringResource(R.string.play_focus_duration, focusMin), color = TextSecondary, fontSize = 13.sp)
                            Slider(value = focusMin.toFloat(), onValueChange = { focusMin = it.toInt() },
                                valueRange = 1f..120f, steps = 118,
                                colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.play_break_duration, breakMin), color = TextSecondary, fontSize = 13.sp)
                            Slider(value = breakMin.toFloat(), onValueChange = { breakMin = it.toInt() },
                                valueRange = 1f..30f, steps = 28,
                                colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current))
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showPomodoroCustom = false }) {
                                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                                }
                                Button(onClick = {
                                    viewModel.updatePomodoroCustomFocus(focusMin)
                                    viewModel.updatePomodoroCustomBreak(breakMin)
                                    viewModel.startPomodoro(focusMin, breakMin)
                                    showTimerDialog = false
                                }, colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)) {
                                    Text(stringResource(R.string.play_pomodoro_start))
                                }
                            }
                        }
                    } else {
                        TimerOption(stringResource(R.string.play_pomodoro_option, state.pomodoroCustomFocusMin, state.pomodoroCustomBreakMin)) {
                            viewModel.startPomodoro(state.pomodoroCustomFocusMin, state.pomodoroCustomBreakMin)
                            showTimerDialog = false
                        }
                        TextButton(onClick = { showPomodoroCustom = true }) {
                            Text(stringResource(R.string.play_pomodoro_custom), color = LocalAccentColor.current, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
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
                    text = stringResource(R.string.play_binaural_title),
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
                                    color = if (isActive) LocalAccentColor.current else TextSecondary,
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
                            Text(stringResource(R.string.common_volume), color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.binauralVolume,
                                onValueChange = { viewModel.setBinauralVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = LocalAccentColor.current,
                                    activeTrackColor = LocalAccentColor.current,
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
                            Text(stringResource(R.string.play_stop_binaural), color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBinauralDialog = false }) {
                    Text(stringResource(R.string.common_close), color = TextSecondary)
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
            title = { Text(stringResource(R.string.play_noise_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "WHITE" to R.string.play_noise_white_desc,
                        "PINK" to R.string.play_noise_pink_desc,
                        "BROWN" to R.string.play_noise_brown_desc
                    ).forEach { (type, descRes) ->
                        val desc = stringResource(descRes)
                        val isActive = state.noiseActive && state.noiseType == type
                        TextButton(
                            onClick = {
                                viewModel.toggleNoise(com.asmrhelper.player.NoiseType.valueOf(type))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isActive) "▶ $desc" else desc,
                                color = if (isActive) LocalAccentColor.current else TextSecondary,
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
                            Text(stringResource(R.string.common_volume), color = TextSecondary, fontSize = 12.sp)
                            Slider(
                                value = state.noiseVolume,
                                onValueChange = { viewModel.setNoiseVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = LocalAccentColor.current,
                                    activeTrackColor = LocalAccentColor.current,
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
                            Text(stringResource(R.string.play_stop_noise), color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNoiseDialog = false }) { Text(stringResource(R.string.common_close), color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 环境音选择对话框
    if (showAmbientDialog) {
        AlertDialog(
            onDismissRequest = { showAmbientDialog = false },
            title = { Text(stringResource(R.string.play_ambient_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── Built-in nature sounds ──
                    Text(
                        stringResource(R.string.play_builtin_sounds),
                        color = TextHint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    com.asmrhelper.domain.model.AmbientSource.BUILT_IN.forEach { source ->
                        val isBuiltInSelected = state.selectedAmbientPath == source.sourcePath
                        val isBuiltInPlaying = state.playerState.isBackgroundPlaying && isBuiltInSelected
                        TextButton(
                            onClick = {
                                viewModel.playBuiltInAmbient(source)
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
                                    text = source.label,
                                    color = if (isBuiltInSelected) LocalAccentColor.current else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when {
                                        isBuiltInPlaying -> stringResource(R.string.play_playing)
                                        isBuiltInSelected -> stringResource(R.string.play_selected)
                                        else -> ""
                                    },
                                    color = if (isBuiltInPlaying) LocalAccentColor.current else TextHint,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = DarkSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Text(
                        stringResource(R.string.play_imported_ambient),
                        color = TextHint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (state.ambientAudios.isEmpty()) {
                        Text(
                            stringResource(R.string.play_no_ambient),
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
                                    color = if (isSelected) LocalAccentColor.current else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = when {
                                        isPlaying -> stringResource(R.string.play_playing)
                                        isSelected -> stringResource(R.string.play_selected)
                                        else -> ""
                                    },
                                    color = if (isPlaying) LocalAccentColor.current else TextHint,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    // ── Ambient loop toggle ──
                    HorizontalDivider(
                        color = DarkSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.play_ambient_loop),
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.play_ambient_loop_desc),
                                color = TextHint,
                                fontSize = 11.sp
                            )
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
                            Text(stringResource(R.string.play_stop_ambient), color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAmbientDialog = false }) { Text(stringResource(R.string.common_close), color = TextSecondary) } },
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
            title = { Text(stringResource(R.string.play_scene_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
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
                                stringResource(R.string.play_save_scene),
                                color = if (sceneTab == 0) LocalAccentColor.current else TextSecondary,
                                fontWeight = if (sceneTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        TextButton(
                            onClick = { sceneTab = 1 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.play_load_scene),
                                color = if (sceneTab == 1) LocalAccentColor.current else TextSecondary,
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
                                Text(stringResource(R.string.play_scene_name), color = TextHint, fontSize = 14.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)
                        ) {
                            Text(stringResource(R.string.play_save_current_state))
                        }
                    } else {
                        // Load tab
                        if (state.scenes.isEmpty()) {
                            Text(stringResource(R.string.play_no_scenes), color = TextHint, fontSize = 14.sp)
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
                                            if (scene.timerMinutes > 0) stringResource(R.string.play_scene_minutes, scene.timerMinutes) else null,
                                            if (scene.noiseType.isNotEmpty() && scene.noiseType != "WHITE") scene.noiseType else null,
                                            if (scene.visualizerEnabled) stringResource(R.string.play_visualizer_on) else null
                                        ).joinToString(" · ").ifEmpty { stringResource(R.string.play_no_extra) },
                                        color = TextHint,
                                        fontSize = 11.sp
                                    )
                                }
                                Row {
                                    IconButton(onClick = { viewModel.applyScene(scene); showSceneDialog = false }) {
                                        Text("▶", color = LocalAccentColor.current)
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
            dismissButton = { TextButton(onClick = { showSceneDialog = false }) { Text(stringResource(R.string.common_close), color = TextSecondary) } },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 书签管理对话框
    if (showBookmarkDialog) {
        var newBookmarkName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text(stringResource(R.string.play_bookmark_title), color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
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
                                Text(stringResource(R.string.play_bookmark_name), color = TextHint, fontSize = 14.sp)
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
                            Text(stringResource(R.string.play_add), color = LocalAccentColor.current)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (state.bookmarks.isEmpty()) {
                        Text(stringResource(R.string.play_no_bookmarks), color = TextHint, fontSize = 14.sp)
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
                                    color = LocalAccentColor.current,
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
            dismissButton = { TextButton(onClick = { showBookmarkDialog = false }) { Text(stringResource(R.string.common_close), color = TextSecondary) } },
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
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
    val simulated = remember { mutableStateOf(FloatArray(0)) }
    var animTime by remember { mutableFloatStateOf(0f) }
    val hasRealData = remember { mutableStateOf(false) }

    // Convert raw ByteArray to normalized floats when new data arrives
    // Use content hash code as key since ByteArray.equals() is reference equality,
    // which would cause LaunchedEffect to restart on every identical-content update.
    val waveformKey = waveformBytes?.contentHashCode()
    LaunchedEffect(waveformKey) {
        val wb = waveformBytes
        if (wb != null && wb.isNotEmpty()) {
            val floats = FloatArray(wb.size)
            for (i in floats.indices) {
                floats[i] = ((wb[i].toInt() and 0xFF) - 128) / 128f
            }
            targetData.value = floats
            hasRealData.value = true
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

    // Simulated waveform: animated sine-like pattern for devices without Visualizer API
    LaunchedEffect(Unit) {
        while (true) {
            if (isPlaying && !hasRealData.value) {
                animTime += 0.05f
                val sim = FloatArray(64)
                for (i in sim.indices) {
                    val t = i.toFloat() / sim.size * kotlin.math.PI.toFloat() * 2f
                    sim[i] = (kotlin.math.sin(t * 3f + animTime) * 0.55f
                        + kotlin.math.sin(t * 7f + animTime * 1.7f) * 0.25f
                        + kotlin.math.sin(t * 13f + animTime * 2.3f) * 0.15f
                        + kotlin.math.sin(t * 19f + animTime * 3.1f) * 0.05f)
                }
                simulated.value = sim
                currentData.value = sim
            } else if (!isPlaying) {
                animTime = 0f
                if (!hasRealData.value) currentData.value = FloatArray(0)
            }
            delay(33L) // ~30fps for smooth animation
        }
    }

    val data = currentData.value

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val maxAmp = size.height / 2f - 2.dp.toPx()

        if (data.isEmpty()) {
            // Draw only center line when no data
            drawLine(
                color = Color(0xFFBB86FC).copy(alpha = 0.15f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        // Draw center line
        drawLine(
            color = Color(0xFFBB86FC).copy(alpha = 0.18f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

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

    // Single combined LaunchedEffect prevents radius/alpha from drifting
    // out of sync over time (two independent coroutines would diverge).
    LaunchedEffect(Unit) {
        while (true) {
            // Inhale
            radiusFraction.animateTo(1.0f, tween(4000))
            alpha.animateTo(0.35f, tween(4000))
            // Exhale
            radiusFraction.animateTo(0.3f, tween(4000))
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
            currentRadius > 0.8f -> stringResource(R.string.play_inhale)
            currentRadius < 0.4f -> stringResource(R.string.play_exhale)
            else -> stringResource(R.string.play_hold)
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
