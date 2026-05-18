package com.asmrhelper.ui.background

import android.net.Uri
import android.provider.OpenableColumns
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.BackgroundImage
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import java.io.File

@Composable
fun BackgroundGalleryScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BackgroundViewModel = hiltViewModel()
) {
    val images by viewModel.backgroundImages.collectAsStateWithLifecycle()
    val allAudio by viewModel.allAudio.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /* ──── Dialog state ──── */
    var showDeleteDialog by remember { mutableStateOf(false) }
    var targetImage by remember { mutableStateOf<BackgroundImage?>(null) }

    var showBindDialog by remember { mutableStateOf(false) }
    var selectedImageForBinding by remember { mutableStateOf<BackgroundImage?>(null) }
    var bindingStatuses by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }

    /* ──── Image picker ──── */
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val fileName = getFileNameFromUri(context, selectedUri)
                ?: "bg_${System.currentTimeMillis()}.jpg"
            val dir = File(context.filesDir, "backgrounds")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, fileName)
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    viewModel.addImage(fileName, destFile.absolutePath)
                    Toast.makeText(context, "背景图已添加", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "图片保存失败，请重试", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /* ──── Load binding statuses when bind dialog opens ──── */
    LaunchedEffect(selectedImageForBinding) {
        selectedImageForBinding?.let { image ->
            val map = mutableMapOf<Long, Boolean>()
            allAudio.forEach { audio ->
                val binding = viewModel.getBinding(audio.id)
                map[audio.id] = binding?.id == image.id
            }
            bindingStatuses = map
        }
    }

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
                    text = "背景图库",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // Symmetric spacer to balance the back button
                Spacer(modifier = Modifier.width(48.dp))
            }

            /* ──── Content ──── */
            if (images.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无背景图，点击 + 添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextHint
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        BackgroundImageCard(
                            image = image,
                            onDelete = {
                                targetImage = image
                                showDeleteDialog = true
                            },
                            onBind = {
                                selectedImageForBinding = image
                                bindingStatuses = emptyMap()
                                showBindDialog = true
                            },
                            onClick = {
                                viewModel.setCurrentBgImage(image.filePath)
                                Toast.makeText(context, "背景已应用: ${image.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        /* ──── FAB ──── */
        FloatingActionButton(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = AccentPurple,
            contentColor = ControlWhite,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加背景图"
            )
        }
    }

    /* ════════════════════════════════════════════
       Delete confirmation dialog
       ════════════════════════════════════════════ */
    if (showDeleteDialog && targetImage != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                targetImage = null
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
                    text = "删除后将无法恢复，确定要删除\"${targetImage!!.name}\"吗？",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    targetImage?.let { viewModel.deleteImage(it.id) }
                    showDeleteDialog = false
                    targetImage = null
                }) {
                    Text("删除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    targetImage = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    /* ════════════════════════════════════════════
       Bind dialog: long press to bind image to audio
       ════════════════════════════════════════════ */
    if (showBindDialog && selectedImageForBinding != null) {
        BindAudioDialog(
            allAudio = allAudio,
            image = selectedImageForBinding!!,
            bindingStatuses = bindingStatuses,
            onBind = { audioId ->
                selectedImageForBinding?.let { img ->
                    viewModel.bindAudioToImage(audioId, img.id)
                    bindingStatuses = bindingStatuses + (audioId to true)
                }
            },
            onUnbind = { audioId ->
                selectedImageForBinding?.let { img ->
                    viewModel.unbindAudioFromImage(audioId, img.id)
                    bindingStatuses = bindingStatuses + (audioId to false)
                }
            },
            onDismiss = {
                showBindDialog = false
                selectedImageForBinding = null
            }
        )
    }
}

/* ═══════════════════════════════════════════════
   Grid item card
   ═══════════════════════════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundImageCard(
    image: BackgroundImage,
    onDelete: () -> Unit,
    onBind: () -> Unit,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onBind
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Image thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(image.filePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = image.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Image name
                Text(
                    text = image.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            // Delete icon overlay (top-right corner)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(
                        color = DarkBackground.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除",
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   Bind audio dialog
   ═══════════════════════════════════════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BindAudioDialog(
    allAudio: List<Audio>,
    image: BackgroundImage,
    bindingStatuses: Map<Long, Boolean>,
    onBind: (Long) -> Unit,
    onUnbind: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "绑定音频到\"${image.name}\"",
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    allAudio.forEach { audio ->
                        val isBound = bindingStatuses[audio.id] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isBound) DarkSurfaceVariant.copy(alpha = 0.5f)
                                    else DarkBackground
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isBound) onUnbind(audio.id)
                                        else onBind(audio.id)
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isBound) {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = "已绑定",
                                    tint = AccentPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isBound) "已绑定" else "绑定",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isBound) AccentPurple else TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

/* ═══════════════════════════════════════════════
   Utility: get file name from URI
   ═══════════════════════════════════════════════ */

private fun getFileNameFromUri(
    context: android.content.Context,
    uri: Uri
): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                name = cursor.getString(idx)
            }
        }
    }
    return name
}
