package com.asmrhelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.player.DownloadState
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.SuccessGreen
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

data class PlatformOption(
    val key: String,    // "bilibili" | "youtube" | "douyin"
    val label: String   // Display name
)

private val PLATFORMS = listOf(
    PlatformOption("auto", "自动"),
    PlatformOption("bilibili", "B站"),
    PlatformOption("youtube", "YouTube"),
    PlatformOption("douyin", "抖音"),
)

@Composable
fun DownloadDialog(
    initialUrl: String = "",
    downloadState: DownloadState,
    onStartDownload: (String, String?) -> Unit,   // (url, platform)
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var selectedPlatform by remember { mutableStateOf("auto") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        if (url.isEmpty()) {
            val clipText = clipboardManager.getText()?.text
            if (clipText != null && clipText.startsWith("http")) {
                url = clipText
                // Auto-detect platform from URL
                selectedPlatform = when {
                    "bilibili.com" in clipText || "b23.tv" in clipText -> "bilibili"
                    "youtube.com" in clipText || "youtu.be" in clipText -> "youtube"
                    "douyin.com" in clipText || "iesdouyin.com" in clipText -> "douyin"
                    else -> "auto"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloadState.isDownloading) onDismiss() },
        title = {
            Column {
                Text(
                    text = if (downloadState.isDownloading) "正在提取音频" else "提取视频音频",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(10.dp))

                // ── Platform selector ──
                if (!downloadState.isDownloading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PLATFORMS.forEach { platform ->
                            val isSelected = selectedPlatform == platform.key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) AccentPurple.copy(alpha = 0.2f)
                                        else DarkSurfaceVariant
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) AccentPurple else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedPlatform = platform.key }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = platform.label,
                                    color = if (isSelected) AccentPurple else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // URL input (hidden during download)
                if (!downloadState.isDownloading && downloadState.progress == 0f) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (url.isEmpty()) {
                            Text(
                                "粘贴视频链接 (B站/YouTube/抖音...)",
                                color = TextHint,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            singleLine = true
                        )
                    }
                }

                // Status / error / progress
                if (downloadState.statusText.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val isError = !downloadState.isDownloading && downloadState.progress == 0f
                    val isSuccess = downloadState.statusText == "提取完成"
                    Text(
                        text = downloadState.statusText,
                        color = when {
                            isSuccess -> SuccessGreen
                            isError -> ErrorRed
                            else -> AccentPurple
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (downloadState.isDownloading || downloadState.progress > 0f) {
                    if (downloadState.progress in 0.01f..0.99f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentPurple,
                            trackColor = DarkSurfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadState.isDownloading -> {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = ErrorRed)
                    }
                }
                downloadState.statusText == "提取完成" -> {
                    TextButton(onClick = onDismiss) {
                        Text("完成", color = SuccessGreen)
                    }
                }
                downloadState.statusText.isNotEmpty() && !downloadState.isDownloading -> {
                    // Error state — show retry + close
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = TextSecondary)
                        }
                        TextButton(
                            onClick = {
                                if (url.isNotBlank()) {
                                    val platformKey = if (selectedPlatform == "auto") null else selectedPlatform
                                    onStartDownload(url.trim(), platformKey)
                                }
                            },
                            enabled = url.isNotBlank()
                        ) {
                            Text("重试", color = if (url.isNotBlank()) AccentPurple else TextHint)
                        }
                    }
                }
                else -> {
                    TextButton(
                        onClick = {
                            if (url.isNotBlank()) {
                                val platformKey = if (selectedPlatform == "auto") null else selectedPlatform
                                onStartDownload(url.trim(), platformKey)
                            }
                        },
                        enabled = url.isNotBlank()
                    ) {
                        Text(
                            "提取音频",
                            color = if (url.isNotBlank()) AccentPurple else TextHint
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!downloadState.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
