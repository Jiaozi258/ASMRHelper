package com.asmrhelper.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.ui.components.HypnosisBgType
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.ControlWhite
import java.io.File
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.ui.theme.ThemePreset

// ── 循环模式显示名 ────────────────────────────────────────

private fun LoopMode.displayName(): String = when (this) {
    LoopMode.NONE   -> "播完即止"
    LoopMode.SINGLE -> "单曲循环"
    LoopMode.LIST   -> "列表循环"
}

// ── 入口 ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isPrivacyMode by viewModel.isPrivacyMode.collectAsStateWithLifecycle()
    val loopMode by viewModel.loopMode.collectAsStateWithLifecycle()
    val bgColorIndex by viewModel.bgColorIndex.collectAsStateWithLifecycle()
    val themePresetOrdinal by viewModel.themePresetOrdinal.collectAsStateWithLifecycle()
    val audioCount by viewModel.audioCount.collectAsStateWithLifecycle()
    val playlistCount by viewModel.playlistCount.collectAsStateWithLifecycle()
    val hypnosisEnabled by viewModel.hypnosisModeEnabled.collectAsStateWithLifecycle()
    val hypnosisBgType by viewModel.hypnosisBgType.collectAsStateWithLifecycle()
    val playEffectsEnabled by viewModel.playEffectsEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showClearImageDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {

            // ═══ 隐私设置 ═══════════════════════════════════

            SectionHeader(icon = Icons.Filled.Lock, title = "隐私设置")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("隐私模式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "开启后，音频标题中间字符将显示为星号",
                            color = TextHint,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isPrivacyMode,
                        onCheckedChange = { viewModel.setPrivacyMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            SectionSpacer()

            // ═══ 通知与锁屏 ═══════════════════════════════════

            SectionHeader(icon = Icons.Filled.Info, title = "通知与锁屏")

            val showNotification by viewModel.showNotification.collectAsStateWithLifecycle()
            val showOnLockScreen by viewModel.showOnLockScreen.collectAsStateWithLifecycle()

            // Android 13+ requires runtime permission for notifications
            val notifyPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    viewModel.setShowNotification(true)
                } else {
                    Toast.makeText(context, "需要通知权限才能显示播放控件", Toast.LENGTH_SHORT).show()
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                // 下拉通知栏播放控件
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通知栏播放控件", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "开启后，下拉通知栏可显示当前播放音频并支持暂停、切歌",
                            color = TextHint,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = showNotification,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Android 13+ needs runtime notification permission
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS)
                                        != PackageManager.PERMISSION_GRANTED) {
                                        notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@Switch
                                    }
                                }
                                viewModel.setShowNotification(true)
                            } else {
                                viewModel.setShowNotification(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                // 后台播放保活（电池优化白名单）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台播放保活", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "关闭电池优化可防止切后台或息屏后被系统强行停止播放",
                            color = TextHint,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint, modifier = Modifier.size(20.dp))
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                // 锁屏界面展示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("锁屏界面展示", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "开启后，音频信息将在锁屏界面可见",
                            color = TextHint,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = showOnLockScreen,
                        onCheckedChange = { viewModel.setShowOnLockScreen(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            SectionSpacer()

            // ═══ 播放设置 ═══════════════════════════════════

            SectionHeader(icon = Icons.Filled.Loop, title = "播放设置")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                var loopMenuExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { loopMenuExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("默认循环模式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            loopMode.displayName(),
                            color = LocalAccentColor.current,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextHint
                        )
                        DropdownMenu(
                            expanded = loopMenuExpanded,
                            onDismissRequest = { loopMenuExpanded = false }
                        ) {
                            LoopMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(mode.displayName(), color = TextPrimary)
                                            if (mode == loopMode) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setLoopMode(mode)
                                        loopMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SectionSpacer()

            // ═══ 外观设置 ═══════════════════════════════════

            SectionHeader(icon = Icons.Filled.Palette, title = "外观设置")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                val (label, color) = SettingsViewModel.bgColorOptions[bgColorIndex]
                val animatedColor by animateColorAsState(targetValue = color, label = "bgColor")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.cycleBgColor() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(animatedColor)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("纯色背景", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(label, color = LocalAccentColor.current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                }
            }

            // 主题色预设
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("主题色彩", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreset.entries.forEach { preset ->
                            val isSelected = preset.ordinal == themePresetOrdinal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) preset.accent.copy(alpha = 0.15f)
                                        else DarkSurfaceVariant
                                    )
                                    .clickable { viewModel.setThemePresetOrdinal(preset.ordinal) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(preset.accent)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        preset.label,
                                        color = if (isSelected) preset.accent else TextHint,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionSpacer()

            // ═══ 缓存管理 ═══════════════════════════════════

            SectionHeader(icon = Icons.Filled.Storage, title = "缓存管理")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                // 清除图片缓存
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearImageDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, tint = ErrorRed.copy(alpha = 0.8f))
                        Spacer(Modifier.width(10.dp))
                        Text("清除图片缓存", color = TextPrimary, fontSize = 15.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                // 清除音频缓存
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.clearAudioCache { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, tint = ErrorRed.copy(alpha = 0.8f))
                        Spacer(Modifier.width(10.dp))
                        Text("清除音频缓存", color = TextPrimary, fontSize = 15.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                // 数据库统计
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("数据库统计", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Row {
                        Text("音频 ${audioCount}", color = LocalAccentColor.current, fontSize = 13.sp)
                        Spacer(Modifier.width(16.dp))
                        Text("播放列表 ${playlistCount}", color = LocalAccentColor.current, fontSize = 13.sp)
                    }
                }
            }

            SectionSpacer()

            // ═══ 环境音管理 ═══════════════════════════════════════

            SectionHeader(icon = Icons.Filled.MusicNote, title = "环境音管理")
            val ambientAudios by viewModel.ambientAudios.collectAsStateWithLifecycle()
            val selectedAmbient by viewModel.selectedAmbient.collectAsStateWithLifecycle()
            val ambientLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    val file = File(context.cacheDir, "ambient_${System.currentTimeMillis()}.audio")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    viewModel.addAmbientAudio(file.absolutePath)
                    Toast.makeText(context, "已导入环境音", Toast.LENGTH_SHORT).show()
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                // Import button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ambientLauncher.launch("audio/*") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = LocalAccentColor.current)
                        Spacer(Modifier.width(10.dp))
                        Text("导入环境音", color = TextPrimary, fontSize = 15.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                }

                // List imported files
                if (ambientAudios.isNotEmpty()) {
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                    ambientAudios.forEach { path ->
                        val name = File(path).name
                        val isSelected = selectedAmbient == path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectAmbient(if (isSelected) null else path)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) LocalAccentColor.current else TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(
                                    Icons.Filled.Delete, "删除",
                                    tint = ErrorRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp).clickable { viewModel.removeAmbientAudio(path) }
                                )
                            }
                        }
                        HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            SectionSpacer()

            // ═══ 音频可视化 ═══════════════════════════════════════

            SectionHeader(icon = Icons.Filled.GraphicEq, title = "音频可视化")
            val visualizerEnabled by viewModel.audioVisualizerEnabled.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAudioVisualizer(!visualizerEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.GraphicEq, null, tint = if (visualizerEnabled) LocalAccentColor.current else TextHint)
                        Spacer(Modifier.width(10.dp))
                        Text("启用音频可视化", color = if (visualizerEnabled) LocalAccentColor.current else TextPrimary, fontSize = 15.sp)
                    }
                    if (visualizerEnabled) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(20.dp))
                }
            }

            SectionSpacer()

            // ═══ 音量触发特效 ═══════════════════════════════════════

            SectionHeader(icon = Icons.Filled.AutoAwesome, title = "音量触发特效")
            val triggerEnabled by viewModel.volumeTriggerEnabled.collectAsStateWithLifecycle()
            val triggerThreshold by viewModel.volumeTriggerThreshold.collectAsStateWithLifecycle()
            val triggerColor by viewModel.volumeTriggerColor.collectAsStateWithLifecycle()
            val triggerEmoji by viewModel.volumeTriggerEmoji.collectAsStateWithLifecycle()
            val triggerAnimType by viewModel.volumeTriggerAnimType.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setVolumeTriggerEnabled(!triggerEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = if (triggerEnabled) LocalAccentColor.current else TextHint)
                        Spacer(Modifier.width(10.dp))
                        Text("启用音量触发特效", color = if (triggerEnabled) LocalAccentColor.current else TextPrimary, fontSize = 15.sp)
                    }
                    if (triggerEnabled) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(20.dp))
                }

                if (triggerEnabled) {
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Threshold setting
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("触发阈值: ${triggerThreshold}%", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = triggerThreshold.toFloat(),
                            onValueChange = { viewModel.setVolumeTriggerThreshold(it.toInt()) },
                            valueRange = 10f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = LocalAccentColor.current,
                                activeTrackColor = LocalAccentColor.current,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Animation type
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("特效动画类型", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        SettingsViewModel.volumeEffectOptions.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setVolumeTriggerAnimType(index) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (triggerAnimType == index) "● $name" else "○ $name",
                                    color = if (triggerAnimType == index) LocalAccentColor.current else TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Color selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("粒子颜色", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsViewModel.triggerColorOptions.forEach { (name, colorValue) ->
                                val isSelected = triggerColor == colorValue
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(colorValue))
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) ControlWhite else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { viewModel.setVolumeTriggerColor(colorValue) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Emoji input
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("自定义表情符号（在特效中显示）", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(DarkSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (triggerEmoji.isEmpty()) {
                                    Text("输入一个emoji（如 💖）", color = TextHint, fontSize = 14.sp)
                                }
                                BasicTextField(
                                    value = triggerEmoji,
                                    onValueChange = { newVal ->
                                        // Only accept a single emoji/character
                                        val filtered = if (newVal.length > 2) newVal.take(2) else newVal
                                        viewModel.setVolumeTriggerEmoji(filtered)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                    singleLine = true
                                )
                            }
                            if (triggerEmoji.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { viewModel.setVolumeTriggerEmoji("") }) {
                                    Text("清除", color = ErrorRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Particle count
                    val triggerParticleCount by viewModel.triggerParticleCount.collectAsStateWithLifecycle()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("粒子数量: ${triggerParticleCount}", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = triggerParticleCount.toFloat(),
                            onValueChange = { viewModel.setTriggerParticleCount(it.toInt()) },
                            valueRange = 4f..30f,
                            steps = 25,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = LocalAccentColor.current,
                                activeTrackColor = LocalAccentColor.current,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    // Cooldown interval
                    val triggerCooldownMs by viewModel.triggerCooldownMs.collectAsStateWithLifecycle()
                    val cooldownSec = triggerCooldownMs / 1000f
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("触发冷却间隔: %.1f秒".format(cooldownSec), color = TextPrimary, fontSize = 14.sp)
                        Text(
                            "两次特效触发之间的最短间隔，值越小特效越频繁",
                            color = TextHint,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = triggerCooldownMs.toFloat(),
                            onValueChange = { viewModel.setTriggerCooldownMs(it.toInt()) },
                            valueRange = 250f..5000f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = LocalAccentColor.current,
                                activeTrackColor = LocalAccentColor.current,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }

            SectionSpacer()

            // ═══ 催眠模式 ═══════════════════════════════════════

            SectionHeader(icon = Icons.Filled.AutoAwesome, title = "催眠模式")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setHypnosisModeEnabled(!hypnosisEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用催眠模式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "开启后播放界面只显示动态催眠背景，点击屏幕浮现UI 5秒",
                            color = TextHint,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = hypnosisEnabled,
                        onCheckedChange = { viewModel.setHypnosisModeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LocalAccentColor.current,
                            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                if (hypnosisEnabled) {
                    HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("动态背景选择", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        HypnosisBgType.entries.forEach { bgType ->
                            val isSelected = bgType.index == hypnosisBgType
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setHypnosisBgType(bgType.index) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "● ${bgType.label}" else "○ ${bgType.label}",
                                    color = if (isSelected) LocalAccentColor.current else TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 播放界面特效 toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("播放界面特效", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "浮动粒子和呼吸光晕效果",
                        color = TextHint,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = playEffectsEnabled,
                    onCheckedChange = {
                        viewModel.setPlayEffectsEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalAccentColor.current,
                        checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            SectionSpacer()

            // ═══ 播放历史 ═══════════════════════════════════

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHistory() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("播放历史", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                }
            }

            SectionSpacer()

            // ═══ 关于 ═══════════════════════════════════════

            SectionHeader(icon = Icons.Filled.Info, title = "关于")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                AboutRow(label = "版本信息", value = "ASMRHelper v1.0.0")

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                AboutRow(label = "开源许可", value = "Kotlin (Apache 2.0)\nAndroid (Apache 2.0)\nMaterial3 (Apache 2.0)")
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── 确认对话框：清除图片缓存 ───────────────────────
        if (showClearImageDialog) {
            AlertDialog(
                onDismissRequest = { showClearImageDialog = false },
                title = { Text("清除图片缓存") },
                text = { Text("确定要清除所有图片缓存吗？已缓存图片将被删除，下次加载时将重新下载。") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearImageCache { success ->
                                val msg = if (success) "图片缓存已清除" else "清除失败"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            showClearImageDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearImageDialog = false }) {
                        Text("取消", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }
    }
}

// ── 辅助组件 ──────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = LocalAccentColor.current, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Text(value, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}
