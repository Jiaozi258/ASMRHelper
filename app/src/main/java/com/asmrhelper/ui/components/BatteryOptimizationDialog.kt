package com.asmrhelper.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.R
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
    val romGuidance = remember { BatteryOptimizationHelper.getRomGuidance(context) }

    // Record that we've shown the dialog (once per composition, not per recomposition)
    LaunchedEffect(Unit) {
        BatteryOptimizationHelper.recordDialogShown(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.battery_dialog_title),
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
                    text = stringResource(R.string.battery_dialog_body),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (isChineseRom && romGuidance != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.battery_dialog_rom_hint),
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
                Text(stringResource(R.string.battery_dialog_action))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isChineseRom) {
                    // Additionally open app info for autostart/background permission
                    BatteryOptimizationHelper.openAppSettings(context)
                    Toast.makeText(
                        context,
                        context.getString(R.string.battery_dialog_app_info),
                        Toast.LENGTH_LONG
                    ).show()
                }
                onDismiss()
            }) {
                Text(if (isChineseRom) stringResource(R.string.battery_dialog_app_details) else stringResource(R.string.battery_dialog_later))
            }
        }
    )
}
