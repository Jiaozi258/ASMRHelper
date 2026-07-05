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
    progressMs: Long,
    viewModel: ImageSlideshowViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(progressMs) {
        viewModel.checkTimedAdvance(progressMs)
    }

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

    Column(modifier = modifier.fillMaxWidth()) {
        // Current image display
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
                    TextButton(onClick = { importLauncher.launch(arrayOf("image/*")) }) {
                        Text("+ 导入图片", color = AccentPurple)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Controls bar
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            // Mode chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("手动", state.mode == SlideshowMode.Manual) { viewModel.setMode(SlideshowMode.Manual) }
                ModeChip("自动", state.mode == SlideshowMode.Auto) { viewModel.setMode(SlideshowMode.Auto) }
                ModeChip("时间点", state.mode == SlideshowMode.Timed) { viewModel.setMode(SlideshowMode.Timed) }
                Spacer(Modifier.weight(1f))
                if (state.images.isNotEmpty()) {
                    IconButton(onClick = { importLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "导入", tint = AccentPurple, modifier = Modifier.size(20.dp))
                    }
                }
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
                            colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                    }
                }
                SlideshowMode.Timed -> {
                    Spacer(Modifier.height(4.dp))
                    // Show time points
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
                    TextButton(onClick = { viewModel.addTimePoint(progressMs) }) {
                        val sec = progressMs / 1000
                        Text("+ 当前时间 (${sec / 60}:${String.format(Locale.ROOT, "%02d", sec % 60)})",
                            color = AccentPurple, fontSize = 13.sp)
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
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (selected) AccentPurple.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) AccentPurple else TextSecondary,
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
