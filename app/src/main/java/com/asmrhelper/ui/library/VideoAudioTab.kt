package com.asmrhelper.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

@Composable
fun VideoAudioTab(
    videoAudios: List<VideoAudio>,
    onPlayAudio: (VideoAudio) -> Unit,
    onDeleteAudio: (VideoAudio, Boolean) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var audioToDelete by remember { mutableStateOf<VideoAudio?>(null) }
    var deleteCacheFile by remember { mutableStateOf(false) }

    if (videoAudios.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "暂无视频音频，点击右下角 + 提取",
                color = TextHint,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(videoAudios, key = { it.id }) { videoAudio ->
                VideoAudioCard(
                    videoAudio = videoAudio,
                    onPlay = { onPlayAudio(videoAudio) },
                    onDelete = {
                        audioToDelete = videoAudio
                        deleteCacheFile = false
                        showDeleteDialog = true
                    },
                    onToggleFavorite = { onToggleFavorite(videoAudio.id, !videoAudio.isFavorite) }
                )
            }
        }
    }

    if (showDeleteDialog && audioToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                audioToDelete = null
            },
            title = {
                Text(
                    "确认删除",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        "确定要移除\"${audioToDelete!!.title}\"吗？",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteCacheFile = !deleteCacheFile }
                    ) {
                        Checkbox(
                            checked = deleteCacheFile,
                            onCheckedChange = { deleteCacheFile = it },
                            colors = CheckboxDefaults.colors(checkedColor = LocalAccentColor.current)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "同时删除本地缓存文件",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    audioToDelete?.let { onDeleteAudio(it, deleteCacheFile) }
                    showDeleteDialog = false
                    audioToDelete = null
                }) {
                    Text("移除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    audioToDelete = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoAudioCard(
    videoAudio: VideoAudio,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform indicator
            val platformIcon = when (videoAudio.platform) {
                "bilibili" -> "📺"
                "youtube" -> "▶️"
                "douyin" -> "🎵"
                else -> "🔗"
            }
            Text(platformIcon, fontSize = 22.sp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    videoAudio.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    videoAudio.sourceUrl.take(50),
                    color = TextHint,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoAudio.sourceUrl))
                        context.startActivity(intent)
                    }
                )
            }

            // Duration
            Text(
                formatDuration(videoAudio.durationMs),
                color = TextHint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Favorite
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (videoAudio.isFavorite) Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (videoAudio.isFavorite) ErrorRed else TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "移除",
                    tint = TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
