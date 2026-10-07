package com.asmrhelper.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Apple 风格点按动画：按下时轻微缩小 + 弹性回弹，替代默认水波纹的「生硬」感。
 * 用法：`Modifier.appleClickable { ... }` 直接替换 `Modifier.clickable { ... }`。
 */
@Composable
fun Modifier.appleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appleClickScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * 灵动岛风格「正在播放」胶囊：默认收拢为一个小胶囊，
 * 切歌或恢复播放时横向展开显示标题，停留片刻后自动收起。
 */
@Composable
fun DynamicIsland(
    title: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var previousTitle by remember { mutableStateOf(title) }
    var previousPlaying by remember { mutableStateOf(isPlaying) }

    // 切歌或从暂停恢复播放时展开一次
    LaunchedEffect(title, isPlaying) {
        val titleChanged = title != null && title != previousTitle
        val resumed = isPlaying && !previousPlaying
        if (titleChanged || resumed) {
            expanded = true
            delay(2200)
            expanded = false
        }
        previousTitle = title
        previousPlaying = isPlaying
    }

    val width by animateDpAsState(
        targetValue = if (expanded) 232.dp else 46.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )
    val capsuleHeight = 32.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(capsuleHeight / 2),
        color = Color(0xE61C1C1E),
        shadowElevation = if (expanded) 6.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(capsuleHeight),
            contentAlignment = Alignment.Center
        ) {
            if (expanded && title != null) {
                Text(
                    text = if (isPlaying) "▶ $title" else "⏸ $title",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Color.White.copy(alpha = 0.55f), CircleShape)
                )
            }
        }
    }
}
