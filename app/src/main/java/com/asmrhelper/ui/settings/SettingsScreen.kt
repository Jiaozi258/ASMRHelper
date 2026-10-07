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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.ui.components.HypnosisBgType
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.util.BatteryOptimizationHelper
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
    LoopMode.NONE    -> "播完即止"
    LoopMode.SINGLE  -> "单曲循环"
    LoopMode.LIST    -> "列表循环"
    LoopMode.SHUFFLE -> "随机播放"
}

// ── 设置分类 ─────────────────────────────────────────────

private enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector
) {
    PERSONAL("个人偏好", "隐私、通知与锁屏", Icons.Filled.Lock),
    PLAYBACK("播放设置", "循环、淡出、记忆播放等", Icons.Filled.Loop),
    EQUALIZER("均衡器", "10 段频段调节与预设", Icons.Filled.GraphicEq),
    APPEARANCE("外观与主题", "背景颜色与主题色彩", Icons.Filled.Palette),
    STORAGE("存储管理", "缓存清理与数据库统计", Icons.Filled.Storage),
    AMBIENT("环境音管理", "导入与管理环境音", Icons.Filled.MusicNote),
    LYRICS("歌词设置", "字体、行距、对齐与显示", Icons.Filled.MusicNote),
    AUDIO_EFFECTS("音频特效", "可视化、音量触发、催眠", Icons.Filled.AutoAwesome),
    ABOUT("关于", "版本信息与开源许可", Icons.Filled.Info),
}

// ── 入口 ──────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    when (category) {
        null -> SettingsHub(
            onOpen = { category = it },
            onNavigateToHistory = onNavigateToHistory
        )
        SettingsCategory.PERSONAL -> PersonalPreferenceScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.PLAYBACK -> PlaybackSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.EQUALIZER -> EqualizerSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.STORAGE -> StorageSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.AMBIENT -> AmbientSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.LYRICS -> LyricsSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.AUDIO_EFFECTS -> AudioEffectsSettingsScreen(onBack = { category = null }, viewModel = viewModel)
        SettingsCategory.ABOUT -> AboutSettingsScreen(onBack = { category = null })
    }
}

// ── 设置首页（分类入口） ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHub(
    onOpen: (SettingsCategory) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    Scaffold(
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
            SettingsCategory.entries.forEach { cat ->
                SettingsEntryRow(icon = cat.icon, title = cat.label, subtitle = cat.subtitle) { onOpen(cat) }
                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
            }
            SettingsEntryRow(icon = Icons.Filled.Info, title = "播放历史", subtitle = "查看播放记录") { onNavigateToHistory() }
        }
    }
}

// ── 子页面通用脚手架 ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
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
                .padding(bottom = 24.dp),
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  1. 个人偏好（隐私 + 通知与锁屏）
// ═══════════════════════════════════════════════════════════

@Composable
private fun PersonalPreferenceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val isPrivacyMode by viewModel.isPrivacyMode.collectAsStateWithLifecycle()
    val showNotification by viewModel.showNotification.collectAsStateWithLifecycle()
    val showOnLockScreen by viewModel.showOnLockScreen.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    SubScreenScaffold(title = "个人偏好", onBack = onBack) {
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

        SectionHeader(icon = Icons.Filled.Info, title = "通知与锁屏")

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
            val isWhitelisted = BatteryOptimizationHelper.isWhitelisted(context)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickable {
                        try {
                            BatteryOptimizationHelper.openBatterySettings(context)
                            Toast.makeText(
                                context,
                                "请在列表中找到 ASMRHelper 并关闭电池优化",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开设置: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("后台播放保活", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (isWhitelisted) "已关闭电池优化，后台播放受保护"
                        else "关闭电池优化可防止切后台或息屏后被系统强行停止播放",
                        color = if (isWhitelisted) Color(0xFF4CAF50) else TextHint,
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
    }
}

// ═══════════════════════════════════════════════════════════
//  2. 播放设置
// ═══════════════════════════════════════════════════════════

@Composable
private fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val loopMode by viewModel.loopMode.collectAsStateWithLifecycle()
    val fadeOutMode by viewModel.fadeOutMode.collectAsStateWithLifecycle()
    val rememberPlayback by viewModel.rememberPlayback.collectAsStateWithLifecycle()
    val ambientFade by viewModel.ambientFadeEnabled.collectAsStateWithLifecycle()
    val bluetoothStop by viewModel.bluetoothStopEnabled.collectAsStateWithLifecycle()
    val bluetoothResume by viewModel.bluetoothResumeEnabled.collectAsStateWithLifecycle()
    val pauseOnOther by viewModel.pauseOnOtherAudio.collectAsStateWithLifecycle()
    val seekSeconds by viewModel.seekTimeSeconds.collectAsStateWithLifecycle()
    val volumeFadeMs by viewModel.volumeFadeMs.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "播放设置", onBack = onBack) {
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
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextHint)
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            var fadeOutMenuExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fadeOutMenuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("淡出方式", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (fadeOutMode == 1) "歌曲/音频结尾时淡出" else "在当前位置淡出",
                        color = LocalAccentColor.current,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextHint)
                    DropdownMenu(
                        expanded = fadeOutMenuExpanded,
                        onDismissRequest = { fadeOutMenuExpanded = false }
                    ) {
                        listOf("在当前位置淡出", "歌曲/音频结尾时淡出").forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = TextPrimary)
                                        if (i == fadeOutMode) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setFadeOutMode(i)
                                    fadeOutMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text("记忆播放", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "开启后，下次打开将保留上次歌曲并定位到播放位置；关闭则每次打开为空白",
                        color = TextHint,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = rememberPlayback,
                    onCheckedChange = { viewModel.setRememberPlayback(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalAccentColor.current,
                        checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text("环境音渐入渐出", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "环境音开头 1 秒渐入、结尾 1 秒淡出，循环播放时过渡更平滑",
                        color = TextHint,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = ambientFade,
                    onCheckedChange = { viewModel.setAmbientFadeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LocalAccentColor.current,
                        checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text("断开设备停止播放", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("蓝牙耳机/扬声器断开时自动暂停", color = TextHint, fontSize = 12.sp)
                }
                Switch(
                    checked = bluetoothStop,
                    onCheckedChange = { viewModel.setBluetoothStopEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = LocalAccentColor.current, checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = DarkSurfaceVariant)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text("连接设备自动续播", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("连接耳机/外接设备时自动继续播放", color = TextHint, fontSize = 12.sp)
                }
                Switch(
                    checked = bluetoothResume,
                    onCheckedChange = { viewModel.setBluetoothResumeEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = LocalAccentColor.current, checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = DarkSurfaceVariant)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text("其他应用播放时暂停", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("其他应用播放音频/视频时本应用自动暂停", color = TextHint, fontSize = 12.sp)
                }
                Switch(
                    checked = pauseOnOther,
                    onCheckedChange = { viewModel.setPauseOnOtherAudio(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = LocalAccentColor.current, checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = DarkSurfaceVariant)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("快进/快退时间：${seekSeconds} 秒", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = seekSeconds.toFloat(),
                    onValueChange = { viewModel.setSeekTimeSeconds(it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 24,
                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    if (volumeFadeMs == 0) "渐变音量：关闭" else "渐变音量：${volumeFadeMs} ms",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = volumeFadeMs.toFloat(),
                    onValueChange = { viewModel.setVolumeFadeMs(it.toInt()) },
                    valueRange = 0f..3000f,
                    steps = 29,
                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                )
                Text("播放时渐强、暂停时渐弱音量的过渡时长", color = TextHint, fontSize = 12.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  3. 均衡器
// ═══════════════════════════════════════════════════════════

@Composable
private fun EqualizerSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
    val eqLevels by viewModel.eqBandLevels.collectAsStateWithLifecycle()
    val eqCurrentPreset by viewModel.eqCurrentPreset.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "均衡器", onBack = onBack) {
        SectionHeader(icon = Icons.Filled.GraphicEq, title = "均衡器")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (!eqEnabled) {
                    Text("均衡器未就绪，请先播放一首音频", color = TextHint, fontSize = 13.sp)
                } else {
                    // ── 预设选择 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.asmrhelper.player.EqualizerController.PRESETS.forEach { preset ->
                            val selected = eqCurrentPreset == preset.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .clickable { viewModel.applyEqPreset(preset) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(preset.name, color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 12.sp)
                            }
                        }
                        val custom = remember { viewModel.loadCustomEqPreset() }
                        if (custom != null) {
                            val selected = eqCurrentPreset == custom.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                    .clickable { viewModel.applyEqPreset(custom) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(custom.name, color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── 10 段频段调节 ──
                    val freqLabels = viewModel.eqBandFrequencies
                    eqLevels.forEachIndexed { i, level ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val freq = freqLabels.getOrElse(i) { 0 }
                            val freqLabel = if (freq >= 1000) "${freq / 1000}k" else "$freq"
                            Text(freqLabel, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                            Slider(
                                value = level,
                                onValueChange = { viewModel.setEqBand(i, it) },
                                valueRange = -10f..10f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = LocalAccentColor.current,
                                    activeTrackColor = LocalAccentColor.current
                                )
                            )
                            Text(
                                "${if (level >= 0) "+" else ""}${"%.0f".format(level)}",
                                color = if (level != 0f) LocalAccentColor.current else TextHint,
                                fontSize = 10.sp,
                                modifier = Modifier.width(34.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // ── 保存为自定义预设 + 重置 ──
                    var showSavePreset by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { showSavePreset = true }) {
                            Text("保存为自定义", color = LocalAccentColor.current, fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.resetEq() }) {
                            Text("重置", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    if (showSavePreset) {
                        var presetName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showSavePreset = false },
                            title = { Text("保存自定义预设", color = TextPrimary) },
                            text = {
                                androidx.compose.material3.OutlinedTextField(
                                    value = presetName,
                                    onValueChange = { presetName = it },
                                    placeholder = { Text("预设名称", color = TextHint) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (presetName.isNotBlank()) {
                                            viewModel.saveCustomEqPreset(presetName)
                                            showSavePreset = false
                                        }
                                    },
                                    enabled = presetName.isNotBlank()
                                ) { Text("保存", color = LocalAccentColor.current) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSavePreset = false }) { Text("取消", color = TextSecondary) }
                            },
                            containerColor = DarkSurface,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  4. 外观与主题
// ═══════════════════════════════════════════════════════════

@Composable
private fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val bgColorIndex by viewModel.bgColorIndex.collectAsStateWithLifecycle()
    val themePresetOrdinal by viewModel.themePresetOrdinal.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "外观与主题", onBack = onBack) {
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
    }
}

// ═══════════════════════════════════════════════════════════
//  5. 存储管理
// ═══════════════════════════════════════════════════════════

@Composable
private fun StorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val audioCount by viewModel.audioCount.collectAsStateWithLifecycle()
    val playlistCount by viewModel.playlistCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearImageDialog by remember { mutableStateOf(false) }

    SubScreenScaffold(title = "存储管理", onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Storage, title = "缓存管理")

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
                    Text("清除全部缓存", color = TextPrimary, fontSize = 15.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
            }

            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

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

// ═══════════════════════════════════════════════════════════
//  6. 环境音管理
// ═══════════════════════════════════════════════════════════

@Composable
private fun AmbientSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val ambientAudios by viewModel.ambientAudios.collectAsStateWithLifecycle()
    val selectedAmbient by viewModel.selectedAmbient.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    SubScreenScaffold(title = "环境音管理", onBack = onBack) {
        SectionHeader(icon = Icons.Filled.MusicNote, title = "环境音管理")

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
    }
}

// ═══════════════════════════════════════════════════════════
//  7. 歌词设置
// ═══════════════════════════════════════════════════════════

@Composable
private fun LyricsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val lyricsFontSize by viewModel.lyricsFontSize.collectAsStateWithLifecycle()
    val lyricsShadow by viewModel.lyricsShadowEnabled.collectAsStateWithLifecycle()
    val lyricsLineSpacing by viewModel.lyricsLineSpacing.collectAsStateWithLifecycle()
    val lyricsDisplayArea by viewModel.lyricsDisplayArea.collectAsStateWithLifecycle()
    val lyricsAlignment by viewModel.lyricsAlignment.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "歌词设置", onBack = onBack) {
        SectionHeader(icon = Icons.Filled.MusicNote, title = "歌词设置")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("字体大小：${lyricsFontSize} sp", color = TextPrimary, fontSize = 14.sp)
                Slider(
                    value = lyricsFontSize.toFloat(),
                    onValueChange = { viewModel.setLyricsFontSize(it.toInt()) },
                    valueRange = 10f..32f,
                    steps = 21,
                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                )

                Text("行间距：${"%.1f".format(lyricsLineSpacing)}x", color = TextPrimary, fontSize = 14.sp)
                Slider(
                    value = lyricsLineSpacing,
                    onValueChange = { viewModel.setLyricsLineSpacing(it) },
                    valueRange = 1f..3f,
                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                )

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("字体阴影", color = TextPrimary, fontSize = 14.sp)
                        Text("为歌词文字添加阴影，提升可读性", color = TextHint, fontSize = 12.sp)
                    }
                    Switch(
                        checked = lyricsShadow,
                        onCheckedChange = { viewModel.setLyricsShadowEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = LocalAccentColor.current, checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = DarkSurfaceVariant)
                    )
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                var displayAreaExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { displayAreaExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("显示区域", color = TextPrimary, fontSize = 14.sp)
                        Text(if (lyricsDisplayArea == 0) "上三分之一" else "全屏", color = LocalAccentColor.current, fontSize = 12.sp)
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = displayAreaExpanded, onDismissRequest = { displayAreaExpanded = false }) {
                            listOf("上三分之一", "全屏").forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextPrimary) },
                                    onClick = { viewModel.setLyricsDisplayArea(i); displayAreaExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                var alignmentExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alignmentExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("对齐方式", color = TextPrimary, fontSize = 14.sp)
                        Text(listOf("居中", "左对齐", "右对齐").getOrElse(lyricsAlignment) { "居中" }, color = LocalAccentColor.current, fontSize = 12.sp)
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = alignmentExpanded, onDismissRequest = { alignmentExpanded = false }) {
                            listOf("居中", "左对齐", "右对齐").forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextPrimary) },
                                    onClick = { viewModel.setLyricsAlignment(i); alignmentExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  8. 音频特效（可视化 + 音量触发 + 音量阈值 + 催眠）
// ═══════════════════════════════════════════════════════════

@Composable
private fun AudioEffectsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val visualizerEnabled by viewModel.audioVisualizerEnabled.collectAsStateWithLifecycle()
    val triggerEnabled by viewModel.volumeTriggerEnabled.collectAsStateWithLifecycle()
    val triggerThreshold by viewModel.volumeTriggerThreshold.collectAsStateWithLifecycle()
    val triggerColor by viewModel.volumeTriggerColor.collectAsStateWithLifecycle()
    val triggerEmoji by viewModel.volumeTriggerEmoji.collectAsStateWithLifecycle()
    val triggerAnimType by viewModel.volumeTriggerAnimType.collectAsStateWithLifecycle()
    val triggerParticleCount by viewModel.triggerParticleCount.collectAsStateWithLifecycle()
    val triggerCooldownMs by viewModel.triggerCooldownMs.collectAsStateWithLifecycle()
    val thresholdMode by viewModel.volumeThresholdMode.collectAsStateWithLifecycle()
    val minThresholdDb by viewModel.minThresholdDb.collectAsStateWithLifecycle()
    val maxThresholdDb by viewModel.maxThresholdDb.collectAsStateWithLifecycle()
    val hypnosisEnabled by viewModel.hypnosisModeEnabled.collectAsStateWithLifecycle()
    val hypnosisBgType by viewModel.hypnosisBgType.collectAsStateWithLifecycle()
    val playEffectsEnabled by viewModel.playEffectsEnabled.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "音频特效", onBack = onBack) {
        SectionHeader(icon = Icons.Filled.GraphicEq, title = "音频可视化")

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

        SectionHeader(icon = Icons.Filled.AutoAwesome, title = "音量触发特效")

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

        SectionHeader(icon = Icons.Filled.GraphicEq, title = "音量阈值")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("响度模式", "阈值模式").forEachIndexed { i, label ->
                        val selected = thresholdMode == i
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) LocalAccentColor.current.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .clickable { viewModel.setVolumeThresholdMode(i) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (thresholdMode == 0) {
                    Text("响度模式：通过 LoudnessEnhancer 调整目标响度（在音效器中调节）", color = TextHint, fontSize = 12.sp)
                } else {
                    Text("最大阈值：${maxThresholdDb} dB（限制过响部分）", color = TextPrimary, fontSize = 14.sp)
                    Slider(
                        value = maxThresholdDb.toFloat(),
                        onValueChange = { viewModel.setMaxThresholdDb(it.toInt()) },
                        valueRange = -40f..0f,
                        steps = 39,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                    Text("最小阈值：${minThresholdDb} dB（抑制过弱噪声）", color = TextPrimary, fontSize = 14.sp)
                    Slider(
                        value = minThresholdDb.toFloat(),
                        onValueChange = { viewModel.setMinThresholdDb(it.toInt()) },
                        valueRange = -60f..0f,
                        steps = 59,
                        colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                    )
                }
            }
        }

        SectionSpacer()

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
    }
}

// ═══════════════════════════════════════════════════════════
//  9. 关于
// ═══════════════════════════════════════════════════════════

@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SubScreenScaffold(title = "关于", onBack = onBack) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("检查更新", color = TextPrimary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("v1.0.0", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

            AboutRow(label = "开源许可", value = "Kotlin (Apache 2.0)\nAndroid (Apache 2.0)\nMaterial3 (Apache 2.0)")
        }
    }
}

// ── 辅助组件 ──────────────────────────────────────────────

@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = LocalAccentColor.current, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextHint, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
    }
}

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
