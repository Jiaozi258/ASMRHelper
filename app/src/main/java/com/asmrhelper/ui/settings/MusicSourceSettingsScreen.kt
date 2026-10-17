package com.asmrhelper.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmrhelper.data.music.MusicSourceManager
import com.asmrhelper.domain.music.SourceConfig
import com.asmrhelper.ui.theme.DarkBackground
import com.asmrhelper.ui.theme.DarkSurface
import com.asmrhelper.ui.theme.DarkSurfaceVariant
import com.asmrhelper.ui.theme.ErrorRed
import com.asmrhelper.ui.theme.LocalAccentColor
import com.asmrhelper.ui.theme.TextHint
import com.asmrhelper.ui.theme.TextPrimary
import com.asmrhelper.ui.theme.TextSecondary

/** 音源设置：预设 + 自定义源列表、活动源切换、增删改地址。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val sources by viewModel.musicSources.collectAsStateWithLifecycle()
    val activeSourceId by viewModel.activeSourceId.collectAsStateWithLifecycle()

    var editingSource by remember { mutableStateOf<SourceConfig?>(null) }
    var deletingSource by remember { mutableStateOf<SourceConfig?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音源设置") },
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
                .padding(bottom = 24.dp)
        ) {
            Text(
                "选择一个音源作为在线音乐的来源，或为平台填上你的音源地址。",
                color = TextHint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column {
                    sources.forEachIndexed { index, config ->
                        SourceRow(
                            config = config,
                            isActive = config.id == activeSourceId,
                            onSelect = { viewModel.setActiveSource(config.id) },
                            onEdit = { editingSource = config },
                            onDelete = { deletingSource = config }
                        )
                        if (index != sources.lastIndex) {
                            HorizontalDivider(
                                color = DarkSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAccentColor.current),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加自定义音源")
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addCustomSource(name, url)
                showAddDialog = false
            }
        )
    }

    editingSource?.let { config ->
        EditSourceDialog(
            config = config,
            onDismiss = { editingSource = null },
            onConfirm = { updated ->
                viewModel.updateSource(updated)
                editingSource = null
            }
        )
    }

    deletingSource?.let { config ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            containerColor = DarkSurface,
            title = { Text("删除音源", color = TextPrimary) },
            text = { Text("确定删除自定义音源「${config.displayName}」？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSource(config.id)
                    deletingSource = null
                }) { Text("删除", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) { Text("取消", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun SourceRow(
    config: SourceConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (isActive) LocalAccentColor.current else TextHint,
                    shape = CircleShape
                )
                .then(
                    if (isActive) Modifier.background(LocalAccentColor.current, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(DarkSurface, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(config.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                config.baseUrl.ifBlank { "未配置音源地址" },
                color = if (config.baseUrl.isBlank()) TextHint else TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, "编辑", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        if (config.isCustom) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = ErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditSourceDialog(
    config: SourceConfig,
    onDismiss: () -> Unit,
    onConfirm: (SourceConfig) -> Unit
) {
    var url by remember { mutableStateOf(config.baseUrl) }
    val valid = MusicSourceManager.validateBaseUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("编辑 ${config.displayName}", color = TextPrimary) },
        text = {
            Column {
                Text("音源地址（http/https）", color = TextHint, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://api.example.com", color = TextHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (url.isNotBlank() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text("请输入有效的 http/https 地址", color = ErrorRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(config.copy(baseUrl = url.trim())) }
            ) { Text("确定", color = if (valid) LocalAccentColor.current else TextHint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && MusicSourceManager.validateBaseUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("添加自定义音源", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("音源地址") },
                    placeholder = { Text("https://api.example.com", color = TextHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (url.isNotBlank() && !MusicSourceManager.validateBaseUrl(url)) {
                    Spacer(Modifier.height(6.dp))
                    Text("请输入有效的 http/https 地址", color = ErrorRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name.trim(), url.trim()) }
            ) { Text("添加", color = if (valid) LocalAccentColor.current else TextHint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
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
