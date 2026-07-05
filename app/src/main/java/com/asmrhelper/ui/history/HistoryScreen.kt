package com.asmrhelper.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlayAudio: (Audio) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
            }
            Text("播放历史", style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("清除全部", color = ErrorRed, fontSize = 13.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播放记录", color = TextHint, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val dateStr = remember(entry.playedAt) {
                        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        sdf.format(Date(entry.playedAt))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onPlayAudio(Audio(
                                    id = -entry.id - 1,
                                    title = entry.audioTitle,
                                    artist = entry.audioArtist,
                                    filePath = entry.filePath,
                                    durationMs = entry.durationMs
                                ))
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.audioTitle, color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.audioArtist.ifEmpty { "未知艺术家" } + " · $dateStr",
                                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.delete(entry.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "删除", tint = TextHint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除全部历史", color = TextPrimary) },
            text = { Text("确定要清除所有播放记录吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAll(); showClearDialog = false }) {
                    Text("清除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface, shape = RoundedCornerShape(16.dp)
        )
    }
}
