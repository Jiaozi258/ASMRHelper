package com.asmrhelper.ui.play.folia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
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
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.util.Duotone
import com.asmrhelper.util.MusicVisualizerMath
import com.asmrhelper.util.WordLyricLine
import com.asmrhelper.util.currentLineIndex
import com.asmrhelper.util.evenWords
import com.asmrhelper.util.wordIndexAt
import kotlinx.coroutines.delay

/**
 * 全屏音频反应背景：双色调封面为底（真实提取主/次色），叠加随 FFT 频谱
 * 从底部向上流动的柔光柱，让整个画面跟随音频动。
 */
@Composable
fun AudioReactiveBackground(
    coverUrl: String?,
    waveform: ByteArray?,
    fft: FloatArray?,
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
            animation = tween(durationMillis = 9000, easing = LinearEasing),
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
 * 居中逐词歌词：单行显示，唱到哪个字哪个字高亮发光，不展示上下文。
 * 有逐词时间戳（yrc）按真实时刻推进；没有则把整行时长按字符均分。
 */
@Composable
fun CenteredLyrics(
    lines: List<WordLyricLine>,
    progressMs: Long,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccentColor.current
    val currentIndex = remember(lines, progressMs) { lines.currentLineIndex(progressMs) }
    val currentLine = lines.getOrNull(currentIndex)

    val glowPulse = if (lines.isNotEmpty()) {
        val transition = rememberInfiniteTransition(label = "lyricGlow")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowPulse"
        ).value
    } else 0f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            lines.isEmpty() -> Text(stringResource(R.string.no_lyrics), color = TextSecondary, fontSize = 15.sp)
            currentLine == null -> Text(stringResource(R.string.no_lyrics), color = TextSecondary, fontSize = 15.sp)
            else -> {
                val words = currentLine.words.ifEmpty { currentLine.evenWords() }
                if (words.isEmpty()) {
                    Text(
                        text = currentLine.text,
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                } else {
                    val wordIndex = words.wordIndexAt(progressMs)
                    val annotated = buildAnnotatedString {
                        words.forEachIndexed { i, w ->
                            val isCurrent = i == wordIndex
                            withStyle(
                                SpanStyle(
                                    color = if (isCurrent) accent else TextSecondary,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    shadow = if (isCurrent) Shadow(
                                        color = accent.copy(alpha = 0.6f * glowPulse),
                                        blurRadius = 16f * glowPulse
                                    ) else null
                                )
                            ) {
                                append(w.text)
                            }
                        }
                    }
                    Text(
                        text = annotated,
                        fontSize = 20.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

/**
 * 底部悬浮命令条（半透明磨砂质感）：
 * 折叠时只有歌名的小胶囊；点一下弹性展开成完整控制条（歌名+作者+进度+上一曲/播放/下一曲/更多），
 * 再点收起，展开后约 3 秒无操作自动收起。
 */
@Composable
fun FoliaCommandBar(
    title: String,
    artist: String?,
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
    var expanded by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shownValue = dragValue ?: progress.coerceIn(0f, 1f)

    LaunchedEffect(expanded) {
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
                        .clickable { expanded = !expanded }
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
                            onValueChange = { dragValue = it },
                            onValueChangeFinished = {
                                dragValue?.let { onSeek((it * durationMs).toLong()) }
                                dragValue = null
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = onPrevious) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.play_prev_track), tint = TextPrimary)
                            }
                            IconButton(onClick = onPlayPause, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.play_pause) else stringResource(R.string.play_play),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            IconButton(onClick = onNext) {
                                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.play_next_track), tint = TextPrimary)
                            }
                            IconButton(onClick = onMore) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more), tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
