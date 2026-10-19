package com.asmrhelper.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.R
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.ui.components.HypnosisBgType
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.ControlWhite
import com.asmrhelper.util.BatteryOptimizationHelper
import com.asmrhelper.util.GITHUB_URL
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary
import com.asmrhelper.ui.theme.ThemePreset
import com.asmrhelper.util.AppFont

// ── 循环模式显示名 ────────────────────────────────────────

@StringRes
private fun LoopMode.displayNameRes(): Int = when (this) {
    LoopMode.NONE    -> R.string.settings_loop_none
    LoopMode.SINGLE  -> R.string.settings_loop_single
    LoopMode.LIST    -> R.string.settings_loop_list
    LoopMode.SHUFFLE -> R.string.settings_loop_shuffle
}

// ── 设置分类 ─────────────────────────────────────────────

private enum class SettingsCategory(
    @StringRes val labelRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector
) {
    PERSONAL(R.string.category_personal, R.string.category_personal_sub, Icons.Filled.Lock),
    PLAYBACK(R.string.category_playback, R.string.category_playback_sub, Icons.Filled.Loop),
    EQUALIZER(R.string.category_equalizer, R.string.category_equalizer_sub, Icons.Filled.GraphicEq),
    APPEARANCE(R.string.category_appearance, R.string.category_appearance_sub, Icons.Filled.Palette),
    STORAGE(R.string.category_storage, R.string.category_storage_sub, Icons.Filled.Storage),
    AMBIENT(R.string.category_ambient, R.string.category_ambient_sub, Icons.Filled.MusicNote),
    LYRICS(R.string.category_lyrics, R.string.category_lyrics_sub, Icons.Filled.MusicNote),
    AUDIO_EFFECTS(R.string.category_audio_effects, R.string.category_audio_effects_sub, Icons.Filled.AutoAwesome),
    ABOUT(R.string.category_about, R.string.category_about_sub, Icons.Filled.Info),
}

// ── 入口 ──────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToMusicSources: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    when (category) {
        null -> SettingsHub(
            onOpen = { category = it },
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToMusicSources = onNavigateToMusicSources,
            onBack = onBack
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
    onNavigateToHistory: () -> Unit,
    onNavigateToMusicSources: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = TextPrimary)
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
                .padding(bottom = 24.dp)
        ) {
            SettingsCategory.entries.forEach { cat ->
                SettingsEntryRow(icon = cat.icon, titleRes = cat.labelRes, subtitleRes = cat.subtitleRes) { onOpen(cat) }
                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
            }
            SettingsEntryRow(icon = Icons.Filled.MusicNote, titleRes = R.string.music_source_settings_title, subtitleRes = R.string.music_source_settings_sub) { onNavigateToMusicSources() }
            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
            SettingsEntryRow(icon = Icons.Filled.Info, titleRes = R.string.history_title, subtitleRes = R.string.history_subtitle) { onNavigateToHistory() }
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
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = TextPrimary)
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
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Android 13+ requires runtime permission for notifications
    val notifyPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setShowNotification(true)
        } else {
            Toast.makeText(context, context.getString(R.string.settings_notif_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    SubScreenScaffold(title = stringResource(R.string.category_personal), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Lock, title = stringResource(R.string.settings_privacy_section))

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
                    Text(stringResource(R.string.settings_privacy_mode), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_privacy_mode_desc),
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

        SectionHeader(icon = Icons.Filled.Info, title = stringResource(R.string.settings_notif_lock_section))

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
                    Text(stringResource(R.string.settings_notif_control), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_notif_control_desc),
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
                                context.getString(R.string.settings_battery_find_hint),
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_open_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_background_keep_alive), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (isWhitelisted) stringResource(R.string.settings_battery_whitelisted)
                        else stringResource(R.string.settings_battery_not_whitelisted),
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
                    Text(stringResource(R.string.settings_lock_screen), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_lock_screen_desc),
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

        SectionHeader(icon = Icons.Filled.Translate, title = stringResource(R.string.language_title))

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
                    listOf(
                        stringResource(R.string.language_follow_system),
                        stringResource(R.string.language_zh),
                        stringResource(R.string.language_en)
                    ).forEachIndexed { index, label ->
                        val selected = index == appLanguage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) LocalAccentColor.current.copy(alpha = 0.15f)
                                    else DarkSurfaceVariant
                                )
                                .clickable {
                                    if (index != appLanguage) {
                                        viewModel.setAppLanguage(index)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) LocalAccentColor.current else TextHint,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
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

    SubScreenScaffold(title = stringResource(R.string.category_playback), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Loop, title = stringResource(R.string.category_playback))

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
                    Text(stringResource(R.string.settings_default_loop), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(loopMode.displayNameRes()),
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
                                        Text(stringResource(mode.displayNameRes()), color = TextPrimary)
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
                    Text(stringResource(R.string.settings_fade_out_mode), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (fadeOutMode == 1) stringResource(R.string.settings_fade_end) else stringResource(R.string.settings_fade_current),
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
                        listOf(stringResource(R.string.settings_fade_current), stringResource(R.string.settings_fade_end)).forEachIndexed { i, label ->
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
                    Text(stringResource(R.string.settings_remember_playback), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_remember_playback_desc),
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
                    Text(stringResource(R.string.settings_ambient_fade), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_ambient_fade_desc),
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
                    Text(stringResource(R.string.settings_bluetooth_stop), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_bluetooth_stop_desc), color = TextHint, fontSize = 12.sp)
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
                    Text(stringResource(R.string.settings_bluetooth_resume), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_bluetooth_resume_desc), color = TextHint, fontSize = 12.sp)
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
                    Text(stringResource(R.string.settings_pause_on_other), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_pause_on_other_desc), color = TextHint, fontSize = 12.sp)
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
                Text(stringResource(R.string.settings_seek_seconds, seekSeconds), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                    if (volumeFadeMs == 0) stringResource(R.string.settings_volume_fade_off) else stringResource(R.string.settings_volume_fade, volumeFadeMs),
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
                Text(stringResource(R.string.settings_volume_fade_desc), color = TextHint, fontSize = 12.sp)
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

    SubScreenScaffold(title = stringResource(R.string.category_equalizer), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.GraphicEq, title = stringResource(R.string.category_equalizer))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (!eqEnabled) {
                    Text(stringResource(R.string.settings_eq_not_ready), color = TextHint, fontSize = 13.sp)
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
                                Text(stringResource(preset.nameRes), color = if (selected) LocalAccentColor.current else TextSecondary, fontSize = 12.sp)
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
                            Text(stringResource(R.string.settings_eq_save_custom), color = LocalAccentColor.current, fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.resetEq() }) {
                            Text(stringResource(R.string.settings_eq_reset), color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    if (showSavePreset) {
                        var presetName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showSavePreset = false },
                            title = { Text(stringResource(R.string.settings_eq_save_custom_title), color = TextPrimary) },
                            text = {
                                androidx.compose.material3.OutlinedTextField(
                                    value = presetName,
                                    onValueChange = { presetName = it },
                                    placeholder = { Text(stringResource(R.string.settings_eq_preset_name), color = TextHint) },
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
                                ) { Text(stringResource(R.string.common_save), color = LocalAccentColor.current) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSavePreset = false }) { Text(stringResource(R.string.common_cancel), color = TextSecondary) }
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appFontPath by viewModel.appFontPath.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFontDialog by remember { mutableStateOf(false) }
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = AppFont.importFont(context, uri)
            if (imported != null) {
                viewModel.setAppFontPath(imported)
                Toast.makeText(context, R.string.settings_font_imported, Toast.LENGTH_SHORT).show()
                (context as? Activity)?.recreate()
            } else {
                Toast.makeText(context, R.string.settings_font_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SubScreenScaffold(title = stringResource(R.string.category_appearance), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Palette, title = stringResource(R.string.settings_appearance_section))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(stringResource(R.string.theme_mode), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.theme_mode_desc),
                    color = TextHint,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.theme_follow_system),
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_dark)
                    ).forEachIndexed { index, label ->
                        val selected = index == themeMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) LocalAccentColor.current.copy(alpha = 0.15f)
                                    else DarkSurfaceVariant
                                )
                                .clickable { viewModel.setThemeMode(index) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) LocalAccentColor.current else TextHint,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

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
                        Text(stringResource(R.string.settings_bg_solid), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(label), color = LocalAccentColor.current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.settings_theme_accent), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                                    stringResource(preset.labelRes),
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
                    .clickable { showFontDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentFont = appFontPath
                Column {
                    Text(stringResource(R.string.settings_font_type), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (currentFont == null) stringResource(R.string.settings_font_follow_system) else File(currentFont).name,
                        color = LocalAccentColor.current,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
            }
        }

    }

    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(stringResource(R.string.settings_font_type), color = TextPrimary) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.setAppFontPath(null)
                            showFontDialog = false
                            (context as? Activity)?.recreate()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_font_follow_system),
                            color = if (appFontPath == null) LocalAccentColor.current else TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = {
                            showFontDialog = false
                            fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_font_import),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFontDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        )
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

    SubScreenScaffold(title = stringResource(R.string.category_storage), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Storage, title = stringResource(R.string.settings_cache_section))

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
                    Text(stringResource(R.string.settings_clear_image_cache), color = TextPrimary, fontSize = 15.sp)
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
                    Text(stringResource(R.string.settings_clear_all_cache), color = TextPrimary, fontSize = 15.sp)
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
                Text(stringResource(R.string.settings_db_stats), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row {
                    Text(stringResource(R.string.settings_stat_audio, audioCount), color = LocalAccentColor.current, fontSize = 13.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_stat_playlist, playlistCount), color = LocalAccentColor.current, fontSize = 13.sp)
                }
            }
        }
    }

    // ── 确认对话框：清除图片缓存 ───────────────────────
    if (showClearImageDialog) {
        AlertDialog(
            onDismissRequest = { showClearImageDialog = false },
            title = { Text(stringResource(R.string.settings_clear_image_cache)) },
            text = { Text(stringResource(R.string.settings_clear_image_cache_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearImageCache { success ->
                            val msg = if (success) context.getString(R.string.settings_image_cache_cleared) else context.getString(R.string.settings_clear_failed)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        showClearImageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current)
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImageDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
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
            Toast.makeText(context, context.getString(R.string.settings_ambient_imported), Toast.LENGTH_SHORT).show()
        }
    }

    SubScreenScaffold(title = stringResource(R.string.category_ambient), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.MusicNote, title = stringResource(R.string.category_ambient))

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
                    Text(stringResource(R.string.settings_import_ambient), color = TextPrimary, fontSize = 15.sp)
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
                                Icons.Filled.Delete, stringResource(R.string.common_delete),
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
    val lyricsMode by viewModel.lyricsMode.collectAsStateWithLifecycle()
    val lyricsScrollStyle by viewModel.lyricsScrollStyle.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.category_lyrics), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.MusicNote, title = stringResource(R.string.category_lyrics))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                var modeExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { modeExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.lyric_mode), color = TextPrimary, fontSize = 14.sp)
                        Text(
                            if (lyricsMode == 1) stringResource(R.string.lyric_mode_scroll) else stringResource(R.string.lyric_mode_jump),
                            color = LocalAccentColor.current,
                            fontSize = 12.sp
                        )
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                            listOf(stringResource(R.string.lyric_mode_jump), stringResource(R.string.lyric_mode_scroll)).forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextPrimary) },
                                    onClick = { viewModel.setLyricsMode(i); modeExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                var scrollStyleExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scrollStyleExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.lyrics_scroll_style), color = TextPrimary, fontSize = 14.sp)
                        Text(
                            if (lyricsScrollStyle == 1) stringResource(R.string.lyrics_scroll_style_fisheye) else stringResource(R.string.lyrics_scroll_style_flat),
                            color = LocalAccentColor.current,
                            fontSize = 12.sp
                        )
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = scrollStyleExpanded, onDismissRequest = { scrollStyleExpanded = false }) {
                            listOf(stringResource(R.string.lyrics_scroll_style_flat), stringResource(R.string.lyrics_scroll_style_fisheye)).forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = { Text(label, color = TextPrimary) },
                                    onClick = { viewModel.setLyricsScrollStyle(i); scrollStyleExpanded = false }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                Text(stringResource(R.string.settings_lyrics_font_size, lyricsFontSize), color = TextPrimary, fontSize = 14.sp)
                Slider(
                    value = lyricsFontSize.toFloat(),
                    onValueChange = { viewModel.setLyricsFontSize(it.toInt()) },
                    valueRange = 10f..32f,
                    steps = 21,
                    colors = SliderDefaults.colors(thumbColor = LocalAccentColor.current, activeTrackColor = LocalAccentColor.current)
                )

                Text(stringResource(R.string.settings_lyrics_line_spacing, "%.1f".format(lyricsLineSpacing)), color = TextPrimary, fontSize = 14.sp)
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
                        Text(stringResource(R.string.settings_lyrics_shadow), color = TextPrimary, fontSize = 14.sp)
                        Text(stringResource(R.string.settings_lyrics_shadow_desc), color = TextHint, fontSize = 12.sp)
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
                        Text(stringResource(R.string.settings_lyrics_display_area), color = TextPrimary, fontSize = 14.sp)
                        Text(if (lyricsDisplayArea == 0) stringResource(R.string.settings_lyrics_area_top) else stringResource(R.string.settings_lyrics_area_full), color = LocalAccentColor.current, fontSize = 12.sp)
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = displayAreaExpanded, onDismissRequest = { displayAreaExpanded = false }) {
                            listOf(stringResource(R.string.settings_lyrics_area_top), stringResource(R.string.settings_lyrics_area_full)).forEachIndexed { i, label ->
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
                        Text(stringResource(R.string.settings_lyrics_alignment), color = TextPrimary, fontSize = 14.sp)
                        Text(
                            listOf(stringResource(R.string.settings_lyrics_align_center), stringResource(R.string.settings_lyrics_align_left), stringResource(R.string.settings_lyrics_align_right))
                                .getOrElse(lyricsAlignment) { stringResource(R.string.settings_lyrics_align_center) },
                            color = LocalAccentColor.current,
                            fontSize = 12.sp
                        )
                    }
                    Box {
                        Icon(Icons.Filled.ChevronRight, null, tint = TextHint)
                        DropdownMenu(expanded = alignmentExpanded, onDismissRequest = { alignmentExpanded = false }) {
                            listOf(stringResource(R.string.settings_lyrics_align_center), stringResource(R.string.settings_lyrics_align_left), stringResource(R.string.settings_lyrics_align_right)).forEachIndexed { i, label ->
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
//  8. 音频特效（可视化 + 音量触发 + 催眠）
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
    val hypnosisEnabled by viewModel.hypnosisModeEnabled.collectAsStateWithLifecycle()
    val hypnosisBgType by viewModel.hypnosisBgType.collectAsStateWithLifecycle()
    val playEffectsEnabled by viewModel.playEffectsEnabled.collectAsStateWithLifecycle()

    SubScreenScaffold(title = stringResource(R.string.category_audio_effects), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.GraphicEq, title = stringResource(R.string.settings_visualizer_section))

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
                    Text(stringResource(R.string.settings_enable_visualizer), color = if (visualizerEnabled) LocalAccentColor.current else TextPrimary, fontSize = 15.sp)
                }
                if (visualizerEnabled) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(20.dp))
            }
        }

        SectionSpacer()

        SectionHeader(icon = Icons.Filled.AutoAwesome, title = stringResource(R.string.settings_volume_trigger_section))

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
                    Text(stringResource(R.string.settings_enable_trigger), color = if (triggerEnabled) LocalAccentColor.current else TextPrimary, fontSize = 15.sp)
                }
                if (triggerEnabled) Icon(Icons.Filled.Check, null, tint = LocalAccentColor.current, modifier = Modifier.size(20.dp))
            }

            if (triggerEnabled) {
                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.settings_trigger_threshold, triggerThreshold), color = TextPrimary, fontSize = 14.sp)
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
                    Text(stringResource(R.string.settings_trigger_anim), color = TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    SettingsViewModel.volumeEffectOptions.forEachIndexed { index, nameRes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setVolumeTriggerAnimType(index) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (triggerAnimType == index) "● ${stringResource(nameRes)}" else "○ ${stringResource(nameRes)}",
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
                    Text(stringResource(R.string.settings_particle_color), color = TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsViewModel.triggerColorOptions.forEach { colorValue ->
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
                    Text(stringResource(R.string.settings_custom_emoji), color = TextPrimary, fontSize = 14.sp)
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
                                Text(stringResource(R.string.settings_emoji_hint), color = TextHint, fontSize = 14.sp)
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
                                Text(stringResource(R.string.common_clear), color = ErrorRed, fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.settings_particle_count, triggerParticleCount), color = TextPrimary, fontSize = 14.sp)
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
                    Text(stringResource(R.string.settings_trigger_cooldown, "%.1f".format(cooldownSec)), color = TextPrimary, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.settings_trigger_cooldown_desc),
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

        SectionHeader(icon = Icons.Filled.AutoAwesome, title = stringResource(R.string.settings_hypnosis_section))

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
                    Text(stringResource(R.string.settings_enable_hypnosis), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.settings_hypnosis_desc),
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
                    Text(stringResource(R.string.settings_hypnosis_bg), color = TextPrimary, fontSize = 14.sp)
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
                                text = if (isSelected) "● ${stringResource(bgType.labelRes)}" else "○ ${stringResource(bgType.labelRes)}",
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
                Text(stringResource(R.string.settings_play_effects), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.settings_play_effects_desc),
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
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    SubScreenScaffold(title = stringResource(R.string.category_about), onBack = onBack) {
        SectionHeader(icon = Icons.Filled.Info, title = stringResource(R.string.category_about))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            AboutRow(label = stringResource(R.string.settings_version_info), value = "ASMRHelper v1.0.0")

            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !checking) {
                        checking = true
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) { checkForUpdate(context) }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            checking = false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_check_update), color = TextPrimary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (checking) stringResource(R.string.settings_checking) else "v1.0.0", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

            AboutRow(label = stringResource(R.string.settings_license), value = "Kotlin (Apache 2.0)\nAndroid (Apache 2.0)\nMaterial3 (Apache 2.0)")

            HorizontalDivider(color = DarkSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_github), color = TextPrimary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("JiaoZi258/ASMRHelper", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = TextHint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 拉取 GitHub latest release 的 tag 与本地版本对比，返回展示给用户的提示文案。 */
private fun checkForUpdate(context: android.content.Context): String {
    return try {
        val conn = (URL("https://api.github.com/repos/JiaoZi258/ASMRHelper/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ASMRHelper")
        }
        val code = conn.responseCode
        if (code == 404) return context.getString(R.string.settings_update_none)
        if (code !in 200..299) return context.getString(R.string.settings_update_failed_http, code)
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)?.groupValues?.get(1)
            ?: return context.getString(R.string.settings_update_not_found)
        val latest = tag.trim().removePrefix("v")
        val current = (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0")
            .trim().removePrefix("v")
        if (compareVersions(latest, current) > 0) context.getString(R.string.settings_update_new, latest) else context.getString(R.string.settings_update_latest)
    } catch (e: Exception) {
        context.getString(R.string.settings_update_failed, e.message ?: context.getString(R.string.common_network_error))
    }
}

/** 简单语义化版本比较：a > b 返回正数，a == b 返回 0，a < b 返回负数。 */
private fun compareVersions(a: String, b: String): Int {
    val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}

// ── 辅助组件 ──────────────────────────────────────────────

@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
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
            Text(stringResource(titleRes), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(subtitleRes), color = TextHint, fontSize = 12.sp)
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
