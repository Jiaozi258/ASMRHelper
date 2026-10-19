package com.asmrhelper.ui.discover

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.asmrhelper.R
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.domain.model.music.RemoteChart
import com.asmrhelper.domain.model.music.RemotePlaylist
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

/** 发现页：搜索 + 四个标签页（推荐/每日/排行榜/歌单）+ 模块显示自定义。 */
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    onPlayRequested: () -> Unit = {},
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val dailyRecommend by viewModel.dailyRecommend.collectAsStateWithLifecycle()
    val radio by viewModel.radio.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val expandedPlaylistId by viewModel.expandedPlaylistId.collectAsStateWithLifecycle()
    val expandedPlaylistSongs by viewModel.expandedPlaylistSongs.collectAsStateWithLifecycle()
    val expandedChartId by viewModel.expandedChartId.collectAsStateWithLifecycle()
    val expandedChartSongs by viewModel.expandedChartSongs.collectAsStateWithLifecycle()
    val messageRes by viewModel.messageRes.collectAsStateWithLifecycle()
    val fetchingSongId by viewModel.fetchingSongId.collectAsStateWithLifecycle()
    val hiddenModules by viewModel.hiddenModules.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messageRes) {
        messageRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // 点击某首在线歌：当前可见列表整段成为播放队列
    val playSongs: (List<RemoteSong>, Int) -> Unit = { songs, index ->
        viewModel.playSongs(songs, index)
        onPlayRequested()
    }
    val playRecentEntries: (List<PlayHistoryEntity>, Int) -> Unit = { entries, index ->
        viewModel.playRecent(entries, index)
        onPlayRequested()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                searching = searching,
                onOpenSettings = { showSettingsDialog = true }
            )

            when {
                query.isNotBlank() -> SearchResults(
                    searching = searching,
                    results = searchResults,
                    onPlay = playSongs
                )
                else -> {
                    DiscoverTabRow(selectedTab) { selectedTab = it }
                    when (selectedTab) {
                        0 -> RecommendTab(recommendations, hiddenModules, playSongs)
                        1 -> DailyTab(dailyRecommend, radio, hiddenModules, playSongs)
                        2 -> ChartsTab(
                            charts = charts,
                            expandedChartId = expandedChartId,
                            expandedChartSongs = expandedChartSongs,
                            hiddenModules = hiddenModules,
                            onPlay = playSongs,
                            onToggleChart = viewModel::toggleChart
                        )
                        else -> PlaylistsTab(
                            userPlaylists = userPlaylists,
                            recent = recent,
                            expandedPlaylistId = expandedPlaylistId,
                            expandedPlaylistSongs = expandedPlaylistSongs,
                            hiddenModules = hiddenModules,
                            onPlay = playSongs,
                            onPlayRecent = playRecentEntries,
                            onTogglePlaylist = viewModel::toggleUserPlaylist
                        )
                    }
                }
            }
        }

        if (fetchingSongId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = LocalAccentColor.current,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.discover_fetching_url),
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        DiscoverSettingsDialog(
            hiddenModules = hiddenModules,
            onToggle = viewModel::toggleModule,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

private val discoverTabLabels = listOf(
    R.string.discover_tab_recommend,
    R.string.discover_tab_daily,
    R.string.discover_tab_charts,
    R.string.discover_tab_playlists
)

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.discover_search_hint), color = TextHint) },
            leadingIcon = {
                Icon(Icons.Filled.Search, null, tint = TextSecondary)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.common_clear), tint = TextSecondary)
                    }
                } else if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalAccentColor.current,
                        strokeWidth = 2.dp
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LocalAccentColor.current,
                unfocusedBorderColor = DarkSurfaceVariant,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                cursorColor = LocalAccentColor.current,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, stringResource(R.string.discover_settings_title), tint = TextSecondary)
        }
    }
}

@Composable
private fun DiscoverTabRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        discoverTabLabels.forEachIndexed { i, labelRes ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(labelRes),
                    color = if (selected) accent else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    searching: Boolean,
    results: List<RemoteSong>,
    onPlay: (List<RemoteSong>, Int) -> Unit
) {
    when {
        searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalAccentColor.current)
        }
        results.isEmpty() -> EmptyState(stringResource(R.string.discover_no_results))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(results, key = { _, it -> "${it.sourceId}:${it.id}" }) { index, song ->
                SongRow(song = song, onPlay = { onPlay(results, index) })
            }
        }
    }
}

/** 推荐标签：热门推荐。 */
@Composable
private fun RecommendTab(
    recommendations: List<RemoteSong>,
    hiddenModules: Set<String>,
    onPlay: (List<RemoteSong>, Int) -> Unit
) {
    if (DiscoverModule.RECOMMEND in hiddenModules || recommendations.isEmpty()) {
        EmptyTab()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(recommendations, key = { _, it -> "rec:${it.sourceId}:${it.id}" }) { index, song ->
            SongRow(song = song, onPlay = { onPlay(recommendations, index) })
        }
    }
}

/** 每日标签：每日推荐 + 电台，整段拼接成队列。 */
@Composable
private fun DailyTab(
    dailyRecommend: List<RemoteSong>,
    radio: List<RemoteSong>,
    hiddenModules: Set<String>,
    onPlay: (List<RemoteSong>, Int) -> Unit
) {
    val daily = if (DiscoverModule.DAILY in hiddenModules) emptyList() else dailyRecommend
    val radioList = if (DiscoverModule.RADIO in hiddenModules) emptyList() else radio
    val combined = daily + radioList
    if (combined.isEmpty()) {
        EmptyTab()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        if (daily.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_daily_recommend)) }
            itemsIndexed(daily, key = { _, it -> "daily:${it.sourceId}:${it.id}" }) { index, song ->
                SongRow(song = song, onPlay = { onPlay(combined, index) })
            }
        }
        if (radioList.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_radio)) }
            itemsIndexed(radioList, key = { _, it -> "radio:${it.sourceId}:${it.id}" }) { index, song ->
                SongRow(song = song, onPlay = { onPlay(combined, daily.size + index) })
            }
        }
    }
}

/** 排行榜标签：榜单可点开/收起。 */
@Composable
private fun ChartsTab(
    charts: List<RemoteChart>,
    expandedChartId: String?,
    expandedChartSongs: List<RemoteSong>,
    hiddenModules: Set<String>,
    onPlay: (List<RemoteSong>, Int) -> Unit,
    onToggleChart: (RemoteChart) -> Unit
) {
    if (DiscoverModule.CHARTS in hiddenModules || charts.isEmpty()) {
        EmptyTab()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        charts.forEach { chart ->
            val expanded = expandedChartId == chart.id
            item(key = "chart_header:${chart.id}") {
                ChartHeader(
                    name = chart.name,
                    expanded = expanded,
                    onClick = { onToggleChart(chart) }
                )
            }
            if (expanded) {
                itemsIndexed(expandedChartSongs, key = { _, it -> "chart:${chart.id}:${it.id}" }) { index, song ->
                    SongRow(song = song, onPlay = { onPlay(expandedChartSongs, index) })
                }
            }
        }
    }
}

/** 歌单标签：我的歌单（可折叠）+ 最近播放。 */
@Composable
private fun PlaylistsTab(
    userPlaylists: List<RemotePlaylist>,
    recent: List<PlayHistoryEntity>,
    expandedPlaylistId: String?,
    expandedPlaylistSongs: List<RemoteSong>,
    hiddenModules: Set<String>,
    onPlay: (List<RemoteSong>, Int) -> Unit,
    onPlayRecent: (List<PlayHistoryEntity>, Int) -> Unit,
    onTogglePlaylist: (RemotePlaylist) -> Unit
) {
    val playlists = if (DiscoverModule.PLAYLISTS in hiddenModules) emptyList() else userPlaylists
    val recentList = if (DiscoverModule.RECENT in hiddenModules) emptyList() else recent
    if (playlists.isEmpty() && recentList.isEmpty()) {
        EmptyTab()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        if (playlists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_user_playlists)) }
            playlists.forEach { playlist ->
                val expanded = expandedPlaylistId == playlist.id
                item(key = "pl_header:${playlist.id}") {
                    PlaylistRow(
                        name = playlist.name,
                        coverUrl = playlist.coverUrl,
                        expanded = expanded,
                        onClick = { onTogglePlaylist(playlist) }
                    )
                }
                if (expanded) {
                    itemsIndexed(expandedPlaylistSongs, key = { _, it -> "pl:${playlist.id}:${it.id}" }) { index, song ->
                        SongRow(song = song, onPlay = { onPlay(expandedPlaylistSongs, index) })
                    }
                }
            }
        }
        if (recentList.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_recent)) }
            itemsIndexed(recentList, key = { _, it -> "recent:${it.id}" }) { index, entry ->
                RecentRow(entry = entry, onPlay = { onPlayRecent(recentList, index) })
            }
        }
    }
}

@Composable
private fun DiscoverSettingsDialog(
    hiddenModules: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modules = listOf(
        DiscoverModule.RECOMMEND to R.string.discover_recommendations,
        DiscoverModule.DAILY to R.string.discover_daily_recommend,
        DiscoverModule.RADIO to R.string.discover_radio,
        DiscoverModule.CHARTS to R.string.discover_charts,
        DiscoverModule.PLAYLISTS to R.string.discover_user_playlists,
        DiscoverModule.RECENT to R.string.discover_recent
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discover_settings_title), color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                modules.forEach { (key, labelRes) ->
                    val visible = key !in hiddenModules
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(labelRes), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = visible,
                            onCheckedChange = { onToggle(key) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LocalAccentColor.current,
                                checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = TextSecondary) }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun EmptyTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.discover_empty_tab), color = TextHint, fontSize = 14.sp)
    }
}

@Composable
private fun ChartHeader(name: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "▾" else "▸",
            color = TextHint,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PlaylistRow(name: String, coverUrl: String?, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverUrl = coverUrl)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "▾" else "▸",
            color = TextHint,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SongRow(song: RemoteSong, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(coverUrl = song.coverUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artist.isNotBlank()) {
                Text(
                    song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (song.durationMs > 0) {
            Text(
                formatDuration(song.durationMs),
                color = TextHint,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RecentRow(entry: PlayHistoryEntity, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.audioTitle,
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.audioArtist.isNotBlank()) {
                Text(
                    entry.audioArtist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (entry.durationMs > 0) {
            Text(formatDuration(entry.durationMs), color = TextHint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Cover(coverUrl: String?) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNullOrBlank()) {
            Icon(Icons.Filled.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        } else {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Search,
                null,
                tint = TextHint,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(text, color = TextHint, fontSize = 14.sp)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
