package com.asmrhelper.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asmrhelper.R
import com.asmrhelper.domain.model.PlaybackMode
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.pressScale

/**
 * 播放页左上角模式切换按钮：空心圆内一个小点。
 *
 * 两种模式共用同一个按钮（同位置、同颜色），图标随模式淡入淡出：
 * ASMR = 圆环 + 点，MUSIC = 空心圆。弹性按压（Apple 风格）。
 */
@Composable
fun ModeToggleButton(
    mode: PlaybackMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = if (mode == PlaybackMode.ASMR) stringResource(R.string.mode_toggle_to_music) else stringResource(R.string.mode_toggle_to_asmr)
    Box(
        modifier = modifier
            .size(34.dp)
            .background(DarkSurfaceVariant, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) { onToggle(); true }
            }
            .pressScale(onPress = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = mode, label = "mode_toggle") { target ->
            Icon(
                imageVector = if (target == PlaybackMode.ASMR)
                    Icons.Filled.RadioButtonChecked
                else
                    Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = LocalAccentColor.current,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
