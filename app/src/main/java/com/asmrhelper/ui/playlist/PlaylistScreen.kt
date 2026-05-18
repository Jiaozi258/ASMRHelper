package com.asmrhelper.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.Playlist
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

@Composable
fun PlaylistScreen(
    onBack: () -> Unit = {},
    onPlayAudio: (Audio) -> Unit = {},
    onPlayAll: (List<Audio>) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val selectedPlaylistAudios by viewModel.selectedPlaylistAudios.collectAsStateWithLifecycle()
    val allAudio by viewModel.allAudio.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect import/export results
    LaunchedEffect(Unit) {
        viewModel.exportResult.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonStr = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                viewModel.importPlaylistFromJson(jsonStr)
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /* ──── Dialog state ──── */
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddAudioDialog by remember { mutableStateOf(false) }

    var newPlaylistName by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    var targetPlaylistId by remember { mutableStateOf<Long?>(null) }
    var targetPlaylistForAdd by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            /* ──── Top bar ──── */
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
                    text = "播放列表",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // Import button
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Icon(
                        imageVector = Icons.Filled.FileOpen,
                        contentDescription = "导入播放列表",
                        tint = AccentPurple
                    )
                }
            }

            /* ──── Empty or list ──── */
            if (playlists.isEmpty()) {
                EmptyPlaylistMessage()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            isExpanded = selectedPlaylistId == playlist.id,
                            audios = selectedPlaylistAudios,
                            onToggleExpand = { viewModel.selectPlaylist(playlist.id) },
                            onRenameRequest = {
                                targetPlaylistId = playlist.id
                                renameText = playlist.name
                                showRenameDialog = true
                            },
                            onDeleteRequest = {
                                targetPlaylistId = playlist.id
                                showDeleteDialog = true
                            },
                            onAddAudioRequest = {
                                targetPlaylistForAdd = playlist.id
                                showAddAudioDialog = true
                            },
                            onRemoveAudio = { audioId ->
                                viewModel.removeAudioFromPlaylist(playlist.id, audioId)
                            },
                            onExport = {
                                scope.launch {
                                    val json = viewModel.getExportJson(playlist.id) ?: return@launch
                                    val file = File(context.cacheDir, "${playlist.name}.json")
                                    file.writeText(json)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "导出播放列表"))
                                }
                            },
                            onPlayAll = {
                                if (selectedPlaylistAudios.isNotEmpty()) {
                                    onPlayAll(selectedPlaylistAudios)
                                }
                            }
                        )
                    }
                }
            }
        }

        /* ──── FAB ──── */
        FloatingActionButton(
            onClick = {
                newPlaylistName = ""
                showCreateDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = AccentPurple,
            contentColor = ControlWhite,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "创建播放列表"
            )
        }
    }

    /* ════════════════════════════════════════════
       Dialogs
       ════════════════════════════════════════════ */

    // ── Create playlist ──
    if (showCreateDialog) {
        RenameOrCreateDialog(
            title = "新建播放列表",
            initialText = newPlaylistName,
            onTextChange = { newPlaylistName = it },
            onConfirm = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                    newPlaylistName = ""
                }
            },
            onDismiss = { showCreateDialog = false },
            confirmLabel = "创建"
        )
    }

    // ── Rename playlist ──
    if (showRenameDialog) {
        RenameOrCreateDialog(
            title = "重命名播放列表",
            initialText = renameText,
            onTextChange = { renameText = it },
            onConfirm = {
                val name = renameText.trim()
                if (name.isNotEmpty()) {
                    targetPlaylistId?.let { viewModel.renamePlaylist(it, name) }
                    showRenameDialog = false
                    renameText = ""
                    targetPlaylistId = null
                }
            },
            onDismiss = {
                showRenameDialog = false
                renameText = ""
                targetPlaylistId = null
            },
            confirmLabel = "重命名"
        )
    }

    // ── Delete confirmation ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                targetPlaylistId = null
            },
            title = {
                Text(
                    text = "确认删除",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "删除后将无法恢复，确定要删除此播放列表吗？",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    targetPlaylistId?.let { viewModel.deletePlaylist(it) }
                    showDeleteDialog = false
                    targetPlaylistId = null
                }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    targetPlaylistId = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Add audio picker ──
    if (showAddAudioDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddAudioDialog = false
                targetPlaylistForAdd = null
            },
            title = {
                Text(
                    text = "添加音频",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                if (allAudio.isEmpty()) {
                    Text(
                        text = "暂无可用音频",
                        color = TextHint,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(allAudio, key = { it.id }) { audio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        targetPlaylistForAdd?.let { pid ->
                                            viewModel.addAudioToPlaylist(pid, audio.id)
                                            Toast.makeText(context, "已添加: ${audio.title}", Toast.LENGTH_SHORT).show()
                                        }
                                        showAddAudioDialog = false
                                        targetPlaylistForAdd = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = AccentPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = audio.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (audio.artist.isNotEmpty()) {
                                        Text(
                                            text = audio.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showAddAudioDialog = false
                    targetPlaylistForAdd = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/* ═══════════════════════════════════════════════
   Sub-composables
   ═══════════════════════════════════════════════ */

@Composable
private fun EmptyPlaylistMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无播放列表",
            style = MaterialTheme.typography.bodyLarge,
            color = TextHint
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isExpanded: Boolean,
    audios: List<Audio>,
    onToggleExpand: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onAddAudioRequest: () -> Unit,
    onRemoveAudio: (Long) -> Unit,
    onExport: () -> Unit = {},
    onPlayAll: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            /* ── Header row ── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${playlist.audioCount} 首音频",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onRenameRequest) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "导出",
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onDeleteRequest) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            /* ── Expanded audio list ── */
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    HorizontalDivider(
                        color = DarkSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 播放全部
                    if (audios.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAll() }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "▶ 播放全部",
                                style = MaterialTheme.typography.labelLarge,
                                color = ControlWhite
                            )
                        }
                    }

                    // Add audio entry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddAudioRequest() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加音频",
                            tint = AccentPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "添加音频",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentPurple
                        )
                    }

                    if (audios.isEmpty()) {
                        Text(
                            text = "播放列表为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextHint,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        audios.forEach { audio ->
                            AudioItemRow(
                                audio = audio,
                                onRemove = { onRemoveAudio(audio.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioItemRow(
    audio: Audio,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audio.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (audio.artist.isNotEmpty()) {
                Text(
                    text = audio.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "移除",
                tint = ErrorRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RenameOrCreateDialog(
    title: String,
    initialText: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    label = {
                        Text("名称", color = TextHint)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentPurple,
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedLabelColor = AccentPurple,
                        unfocusedLabelColor = TextHint
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = AccentPurple)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
