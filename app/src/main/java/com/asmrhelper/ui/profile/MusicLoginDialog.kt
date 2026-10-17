package com.asmrhelper.ui.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.domain.model.music.QrLoginState
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.util.QrCodeRenderer
import kotlinx.coroutines.launch

/** 扫码登录「过期后应重建 key 重试」的单一事实点。 */
object QrLoginReducer {
    fun shouldRestart(state: QrLoginState): Boolean = state is QrLoginState.Expired
}

/** 在线音乐平台登录对话框：账号密码 + 扫码两种方式。 */
@Composable
fun MusicLoginDialog(source: MusicSource, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(source.displayName, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = LocalAccentColor.current
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("账号密码") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("扫码登录") })
                }
            }
        },
        text = {
            if (tab == 0) AccountLoginPanel(source, onDismiss)
            else QrLoginPanel(source, onDismiss)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        },
        dismissButton = {}
    )
}

@Composable
private fun AccountLoginPanel(source: MusicSource, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = LocalAccentColor.current,
        unfocusedBorderColor = DarkSurfaceVariant,
        focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
        cursorColor = LocalAccentColor.current,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        unfocusedLabelColor = TextHint,
        focusedLabelColor = LocalAccentColor.current
    )

    Column(modifier = Modifier.width(260.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("账号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (username.isBlank()) {
                    Toast.makeText(context, "请输入账号", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (password.isBlank()) {
                    Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    loading = true
                    source.loginAccount(username, password).collect { result ->
                        loading = false
                        result.onSuccess {
                            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                            onDone()
                        }.onFailure {
                            Toast.makeText(context, "登录失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (loading) "登录中…" else "登录")
        }
    }
}

@Composable
private fun QrLoginPanel(source: MusicSource, onDone: () -> Unit) {
    val context = LocalContext.current
    var qrState by remember { mutableStateOf<QrLoginState?>(null) }
    var restart by remember { mutableIntStateOf(0) }

    LaunchedEffect(restart) {
        source.loginQr().collect { state ->
            qrState = state
            when (state) {
                is QrLoginState.Confirmed -> {
                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                    onDone()
                }
                is QrLoginState.Expired -> {
                    if (QrLoginReducer.shouldRestart(state)) restart++
                }
                is QrLoginState.Failed ->
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    Column(
        modifier = Modifier.width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = qrState) {
            is QrLoginState.Waiting -> {
                val bitmap = remember(s.qrContent) { QrCodeRenderer.render(s.qrContent, 512) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(220.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("请使用 App 扫码登录", color = TextSecondary, fontSize = 13.sp)
            }
            is QrLoginState.Scanned -> {
                Spacer(Modifier.height(40.dp))
                Text("已扫码，请在手机上确认", color = LocalAccentColor.current, fontSize = 14.sp)
                Spacer(Modifier.height(40.dp))
            }
            is QrLoginState.Expired -> {
                Spacer(Modifier.height(40.dp))
                Text("二维码已过期，正在刷新…", color = TextHint, fontSize = 13.sp)
                Spacer(Modifier.height(40.dp))
            }
            is QrLoginState.Failed -> {
                Spacer(Modifier.height(40.dp))
                Text(s.message, color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(40.dp))
            }
            is QrLoginState.Confirmed -> {
                // 已在 LaunchedEffect 触发 onDone 关闭对话框，此处兜底占位
                Spacer(Modifier.height(40.dp))
                Text("登录成功", color = LocalAccentColor.current, fontSize = 14.sp)
                Spacer(Modifier.height(40.dp))
            }
            null -> {
                Spacer(Modifier.height(40.dp))
                Text("正在加载二维码…", color = TextHint, fontSize = 13.sp)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
