package com.asmrhelper.ui.profile

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.asmrhelper.domain.music.MusicSource
import com.asmrhelper.domain.music.WebLoginConfig
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.util.Cookies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 在线音乐平台登录对话框：按音源声明的登录方式（账号密码 + WebView 网页登录）渲染。 */
@Composable
fun MusicLoginDialog(source: MusicSource, onDismiss: () -> Unit) {
    val web = source.webLogin
    var tab by remember { mutableIntStateOf(0) }
    val hasTabs = source.supportsAccountLogin && web != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(source.displayName, color = TextPrimary)
                if (hasTabs) {
                    Spacer(Modifier.height(12.dp))
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        contentColor = LocalAccentColor.current
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("账号密码") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("扫码登录") })
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text("网页登录", color = TextSecondary, fontSize = 13.sp)
                }
            }
        },
        text = {
            when {
                web == null -> Text("该音源暂不支持登录", color = TextSecondary)
                hasTabs && tab == 0 -> AccountLoginPanel(source, onDismiss)
                else -> QrWebLoginPanel(source, web, onDismiss)
            }
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
            label = { Text("手机号/邮箱") },
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
private fun QrWebLoginPanel(source: MusicSource, config: WebLoginConfig, onDone: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("正在加载登录页…") }

    // 轮询 WebView cookie，检测到任一鉴权键即完成登录。
    LaunchedEffect(config) {
        while (true) {
            val all = CookieManager.getInstance().getCookie(config.cookieDomain) ?: ""
            if (config.cookieKeys.any { all.contains(it) }) {
                source.importLogin(Cookies.select(all, config.cookieKeys), "")
                Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                onDone()
                break
            }
            delay(800)
        }
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            status = "请扫码或使用网页登录"
                        }
                    }
                    loadUrl(config.url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Spacer(Modifier.height(8.dp))
        Text(status, color = TextSecondary, fontSize = 13.sp)
    }
}
