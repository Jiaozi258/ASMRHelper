package com.asmrhelper.ui.library

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 支持的音频文件扩展名 */
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac")

/** 配合 LibraryScreen 的“文件管理”标签页使用 */
private val DEFAULT_START_PATH: String by lazy {
    val emulated = File("/storage/emulated/0")
    if (emulated.exists() && emulated.isDirectory) emulated.absolutePath
    else Environment.getExternalStorageDirectory().absolutePath
}

/**
 * 文件系统浏览器 — 浏览本地目录，定位并播放音频文件。
 *
 * @param onPlayAudio  选中音频文件后回调，由上层负责导入数据库并开始播放
 */
@Composable
fun FileBrowserScreen(
    onPlayAudio: (Audio) -> Unit,
    modifier: Modifier = Modifier,
    onSetBackground: (Audio) -> Unit = {}
) {
    var currentDir by remember { mutableStateOf(File(DEFAULT_START_PATH)) }

    // 列出当前目录内容（切换目录时在后台线程执行）
    val entries by androidx.compose.runtime.produceState(emptyList<File>(), currentDir) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            value = currentDir.listFiles()
                ?.toList()
                ?.filter { it.isDirectory || it.extension.lowercase() in AUDIO_EXTENSIONS }
                ?.sortedWith(
                    compareBy<File> { !it.isDirectory }   // 目录优先
                        .thenBy { it.name.lowercase() }     // 再按名称字母序
                )
                ?: emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        /* ──── 路径导航栏 ──── */
        PathBreadcrumb(
            currentDir = currentDir,
            onNavigateTo = { dir -> currentDir = dir }
        )

        HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)

        /* ──── 父目录入口（返回上级） ──── */
        val parentFile = currentDir.parentFile
        if (parentFile != null) {
            ParentDirectoryRow(
                parentPath = parentFile.absolutePath,
                onClick = { currentDir = parentFile }
            )
            HorizontalDivider(
                color = DarkSurfaceVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        /* ──── 文件/目录列表 ──── */
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "此目录为空",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextHint
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries, key = { it.absolutePath }) { file ->
                    if (file.isDirectory) {
                        DirectoryItem(
                            file = file,
                            onClick = { currentDir = file }
                        )
                    } else {
                        AudioFileItem(
                            file = file,
                            onClick = {
                                val audio = Audio(
                                    title = file.nameWithoutExtension,
                                    artist = "",
                                    filePath = file.absolutePath,
                                    durationMs = 0L
                                )
                                onPlayAudio(audio)
                            },
                            onLongClick = {
                                val audio = Audio(
                                    title = file.nameWithoutExtension,
                                    artist = "",
                                    filePath = file.absolutePath,
                                    durationMs = 0L
                                )
                                onSetBackground(audio)
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   子组件
   ═══════════════════════════════════════════════ */

/** 顶部路径面包屑 — 可横向滚动，点击任意层级跳转 */
@Composable
private fun PathBreadcrumb(
    currentDir: File,
    onNavigateTo: (File) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮（回到上级目录，与面包屑逻辑一致）
        val parent = currentDir.parentFile
        IconButton(
            onClick = { parent?.let { onNavigateTo(it) } },
            enabled = parent != null,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回上级",
                tint = if (parent != null) TextSecondary else TextHint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 解析路径为层级列表
        val pathSegments = buildPathSegments(currentDir.absolutePath)

        pathSegments.forEachIndexed { index, (name, file) ->
            if (index > 0) {
                Text(
                    text = "  /  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHint
                )
            }
            val isLast = index == pathSegments.lastIndex
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isLast) LocalAccentColor.current else TextSecondary,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable(enabled = !isLast) { onNavigateTo(file) }
            )
        }
    }
}

/**
 * 将路径拆解为 (显示名, File) 的层级列表
 * 例如 /storage/emulated/0/Music → [(根目录, /), (storage, /storage), ...]
 */
private fun buildPathSegments(path: String): List<Pair<String, File>> {
    val segments = mutableListOf<Pair<String, File>>()
    val root = File("/")
    segments.add("根目录" to root)

    var current: File? = null
    for (part in path.trim('/').split("/")) {
        if (part.isEmpty()) continue
        current = File(current ?: root, part)
        segments.add(part to current!!)
    }

    // 如果路径很长，只保留前两级 + ... + 最后两级
    if (segments.size > 5) {
        val keep = mutableListOf<Pair<String, File>>()
        keep.add(segments.first())
        keep.add(segments[1])
        keep.add("..." to segments[2].second)   // 点击 "..." 跳转到第 3 级
        keep.add(segments[segments.size - 2])
        keep.add(segments.last())
        return keep
    }

    return segments
}

/** 跳转到上级目录条目 */
@Composable
private fun ParentDirectoryRow(
    parentPath: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = LocalAccentColor.current,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = ".. (上级目录)",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAccentColor.current,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 目录条目 */
@Composable
private fun DirectoryItem(
    file: File,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = LocalAccentColor.current,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(file.length())}  |  ${formatDate(file.lastModified())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHint
                )
            }
        }
    }
}

/** 音频文件条目 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFileItem(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = file.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalAccentColor.current
                    )
                    Text(
                        text = "  |  ${formatFileSize(file.length())}  |  ${formatDate(file.lastModified())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   格式化工具
   ═══════════════════════════════════════════════ */

/** 文件大小 → 可读字符串 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}

private val dateFormatter by lazy {
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
}

/** 时间戳 → 日期字符串 */
private fun formatDate(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    return dateFormatter.format(localDateTime)
}
