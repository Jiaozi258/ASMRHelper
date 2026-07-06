package com.asmrhelper.ui.slideshow

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.asmrhelper.ui.theme.*
import java.util.Locale

@Composable
fun ImageSlideshowContent(
    viewModel: ImageSlideshowViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentProgressMs by viewModel.progressMs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-advance timer
    LaunchedEffect(state.mode, state.autoIntervalSec) {
        if (state.mode == SlideshowMode.Auto && state.images.isNotEmpty()) {
            while (true) {
                kotlinx.coroutines.delay(state.autoIntervalSec * 1000L)
                viewModel.nextImage()
            }
        }
    }

    // SAF import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromUris(uris)
    }

    // Toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Dialogs
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var albumMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {

        // ── Album selector bar ───────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album dropdown
            Box {
                val currentAlbum = state.albums.find { it.id == state.selectedAlbumId }
                val label = currentAlbum?.name ?: "全部图片"
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { albumMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Folder, null, tint = LocalAccentColor.current, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, null, tint = TextHint, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = albumMenuExpanded, onDismissRequest = { albumMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("全部图片", color = TextPrimary) },
                        onClick = { viewModel.selectAlbum(0); albumMenuExpanded = false },
                        leadingIcon = { if (state.selectedAlbumId == 0L) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp)) }
                    )
                    HorizontalDivider(color = DarkSurfaceVariant)
                    state.albums.forEach { album ->
                        DropdownMenuItem(
                            text = { Text(album.name, color = TextPrimary) },
                            onClick = { viewModel.selectAlbum(album.id); albumMenuExpanded = false },
                            leadingIcon = { if (state.selectedAlbumId == album.id) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.deleteAlbum(album); albumMenuExpanded = false }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Delete, "删除合集", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Create album button
            IconButton(onClick = { showCreateAlbumDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.CreateNewFolder, "新建合集", tint = LocalAccentColor.current, modifier = Modifier.size(22.dp))
            }

            // Import button
            if (state.images.isNotEmpty()) {
                IconButton(onClick = { importLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Add, "导入图片", tint = LocalAccentColor.current, modifier = Modifier.size(22.dp))
                }
            }

            // Delete current image button
            if (state.images.isNotEmpty()) {
                IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, "删除当前图片", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Current image display ────────────────────────
        val currentImage = state.images.getOrNull(state.currentIndex)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (currentImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentImage.filePath)
                        .crossfade(300)
                        .build(),
                    contentDescription = "幻灯片图片",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Image, null, tint = TextHint, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(state.albums.find { it.id == state.selectedAlbumId }?.let { "「${it.name}」为空" } ?: "暂无图片", color = TextHint, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { importLauncher.launch(arrayOf("image/*")) }) {
                        Text("+ 导入图片", color = LocalAccentColor.current)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Controls bar ─────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            // Mode chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("手动", state.mode == SlideshowMode.Manual) { viewModel.setMode(SlideshowMode.Manual) }
                ModeChip("自动", state.mode == SlideshowMode.Auto) { viewModel.setMode(SlideshowMode.Auto) }
                ModeChip("时间点", state.mode == SlideshowMode.Timed) { viewModel.setMode(SlideshowMode.Timed) }
            }

            when (state.mode) {
                SlideshowMode.Auto -> {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("间隔: ${state.autoIntervalSec}秒", color = TextSecondary, fontSize = 13.sp)
                        Slider(value = state.autoIntervalSec.toFloat(),
                            onValueChange = { viewModel.setAutoInterval(it.toInt()) },
                            valueRange = 1f..60f, steps = 58,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current))
                    }
                }
                SlideshowMode.Timed -> {
                    Spacer(Modifier.height(4.dp))
                    if (state.timePoints.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.timePoints.size) { idx ->
                                val ms = state.timePoints[idx]
                                val sec = ms / 1000
                                val label = "${sec / 60}:${String.format(Locale.ROOT, "%02d", sec % 60)}"
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(DarkSurfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = TextPrimary, fontSize = 12.sp)
                                        IconButton(onClick = { viewModel.removeTimePoint(idx) },
                                            modifier = Modifier.size(18.dp)) {
                                            Icon(Icons.Filled.Close, null, tint = TextHint, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val ms = currentProgressMs
                    TextButton(onClick = { viewModel.addTimePoint(ms) }) {
                        val sec = ms / 1000
                        Text("+ 当前时间 (${sec / 60}:${String.format(Locale.ROOT, "%02d", sec % 60)})",
                            color = LocalAccentColor.current, fontSize = 13.sp)
                    }
                }
                else -> {}
            }

            // Navigation row
            if (state.images.size > 1) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { viewModel.prevImage() }) {
                        Icon(Icons.Filled.SkipPrevious, "上一张", tint = TextPrimary)
                    }
                    Text("${state.currentIndex + 1}/${state.images.size}",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp))
                    IconButton(onClick = { viewModel.nextImage() }) {
                        Icon(Icons.Filled.SkipNext, "下一张", tint = TextPrimary)
                    }
                }
            }
        }
    }

    // ── Create album dialog ──────────────────────────────
    if (showCreateAlbumDialog) {
        var albumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false },
            title = { Text("新建合集", color = TextPrimary) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (albumName.isEmpty()) {
                        Text("输入合集名称", color = TextHint, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (albumName.isNotBlank()) {
                            viewModel.createAlbum(albumName.trim())
                            showCreateAlbumDialog = false
                        }
                    },
                    enabled = albumName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Delete confirmation dialog ───────────────────────
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("删除图片", color = TextPrimary) },
            text = { Text("确定要删除当前图片吗？此操作不可撤销。", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentImage()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) LocalAccentColor.current else TextSecondary,
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
