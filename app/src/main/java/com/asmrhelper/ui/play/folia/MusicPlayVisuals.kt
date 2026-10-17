package com.asmrhelper.ui.play.folia

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.util.Duotone
import com.asmrhelper.util.LrcLine
import com.asmrhelper.util.LyricMode
import com.asmrhelper.util.MusicVisualizerMath
import com.asmrhelper.util.currentLineIndex
import com.asmrhelper.util.scrollAnchorIndex

private const val VISIBLE_LINES = 9

/** 可视化风格：柱状频谱 / 波形。 */
enum class VisualizerStyle { BAR, WAVE }

/** 全屏双色调背景：模糊封面 + 由封面主色派生的双色渐变 + 缓慢缩放流动。 */
@Composable
fun CoverDuotoneBackground(coverUrl: String?, modifier: Modifier = Modifier) {
    val accent = LocalAccentColor.current
    val seed = rememberCoverDominantColor(coverUrl, accent.toArgb().toLong() and 0xFFFFFFFFL)
    val duotone = remember(seed) { Duotone.duotone(seed) }
    val primary = Color(duotone.primary.toInt())
    val secondary = Color(duotone.secondary.toInt())

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

    Box(modifier = modifier.clipToBounds().background(primary)) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .blur(40.dp)
                    .alpha(0.5f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.82f),
                            secondary.copy(alpha = 0.35f),
                            primary.copy(alpha = 0.6f)
                        )
                    )
                )
        )
    }
}

/** 音频可视化：按 [style] 绘制柱状频谱或波形，空数据仅显示一条怠速基线。 */
@Composable
fun MusicVisualizer(
    waveform: ByteArray?,
    fft: FloatArray?,
    style: VisualizerStyle,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val heights = remember(waveform, fft, style) {
        when (style) {
            VisualizerStyle.BAR -> MusicVisualizerMath.barsFromFft(fft, 24)
            VisualizerStyle.WAVE -> MusicVisualizerMath.barsFromWaveform(waveform, 32)
        }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        drawLine(
            color = accent.copy(alpha = 0.22f),
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 2f
        )
        when (style) {
            VisualizerStyle.BAR -> {
                val barWidth = w / heights.size.coerceAtLeast(1)
                heights.forEachIndexed { i, v ->
                    val bh = v * h * 0.9f
                    drawRoundRect(
                        color = accent.copy(alpha = 0.85f),
                        topLeft = Offset(i * barWidth + barWidth * 0.2f, mid - bh / 2f),
                        size = Size(barWidth * 0.6f, bh.coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(barWidth * 0.3f)
                    )
                }
            }
            VisualizerStyle.WAVE -> {
                if (heights.size > 1) {
                    val step = w / (heights.size - 1)
                    val path = Path()
                    heights.forEachIndexed { i, v ->
                        val x = i * step
                        val y = mid - v * h * 0.45f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = accent.copy(alpha = 0.9f), style = Stroke(width = 3f))
                }
            }
        }
    }
}

/** 居中歌词：当前行高亮放大发光，底部一行按钮在「跳转/滚动」间切换。 */
@Composable
fun CenteredLyrics(
    lines: List<LrcLine>,
    progressMs: Long,
    mode: LyricMode,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val listState = rememberLazyListState()
    val currentIndex = remember(lines, progressMs) { lines.currentLineIndex(progressMs) }

    LaunchedEffect(currentIndex, mode, lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        val anchor = scrollAnchorIndex(currentIndex, mode, VISIBLE_LINES)
        listState.animateScrollToItem(anchor.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (lines.isEmpty()) {
                Text("暂无歌词", color = TextSecondary, fontSize = 15.sp)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lines) { i, line ->
                        val isCurrent = i == currentIndex
                        Text(
                            text = line.text,
                            style = TextStyle(
                                color = if (isCurrent) accent else TextSecondary,
                                fontSize = if (isCurrent) 20.sp else 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                shadow = if (isCurrent) Shadow(color = accent.copy(alpha = 0.5f), blurRadius = 16f) else null
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp)
                        )
                    }
                }
            }
        }
        TextButton(onClick = onToggleMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (mode == LyricMode.JUMP) "跳转" else "滚动", color = TextHint, fontSize = 12.sp)
        }
    }
}

/** 底部悬浮控制条：进度 + 上一曲/播放暂停/下一曲 + 更多。 */
@Composable
fun FloatingControls(
    isPlaying: Boolean,
    progress: Float,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { onSeek((it * durationMs).toLong()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一曲", tint = TextPrimary)
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一曲", tint = TextPrimary)
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = TextSecondary)
                }
            }
        }
    }
}
