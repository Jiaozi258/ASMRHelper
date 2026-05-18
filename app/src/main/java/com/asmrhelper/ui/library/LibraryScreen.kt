package com.asmrhelper.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.ui.theme.AccentPurple
import com.asmrhelper.ui.theme.AccentPurpleVariant
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

/**
 * 音频库主页面 — 三标签页：
 * ① 全部音频  ② 我的收藏  ③ 文件管理
 *
 * @param onPlayAudio          点击播放回调，由导航层注入
 * @param currentPlayingAudioId 当前正在播放的音频 ID，用于高亮
 * @param onBack               返回回调
 * @param initialTabIndex      初始选中的标签页索引（0=全部音频, 1=我的收藏, 2=文件管理）
 */
@Composable
fun LibraryScreen(
    onPlayAudio: (Audio) -> Unit,
    currentPlayingAudioId: Long? = null,
    onBack: () -> Unit = {},
    initialTabIndex: Int = 0,
    modifier: Modifier = Modifier,
    onSetBackground: (Audio) -> Unit = {},
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val allAudio by viewModel.allAudio.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex) }
    val tabTitles = listOf("全部音频", "我的收藏", "文件管理")

    // Collect scan result messages
    LaunchedEffect(Unit) {
        viewModel.scanResult.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Permission launcher for audio scanning
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanAndImport()
        } else {
            Toast.makeText(context, "需要音频权限才能扫描本地音频文件", Toast.LENGTH_LONG).show()
        }
    }

    fun requestAudioPermissionAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                        == PackageManager.PERMISSION_GRANTED -> viewModel.scanAndImport()
                else -> permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED -> viewModel.scanAndImport()
                else -> permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            /* ──── 顶部标题栏 ──── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "音频库",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // 扫描按钮 + 加载指示器
                AnimatedVisibility(
                    visible = isScanning,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp),
                        color = AccentPurple,
                        strokeWidth = 2.dp
                    )
                }

                IconButton(
                    onClick = { requestAudioPermissionAndScan() },
                    enabled = !isScanning
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "扫描本地音频",
                        tint = if (isScanning) TextHint else AccentPurple
                    )
                }
            }

            /* ──── 标签栏 ──── */
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkSurface,
                contentColor = AccentPurple,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTabIndex == index) AccentPurple else TextSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            /* ──── 标签内容 ──── */
            when (selectedTabIndex) {
                0 -> AllAudioTab(
                    audios = allAudio,
                    currentPlayingAudioId = currentPlayingAudioId,
                    onPlayAudio = onPlayAudio,
                    onSetBackground = onSetBackground,
                    onToggleFavorite = { id, fav -> viewModel.toggleFavorite(id, fav) }
                )
                1 -> FavoritesTab(
                    favorites = favorites,
                    currentPlayingAudioId = currentPlayingAudioId,
                    onPlayAudio = onPlayAudio,
                    onSetBackground = onSetBackground,
                    onToggleFavorite = { id, fav -> viewModel.toggleFavorite(id, fav) }
                )
                2 -> FileBrowserScreen(
                    onPlayAudio = onPlayAudio,
                    onSetBackground = onSetBackground
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   标签页 ①：全部音频
   ═══════════════════════════════════════════════ */

@Composable
private fun AllAudioTab(
    audios: List<Audio>,
    currentPlayingAudioId: Long?,
    onPlayAudio: (Audio) -> Unit,
    onSetBackground: (Audio) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    if (audios.isEmpty()) {
        EmptyState(message = "暂无音频，请点击右上角扫描按钮")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(audios, key = { it.id }) { audio ->
                AudioItemCard(
                    audio = audio,
                    isCurrentlyPlaying = audio.id == currentPlayingAudioId,
                    onPlay = { onPlayAudio(audio) },
                    onSetBackground = { onSetBackground(audio) },
                    onToggleFavorite = { onToggleFavorite(audio.id, !audio.isFavorite) }
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   标签页 ②：我的收藏
   ═══════════════════════════════════════════════ */

@Composable
private fun FavoritesTab(
    favorites: List<Audio>,
    currentPlayingAudioId: Long?,
    onPlayAudio: (Audio) -> Unit,
    onSetBackground: (Audio) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyState(message = "暂无收藏")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(favorites, key = { it.id }) { audio ->
                AudioItemCard(
                    audio = audio,
                    isCurrentlyPlaying = audio.id == currentPlayingAudioId,
                    onPlay = { onPlayAudio(audio) },
                    onSetBackground = { onSetBackground(audio) },
                    onToggleFavorite = { onToggleFavorite(audio.id, !audio.isFavorite) }
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════
   通用组件
   ═══════════════════════════════════════════════ */

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextHint
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioItemCard(
    audio: Audio,
    isCurrentlyPlaying: Boolean,
    onPlay: () -> Unit,
    onSetBackground: () -> Unit = {},
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onPlay() },
                onLongClick = { onSetBackground() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：音乐图标
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isCurrentlyPlaying) AccentPurple else AccentPurpleVariant,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 中间：标题 + 艺术家
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentlyPlaying) AccentPurple else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = audio.artist.ifEmpty { "未知艺术家" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧：时长
            Text(
                text = formatDuration(audio.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextHint,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // 收藏切换按钮
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (audio.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (audio.isFavorite) "取消收藏" else "添加收藏",
                    tint = if (audio.isFavorite) ErrorRed else TextHint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 毫秒 → mm:ss */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
