package com.asmrhelper.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.util.BatteryOptimizationHelper

/**
 * Battery optimization whitelist guidance dialog.
 *
 * On Chinese ROMs (MIUI, ColorOS, etc.), even a properly implemented foreground
 * service with WakeLock is killed ~10 minutes after screen-off unless the user
 * explicitly whitelists the app in battery optimization settings.
 *
 * This dialog is shown at most [MAX_SHOWS] times, and only when the app is not
 * yet whitelisted.
 */
@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isChineseRom = remember { BatteryOptimizationHelper.isChineseRom() }
    val romGuidance = remember { BatteryOptimizationHelper.getRomGuidance() }

    // Record that we've shown the dialog (once per composition, not per recomposition)
    LaunchedEffect(Unit) {
        BatteryOptimizationHelper.recordDialogShown(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "保持后台播放",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "为确保息屏或切换应用后音乐不会自动停止，需要将 ASMRHelper 加入电池优化白名单。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (isChineseRom && romGuidance != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "您使用的是国产手机系统，建议同时检查以下设置：",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = romGuidance,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                BatteryOptimizationHelper.openBatterySettings(context)
                onDismiss()
            }) {
                Text("去省电设置")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isChineseRom) {
                    // Additionally open app info for autostart/background permission
                    BatteryOptimizationHelper.openAppSettings(context)
                    Toast.makeText(
                        context,
                        "请在应用信息页中开启「自启动」和「后台运行」权限",
                        Toast.LENGTH_LONG
                    ).show()
                }
                onDismiss()
            }) {
                Text(if (isChineseRom) "应用详情 →" else "稍后再说")
            }
        }
    )
}
