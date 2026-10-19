package com.asmrhelper.ui.play.folia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asmrhelper.R
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlaybackBackgroundMode
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.util.Duotone
import com.asmrhelper.util.LyricScrollStyle
import com.asmrhelper.util.MusicVisualizerMath
import com.asmrhelper.util.WordLyricLine
import com.asmrhelper.util.currentLineIndex
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * 全屏音频反应背景：双色调封面为底（真实提取主/次色），叠加随 FFT 频谱
 * 从底部向上流动的柔光柱，让整个画面跟随音频动。
 */
@Composable
fun AudioReactiveBackground(
    coverUrl: String?,
    waveform: ByteArray?,
    fft: FloatArray?,
    mode: PlaybackBackgroundMode = PlaybackBackgroundMode.BLURRED_COVER,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val fallbackPrimary = accent.toArgb().toLong() and 0xFFFFFFFFL
    // 次调做红蓝交换（而非同色加深），保证双色调永不塌成纯色
    val fallbackSecondary = Duotone.swapRedBlue(fallbackPrimary)
    val (primaryLong, secondaryLong) = rememberCoverPalette(coverUrl, fallbackPrimary, fallbackSecondary)
    val primary = Color(primaryLong.toInt())
    val secondary = Color(secondaryLong.toInt())

    val transition = rememberInfiniteTransition(label = "coverBg")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coverScale"
    )
    // 空闲也流动：一条横移的高光带 + 次色呼吸，无 FFT 数据时背景依旧有动感
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flowDrift"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // 频谱柱：优先 FFT，无 FFT 数据退回波形
    val bars = remember(fft, waveform) {
        val b = MusicVisualizerMath.barsFromFft(fft, 28)
        if (b.any { it > 0f }) b else MusicVisualizerMath.barsFromWaveform(waveform, 28)
    }

    Box(modifier = modifier.clipToBounds()) {
        when (mode) {
            PlaybackBackgroundMode.BLURRED_COVER -> {
                Box(modifier = Modifier.fillMaxSize().background(primary)) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .blur(40.dp)
                                .alpha(0.62f)
                        )
                    }
                    // 双色调渐变（真实主/次色，降低覆盖度让封面透出来）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        primary.copy(alpha = 0.58f),
                                        secondary.copy(alpha = 0.44f * breathe),
                                        primary.copy(alpha = 0.50f)
                                    )
                                )
                            )
                    )
                }
            }
            // 双色流动：无封面，主/次色色场 + 游走柔光斑 + 斜向高光带，流动更明显
            PlaybackBackgroundMode.FOLIA_DUOTONE -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // 主/次色随 drift 左右推移的三段渐变，色场本身也在动
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(primary, secondary, primary),
                            start = Offset(w * drift * 0.7f, 0f),
                            end = Offset(w * (0.6f + 0.7f * drift), h)
                        ),
                        size = Size(w, h)
                    )
                    // 两团游走柔光：更大、更亮、沿对角移动
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(secondary.copy(alpha = 0.9f), Color.Transparent),
                            center = Offset(w * (0.1f + 0.8f * drift), h * (0.15f + 0.35f * drift)),
                            radius = w * 1.1f
                        ),
                        size = Size(w, h)
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.55f * (0.4f + 0.6f * breathe)), Color.Transparent),
                            center = Offset(w * (0.9f - 0.8f * drift), h * (0.85f - 0.35f * drift)),
                            radius = w * 1.15f
                        ),
                        size = Size(w, h)
                    )
                    // 斜向高光带扫过画面，强化"流动感"
                    val sweep = drift * w * 1.8f - w * 0.4f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                            start = Offset(sweep, 0f),
                            end = Offset(sweep + w * 0.7f, h)
                        ),
                        size = Size(w, h)
                    )
                }
            }
            PlaybackBackgroundMode.SYSTEM_SOLID -> {
                val solid = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
                Box(modifier = Modifier.fillMaxSize().background(solid))
            }
        }

        // 反应层：横移高光带（始终流动）+ 底部向上流动的柔光柱（有音频数据时）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 流动高光带
            val bandX = drift * w * 1.4f - w * 0.2f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    startX = bandX - w * 0.35f,
                    endX = bandX + w * 0.35f
                ),
                size = Size(w, h)
            )
            // 底部柔光柱
            if (bars.any { it > 0f }) {
                val n = bars.size
                val bw = w / n
                bars.forEachIndexed { i, v ->
                    val bh = v * h * 0.85f
                    drawRoundRect(
                        color = accent.copy(alpha = 0.06f + 0.20f * v),
                        topLeft = Offset(i * bw + bw * 0.18f, h - bh),
                        size = Size(bw * 0.64f, bh),
                        cornerRadius = CornerRadius(bw * 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * 跳转歌词：一句一句跳，单行居中显示当前句，换句时整句轻微弹出强调
 * （不再逐词蹦出，避免逐词时间对不上）。字体大小 / 阴影 / 对齐读取设置。
 */
@Composable
fun CenteredLyrics(
    lines: List<WordLyricLine>,
    progressMs: Long,
    modifier: Modifier = Modifier,
    fontSize: Int = 16,
    shadowEnabled: Boolean = false,
    alignment: Int = 0
) {
    val accent = LocalAccentColor.current
    val currentIndex = remember(lines, progressMs) { lines.currentLineIndex(progressMs) }
    val currentLine = lines.getOrNull(currentIndex)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (lines.isEmpty() || currentLine == null) {
            Text(stringResource(R.string.no_lyrics), color = TextSecondary, fontSize = 15.sp)
        } else {
            // key(currentIndex) 让换句时重启动画：整句从 0.92 弹到 1
            key(currentIndex) {
                var popped by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { popped = true }
                val scale by animateFloatAsState(
                    targetValue = if (popped) 1f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "linePop"
                )
                Text(
                    text = currentLine.text,
                    color = accent,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = lyricTextAlign(alignment),
                    style = if (shadowEnabled) TextStyle(
                        shadow = Shadow(color = accent.copy(alpha = 0.6f), blurRadius = 14f)
                    ) else TextStyle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
        }
    }
}

/**
 * 滑动歌词：只展示上下文（上一句 / 当前句 / 下一句），当前句居中强调，
 * 随进度平滑滚动；视觉样式可选平铺或鱼眼。
 */
@Composable
fun ScrollLyrics(
    lines: List<WordLyricLine>,
    progressMs: Long,
    modifier: Modifier = Modifier,
    fontSize: Int = 16,
    lineSpacing: Float = 1.5f,
    shadowEnabled: Boolean = false,
    alignment: Int = 0,
    style: LyricScrollStyle = LyricScrollStyle.FLAT
) {
    val accent = LocalAccentColor.current
    val density = LocalDensity.current
    val textAlign = lyricTextAlign(alignment)
    val lineHeight = (fontSize * lineSpacing).sp
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val topPaddingPx = (viewportPx - lineHeightPx) / 2f
        val contentHeightPx = lines.size * lineHeightPx + 2f * topPaddingPx
        val maxScroll = (contentHeightPx - viewportPx).coerceAtLeast(0f)
        val topPaddingDp = with(density) { topPaddingPx.toDp() }

        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_lyrics), color = TextSecondary, fontSize = 15.sp)
            }
            return@BoxWithConstraints
        }

        val centerIndex = remember(lines, progressMs) { lines.currentLineIndex(progressMs) }

        // 自动滚动：把当前行居中
        LaunchedEffect(centerIndex, lineHeightPx, viewportPx, lines.size) {
            val target = (centerIndex * lineHeightPx - topPaddingPx).coerceIn(0f, maxScroll)
            scrollState.animateScrollTo(target.roundToInt())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(topPaddingDp))
            lines.forEachIndexed { index, line ->
                val distance = index - centerIndex
                val absDist = abs(distance)
                val isCurrent = absDist == 0
                // 只展示上下文（上一句 / 当前句 / 下一句），更远的句子隐藏，突出当前句
                val alpha = when {
                    isCurrent -> 1f
                    absDist == 1 -> 0.62f
                    else -> 0f
                }
                val scale = if (style == LyricScrollStyle.FISHEYE) fisheyeScale(absDist) else 1f
                Text(
                    text = line.text,
                    color = if (isCurrent) accent else TextSecondary,
                    fontSize = (if (isCurrent) fontSize else (fontSize - 1).coerceAtLeast(10)).sp,
                    lineHeight = lineHeight,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = textAlign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = if (isCurrent && shadowEnabled) TextStyle(
                        shadow = Shadow(color = accent.copy(alpha = 0.6f), blurRadius = 14f)
                    ) else TextStyle(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                )
            }
            Spacer(Modifier.height(topPaddingDp))
        }
    }
}

private fun lyricTextAlign(alignment: Int): TextAlign = when (alignment) {
    1 -> TextAlign.Left
    2 -> TextAlign.Right
    else -> TextAlign.Center
}

/** 鱼眼式缩放衰减：离中心越远越小，越靠近中轴越接近原尺寸，模拟圆柱滚动。 */
private fun fisheyeScale(distance: Int): Float {
    if (distance == 0) return 1f
    val t = (distance / 3f).coerceAtMost(1f)
    return cos(t * (PI.toFloat() / 2f) * 0.9f).coerceAtLeast(0.35f)
}

/**
 * 底部悬浮命令条（半透明磨砂质感）：
 * 折叠时只有歌名的小胶囊；点一下弹性展开成完整控制条（歌名+作者+进度+上一曲/播放/下一曲/更多），
 * 再点收起，展开后约 3 秒无操作自动收起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoliaCommandBar(
    title: String,
    artist: String?,
    isPlaying: Boolean,
    progress: Float,
    durationMs: Long,
    isFavorite: Boolean,
    loopMode: LoopMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onCycleLoopMode: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val loopIcon = when (loopMode) {
        LoopMode.NONE -> Icons.Filled.Stop
        LoopMode.SINGLE -> Icons.Filled.RepeatOne
        LoopMode.LIST -> Icons.Filled.Repeat
        LoopMode.SHUFFLE -> Icons.Filled.Shuffle
    }
    val loopLabel = stringResource(
        when (loopMode) {
            LoopMode.NONE -> R.string.settings_loop_none
            LoopMode.SINGLE -> R.string.settings_loop_single
            LoopMode.LIST -> R.string.settings_loop_list
            LoopMode.SHUFFLE -> R.string.settings_loop_shuffle
        }
    )
    var expanded by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var interactionTick by remember { mutableStateOf(0) }
    val shownValue = dragValue ?: progress.coerceIn(0f, 1f)

    LaunchedEffect(expanded, interactionTick) {
        if (expanded) {
            delay(3000)
            expanded = false
        }
    }

    val springSpec = spring<IntSize>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 磨砂玻璃：半透明渐变底 + 细高光描边，透过它看到已模糊的背景 → 磨砂质感
    Surface(
        modifier = modifier.animateContentSize(animationSpec = springSpec),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        DarkSurfaceVariant.copy(alpha = 0.55f),
                        DarkSurfaceVariant.copy(alpha = 0.32f)
                    )
                )
            )
        ) {
            Column {
                // 标题行始终显示：折叠时只有歌名，点按整条切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { interactionTick++; expanded = !expanded }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = if (expanded) 16.sp else 14.sp,
                        fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (expanded) "▾" else "▴", color = TextSecondary, fontSize = 12.sp)
                }
                // 展开区：弹性展开/收缩
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(animationSpec = springSpec) + fadeIn(),
                    exit = shrinkVertically(animationSpec = springSpec) + fadeOut()
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                        if (artist?.isNotBlank() == true) {
                            Text(
                                text = artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Slider(
                            value = shownValue,
                            onValueChange = { interactionTick++; dragValue = it },
                            onValueChangeFinished = {
                                interactionTick++
                                dragValue?.let { onSeek((it * durationMs).toLong()) }
                                dragValue = null
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = DarkSurfaceVariant.copy(alpha = 0.6f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(color = accent, shape = CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(3.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accent,
                                        activeTrackColor = accent,
                                        inactiveTrackColor = DarkSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { interactionTick++; onPrevious() }) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.play_prev_track), tint = TextPrimary)
                            }
                            IconButton(onClick = { interactionTick++; onPlayPause() }, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.play_pause) else stringResource(R.string.play_play),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            IconButton(onClick = { interactionTick++; onNext() }) {
                                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.play_next_track), tint = TextPrimary)
                            }
                            IconButton(onClick = { interactionTick++; onMore() }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more), tint = TextSecondary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { interactionTick++; onToggleFavorite() }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.library_remove_favorite else R.string.library_add_favorite),
                                    tint = if (isFavorite) accent else TextSecondary
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clickable { interactionTick++; onCycleLoopMode() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(loopIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(loopLabel, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
