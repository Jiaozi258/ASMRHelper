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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.asmrhelper.domain.model.music.RemoteSong
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

/** 发现页：搜索 / 热门推荐 / 排行榜 / 最近播放 + 在线流播放。 */
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
    val messageRes by viewModel.messageRes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(messageRes) {
        messageRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val playSong: (RemoteSong) -> Unit = { song ->
        viewModel.play(song)
        onPlayRequested()
    }
    val playRecentEntry: (PlayHistoryEntity) -> Unit = { entry ->
        viewModel.playRecent(entry)
        onPlayRequested()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        SearchBar(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            searching = searching
        )

        when {
            query.isNotBlank() -> SearchResults(
                searching = searching,
                results = searchResults,
                onPlay = playSong
            )
            else -> DiscoverHome(
                recommendations = recommendations,
                charts = charts,
                recent = recent,
                onPlay = playSong,
                onPlayRecent = playRecentEntry
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        Icon(Icons.Filled.Close, "清除", tint = TextSecondary)
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
    }
}

@Composable
private fun SearchResults(
    searching: Boolean,
    results: List<RemoteSong>,
    onPlay: (RemoteSong) -> Unit
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
            items(results, key = { "${it.sourceId}:${it.id}" }) { song ->
                SongRow(song = song, onPlay = { onPlay(song) })
            }
        }
    }
}

@Composable
private fun DiscoverHome(
    recommendations: List<RemoteSong>,
    charts: List<RemoteChart>,
    recent: List<PlayHistoryEntity>,
    onPlay: (RemoteSong) -> Unit,
    onPlayRecent: (PlayHistoryEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        if (recommendations.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_recommendations)) }
            items(recommendations, key = { "rec:${it.sourceId}:${it.id}" }) { song ->
                SongRow(song = song, onPlay = { onPlay(song) })
            }
        }

        if (charts.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_charts)) }
            charts.forEach { chart ->
                item { ChartHeader(chart.name) }
                items(chart.songs, key = { "chart:${chart.id}:${it.id}" }) { song ->
                    SongRow(song = song, onPlay = { onPlay(song) })
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_recent)) }
            items(recent, key = { "recent:${it.id}" }) { entry ->
                RecentRow(entry = entry, onPlay = { onPlayRecent(entry) })
            }
        }
    }
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
private fun ChartHeader(name: String) {
    Text(
        name,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
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
