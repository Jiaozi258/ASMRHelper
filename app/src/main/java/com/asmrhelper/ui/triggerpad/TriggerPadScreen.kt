package com.asmrhelper.ui.triggerpad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.data.local.db.entity.TriggerPadEntity
import com.asmrhelper.ui.library.AudioFilePickerDialog
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.ui.theme.ErrorRed

@Composable
fun TriggerPadScreen(
    onBack: () -> Unit,
    viewModel: TriggerPadViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPickerForSlot by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary
                )
            }
            Text(
                text = "触发器面板",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        // Mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip(
                text = "独立模式",
                selected = state.mode == TriggerPadMode.Independent,
                onClick = {
                    if (state.mode != TriggerPadMode.Independent) viewModel.toggleMode()
                },
                modifier = Modifier.weight(1f)
            )
            ModeChip(
                text = "并行模式",
                selected = state.mode == TriggerPadMode.Parallel,
                onClick = {
                    if (state.mode != TriggerPadMode.Parallel) viewModel.toggleMode()
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3x3 grid
        val pads = state.pads
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(9) { index ->
                val pad = pads.find { it.slotIndex == index }
                val isActive = when (state.mode) {
                    TriggerPadMode.Independent -> state.playingIndex == index
                    TriggerPadMode.Parallel -> index in state.activeParallelSlots
                }
                PadButton(
                    index = index,
                    pad = pad,
                    isActive = isActive,
                    mode = state.mode,
                    onTap = {
                        if (pad != null) {
                            viewModel.playSlot(pad.filePath, index)
                        } else {
                            showPickerForSlot = index
                        }
                    },
                    onRemove = { viewModel.removeFromSlot(index) }
                )
            }
        }
    }

    // File picker dialog
    showPickerForSlot?.let { slot ->
        AudioFilePickerDialog(
            onDismiss = { showPickerForSlot = null },
            onFileSelected = { name, path ->
                viewModel.assignToSlot(slot, name, path)
                showPickerForSlot = null
            }
        )
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .border(
                width = 1.dp,
                color = if (selected) AccentPurple else DarkSurfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                color = if (selected) AccentPurple.copy(alpha = 0.15f) else DarkSurface,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) AccentPurple else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PadButton(
    index: Int,
    pad: TriggerPadEntity?,
    isActive: Boolean,
    mode: TriggerPadMode,
    onTap: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (pad != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onTap() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (mode == TriggerPadMode.Parallel && isActive) "🔊"
                               else if (isActive) "▶"
                               else "🔊",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = pad.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) AccentPurple else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                // Remove button overlay
                IconButton(
                    onClick = { onRemove() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Text("✕", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onTap() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加",
                        tint = TextHint,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )
                }
            }
        }
    }
}
