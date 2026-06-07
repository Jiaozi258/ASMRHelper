package com.asmrhelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.player.DownloadState
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

@Composable
fun DownloadDialog(
    initialUrl: String = "",
    downloadState: DownloadState,
    onStartDownload: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        if (url.isEmpty()) {
            val clipText = clipboardManager.getText()?.text
            if (clipText != null && clipText.startsWith("http")) {
                url = clipText
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloadState.isDownloading) onDismiss() },
        title = {
            Text(
                text = if (downloadState.isDownloading) "正在提取音频" else "提取视频音频",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!downloadState.isDownloading && downloadState.progress == 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                DarkSurfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
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

                if (downloadState.isDownloading || downloadState.progress > 0f) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = downloadState.statusText,
                        color = AccentPurple,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            if (!downloadState.isDownloading) {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) onStartDownload(url.trim())
                    },
                    enabled = url.isNotBlank()
                ) {
                    Text(
                        "提取音频",
                        color = if (url.isNotBlank()) AccentPurple else TextHint
                    )
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text("取消", color = ErrorRed)
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
