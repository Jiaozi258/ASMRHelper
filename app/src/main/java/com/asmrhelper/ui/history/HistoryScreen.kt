package com.asmrhelper.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.R
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.domain.model.Audio
import com.asmrhelper.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlayAudio: (Audio) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = TextPrimary)
            }
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text(stringResource(R.string.history_clear_all), color = ErrorRed, fontSize = 13.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = TextHint, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val dateStr = remember(entry.playedAt) {
                        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        sdf.format(Date(entry.playedAt))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onPlayAudio(Audio(
                                    id = 0L,
                                    title = entry.audioTitle,
                                    artist = entry.audioArtist,
                                    filePath = entry.filePath,
                                    durationMs = entry.durationMs
                                ))
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.audioTitle, color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.audioArtist.ifEmpty { stringResource(R.string.history_unknown_artist) } + " · $dateStr",
                                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.delete(entry.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.common_delete), tint = TextHint, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.history_clear_confirm), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAll(); showClearDialog = false }) {
                    Text(stringResource(R.string.common_clear), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = TextSecondary)
                }
            },
            containerColor = DarkSurface, shape = RoundedCornerShape(16.dp)
        )
    }
}
