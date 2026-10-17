package com.asmrhelper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.pressScale

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = if (isPlaying) "暂停" else "播放"
    val onClickAction = onClick
    Box(
        modifier = modifier
            .size(96.dp)
            .background(LocalAccentColor.current, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) { onClickAction(); true }
            }
            .pressScale { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = ControlWhite
        )
    }
}
