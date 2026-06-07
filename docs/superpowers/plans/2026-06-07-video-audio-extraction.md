# Video Audio Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract audio from video URLs (Douyin, Bilibili, YouTube) and play them in ASMRHelper as a separate "Video Audio" category.

**Architecture:** New VideoAudio system is fully independent from existing Audio system. A youtubedl-android wrapper extracts audio-only streams, Room persists metadata, and existing ExoPlayer handles playback. UI adds a 4th tab in the library.

**Tech Stack:** Room (KSP), Hilt, youtubedl-android (JitPack), Jetpack Compose, Media3 ExoPlayer

**Constraint:** Do NOT modify existing system behavior. Only add new files and insert new entries (tab, menu item, navigation, DI bindings) into existing files.

---

### Task 1: Add JitPack and youtubedl-android dependency

**Files:**
- Modify: `settings.gradle.kts:14`
- Modify: `gradle/libs.versions.toml:16-17`
- Modify: `app/build.gradle.kts:82-87`

- [ ] **Step 1: Add JitPack repository to settings.gradle.kts**

In the `dependencyResolutionManagement` block, add JitPack:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

- [ ] **Step 2: Add version and library entry to libs.versions.toml**

After the `coil` version line:
```toml
youtubedl = "0.19.0"
```

After the `coil-compose` library line:
```toml
youtubedl-android = { group = "com.github.yausername", name = "youtubedl-android", version.ref = "youtubedl" }
```

- [ ] **Step 3: Add dependency to app/build.gradle.kts**

After the Coil dependency line:
```kotlin
// Video audio extraction
implementation(libs.youtubedl.android)
```

- [ ] **Step 4: Sync and verify**

Run: `./gradlew build`  
Expected: No dependency resolution errors

---

### Task 2: Create VideoAudio domain model

**Files:**
- Create: `app/src/main/java/com/asmrhelper/domain/model/VideoAudio.kt`

- [ ] **Step 1: Create the domain model**

```kotlin
package com.asmrhelper.domain.model

data class VideoAudio(
    val id: Long = 0,
    val title: String,
    val platform: String = "other", // "bilibili" | "youtube" | "douyin" | "other"
    val sourceUrl: String,
    val filePath: String,
    val coverPath: String? = null,
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

### Task 3: Create Room entity and DAO

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/VideoAudioEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/VideoAudioDao.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt:1-45`

- [ ] **Step 1: Create VideoAudioEntity**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_audio")
data class VideoAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "platform") val platform: String = "other",
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "cover_path") val coverPath: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create VideoAudioDao**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmrhelper.data.local.db.entity.VideoAudioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoAudioDao {
    @Query("SELECT * FROM video_audio ORDER BY created_at DESC")
    fun getAll(): Flow<List<VideoAudioEntity>>

    @Query("SELECT * FROM video_audio WHERE is_favorite = 1 ORDER BY created_at DESC")
    fun getFavorites(): Flow<List<VideoAudioEntity>>

    @Query("SELECT * FROM video_audio WHERE id = :id")
    suspend fun getById(id: Long): VideoAudioEntity?

    @Query("SELECT * FROM video_audio WHERE source_url = :url LIMIT 1")
    suspend fun getBySourceUrl(url: String): VideoAudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VideoAudioEntity): Long

    @Update
    suspend fun update(entity: VideoAudioEntity)

    @Query("DELETE FROM video_audio WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 3: Update AsmrDatabase — add entity and DAO**

Add `VideoAudioEntity::class` to the `entities` list in `@Database`.  
Add `abstract fun videoAudioDao(): VideoAudioDao`.  
Bump `version` to 4 and add migration:

```kotlin
@Database(
    entities = [
        AudioEntity::class, PlaylistEntity::class, PlaylistAudioCrossRef::class,
        BackgroundImageEntity::class, AudioBgBinding::class,
        TriggerPadEntity::class, SleepJournalEntity::class,
        SceneEntity::class, BookmarkEntity::class,
        VideoAudioEntity::class  // <-- NEW
    ],
    version = 4,  // <-- bumped from 3
    exportSchema = false
)
abstract class AsmrDatabase : RoomDatabase() {
    // ... existing DAOs ...
    abstract fun videoAudioDao(): VideoAudioDao  // <-- NEW
}
```

- [ ] **Step 4: Add migration 3→4 in DatabaseModule.kt**

```kotlin
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS video_audio (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                platform TEXT NOT NULL DEFAULT 'other',
                source_url TEXT NOT NULL,
                file_path TEXT NOT NULL,
                cover_path TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                file_size_bytes INTEGER NOT NULL DEFAULT 0,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
```

Add `MIGRATION_3_4` to the `.addMigrations(...)` call.  
Add `provideVideoAudioDao()` provider method.

---

### Task 4: Create mapper, repository interface, and implementation

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/data/mapper/EntityMappers.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/VideoAudioRepository.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/VideoAudioRepositoryImpl.kt`
- Modify: `app/src/main/java/com/asmrhelper/di/RepositoryModule.kt`

- [ ] **Step 1: Add mapper functions to EntityMappers.kt**

```kotlin
import com.asmrhelper.data.local.db.entity.VideoAudioEntity
import com.asmrhelper.domain.model.VideoAudio

fun VideoAudioEntity.toDomain() = VideoAudio(
    id = id, title = title, platform = platform,
    sourceUrl = sourceUrl, filePath = filePath,
    coverPath = coverPath, durationMs = durationMs,
    fileSizeBytes = fileSizeBytes, isFavorite = isFavorite,
    createdAt = createdAt
)

fun VideoAudio.toEntity() = VideoAudioEntity(
    id = id, title = title, platform = platform,
    sourceUrl = sourceUrl, filePath = filePath,
    coverPath = coverPath, durationMs = durationMs,
    fileSizeBytes = fileSizeBytes, isFavorite = isFavorite,
    createdAt = createdAt
)
```

- [ ] **Step 2: Create repository interface**

```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.domain.model.VideoAudio
import kotlinx.coroutines.flow.Flow

interface VideoAudioRepository {
    fun getAll(): Flow<List<VideoAudio>>
    fun getFavorites(): Flow<List<VideoAudio>>
    suspend fun getById(id: Long): VideoAudio?
    suspend fun getBySourceUrl(url: String): VideoAudio?
    suspend fun insert(videoAudio: VideoAudio): Long
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 3: Create repository implementation**

```kotlin
package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.VideoAudioDao
import com.asmrhelper.data.mapper.toDomain
import com.asmrhelper.data.mapper.toEntity
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoAudioRepositoryImpl @Inject constructor(
    private val dao: VideoAudioDao
) : VideoAudioRepository {

    override fun getAll(): Flow<List<VideoAudio>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<VideoAudio>> =
        dao.getFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): VideoAudio? =
        dao.getById(id)?.toDomain()

    override suspend fun getBySourceUrl(url: String): VideoAudio? =
        dao.getBySourceUrl(url)?.toDomain()

    override suspend fun insert(videoAudio: VideoAudio): Long =
        dao.insert(videoAudio.toEntity())

    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(isFavorite = isFavorite)) }
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
```

- [ ] **Step 4: Bind repository in RepositoryModule.kt**

```kotlin
@Binds @Singleton
abstract fun bindVideoAudioRepository(impl: VideoAudioRepositoryImpl): VideoAudioRepository
```

---

### Task 5: Create VideoAudioExtractor (yt-dlp wrapper)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/player/VideoAudioExtractor.kt`

- [ ] **Step 1: Create the extractor**

```kotlin
package com.asmrhelper.player

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class VideoInfo(
    val title: String,
    val platform: String,
    val durationMs: Long
)

data class ExtractionResult(
    val title: String,
    val platform: String,
    val sourceUrl: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long
)

@Singleton
class VideoAudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
            initialized = true
        }
    }

    suspend fun extractInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val info = YoutubeDL.getInstance().getInfo(url)
            VideoInfo(
                title = info.title ?: "未知视频",
                platform = detectPlatform(url),
                durationMs = (info.duration ?: 0) * 1000
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractAudio(
        url: String,
        onProgress: (Float) -> Unit = {}
    ): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "video_audio")
            if (!dir.exists()) dir.mkdirs()

            val info = extractInfo(url) ?: return@withContext null
            val safeTitle = info.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80)
            val outputPath = File(dir, "${System.currentTimeMillis()}_$safeTitle").absolutePath

            val request = YoutubeDLRequest(url).apply {
                addOption("-x")
                addOption("--audio-format", "m4a")
                addOption("--audio-quality", "0")
                addOption("-o", "$outputPath.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-continue")
            }

            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress / 100f)
            }

            val outputFile = File("$outputPath.m4a")
            if (outputFile.exists()) {
                ExtractionResult(
                    title = info.title,
                    platform = info.platform,
                    sourceUrl = url,
                    filePath = outputFile.absolutePath,
                    durationMs = info.durationMs,
                    fileSizeBytes = outputFile.length()
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun detectPlatform(url: String): String = when {
        "bilibili.com" in url || "b23.tv" in url -> "bilibili"
        "youtube.com" in url || "youtu.be" in url -> "youtube"
        "douyin.com" in url || "iesdouyin.com" in url -> "douyin"
        else -> "other"
    }
}
```

---

### Task 6: Create DownloadManager

**Files:**
- Create: `app/src/main/java/com/asmrhelper/player/DownloadManager.kt`

- [ ] **Step 1: Create download state manager**

```kotlin
package com.asmrhelper.player

import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = ""
)

@Singleton
class DownloadManager @Inject constructor(
    private val extractor: VideoAudioExtractor,
    private val repository: VideoAudioRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun startDownload(url: String, onComplete: (VideoAudio?) -> Unit = {}) {
        if (_state.value.isDownloading) return
        downloadJob = scope.launch {
            _state.value = DownloadState(true, 0f, "正在初始化...")
            extractor.init()

            _state.value = _state.value.copy(statusText = "正在获取视频信息...")
            val result = extractor.extractAudio(url) { progress ->
                _state.value = _state.value.copy(
                    progress = progress,
                    statusText = "正在提取音频 ${(progress * 100).toInt()}%"
                )
            }

            if (result != null) {
                _state.value = _state.value.copy(
                    statusText = "正在保存...",
                    progress = 1f
                )
                val videoAudio = VideoAudio(
                    title = result.title,
                    platform = result.platform,
                    sourceUrl = result.sourceUrl,
                    filePath = result.filePath,
                    durationMs = result.durationMs,
                    fileSizeBytes = result.fileSizeBytes
                )
                repository.insert(videoAudio)
                _state.value = DownloadState(false, 1f, "完成")
                onComplete(videoAudio)
            } else {
                _state.value = DownloadState(false, 0f, "提取失败")
                onComplete(null)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        _state.value = DownloadState(false, 0f, "已取消")
    }
}
```

---

### Task 7: Create VideoAudioViewModel

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/library/VideoAudioViewModel.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.asmrhelper.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.domain.repository.VideoAudioRepository
import com.asmrhelper.player.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoAudioViewModel @Inject constructor(
    private val repository: VideoAudioRepository,
    private val downloadManager: DownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val deletedUrlsKey = "deleted_video_audio_urls"

    val videoAudios: StateFlow<List<VideoAudio>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadState = downloadManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState())

    fun startDownload(url: String, onComplete: (Boolean) -> Unit = {}) {
        // Check if URL was previously deleted
        val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
        val deletedUrls = prefs.getStringSet(deletedUrlsKey, emptySet()) ?: emptySet()
        if (url in deletedUrls) {
            onComplete(false)
            return
        }
        downloadManager.startDownload(url) { result ->
            onComplete(result != null)
        }
    }

    fun cancelDownload() = downloadManager.cancel()

    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch { repository.updateFavorite(id, isFavorite) }
    }

    fun deleteVideoAudio(videoAudio: VideoAudio, deleteFile: Boolean = false) {
        viewModelScope.launch {
            repository.deleteById(videoAudio.id)
            // Track deleted URL to prevent re-import
            val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)
            val deletedUrls = (prefs.getStringSet(deletedUrlsKey, emptySet())
                ?: emptySet()).toMutableSet()
            deletedUrls.add(videoAudio.sourceUrl)
            prefs.edit().putStringSet(deletedUrlsKey, deletedUrls).apply()
            // Optionally delete local file
            if (deleteFile) {
                try { File(videoAudio.filePath).delete() } catch (_: Exception) {}
                videoAudio.coverPath?.let { try { File(it).delete() } catch (_: Exception) {} }
            }
        }
    }
}
```

---

### Task 8: Create DownloadDialog composable

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/library/DownloadDialog.kt`

- [ ] **Step 1: Create the download dialog**

```kotlin
package com.asmrhelper.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmrhelper.player.DownloadState
import com.asmrhelper.ui.theme.*

@Composable
fun DownloadDialog(
    initialUrl: String = "",
    downloadState: DownloadState,
    onStartDownload: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        if (url.isEmpty()) {
            val clipText = clipboardManager.getText()?.text
            if (clipText != null && clipText.startsWith("http")) {
                url = clipText
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloadState.isDownloading) onDismiss() },
        title = {
            Text(
                text = if (downloadState.isDownloading) "正在提取音频" else "提取视频音频",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!downloadState.isDownloading && downloadState.progress == 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                DarkSurfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (url.isEmpty()) {
                            Text("粘贴视频链接 (B站/YouTube/抖音...)", color = TextHint, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            singleLine = true
                        )
                    }
                }

                if (downloadState.isDownloading || downloadState.progress > 0f) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = downloadState.statusText,
                        color = AccentPurple,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (downloadState.progress in 0.01f..0.99f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentPurple,
                            trackColor = DarkSurfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!downloadState.isDownloading) {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) onStartDownload(url.trim())
                    },
                    enabled = url.isNotBlank()
                ) {
                    Text("提取音频", color = if (url.isNotBlank()) AccentPurple else TextHint)
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text("取消", color = ErrorRed)
                }
            }
        },
        dismissButton = {
            if (!downloadState.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
```

---

### Task 9: Create VideoAudioTab composable

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/library/VideoAudioTab.kt`

- [ ] **Step 1: Create the tab with list, platform icons, and delete dialog**

```kotlin
package com.asmrhelper.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmrhelper.domain.model.VideoAudio
import com.asmrhelper.ui.theme.*

@Composable
fun VideoAudioTab(
    videoAudios: List<VideoAudio>,
    onPlayAudio: (VideoAudio) -> Unit,
    onDeleteAudio: (VideoAudio, Boolean) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var audioToDelete by remember { mutableStateOf<VideoAudio?>(null) }
    var deleteCacheFile by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (videoAudios.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无视频音频，点击右下角 + 提取", color = TextHint, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(videoAudios, key = { it.id }) { videoAudio ->
                VideoAudioCard(
                    videoAudio = videoAudio,
                    onPlay = { onPlayAudio(videoAudio) },
                    onDelete = {
                        audioToDelete = videoAudio
                        deleteCacheFile = false
                        showDeleteDialog = true
                    },
                    onToggleFavorite = { onToggleFavorite(videoAudio.id, !videoAudio.isFavorite) }
                )
            }
        }
    }

    if (showDeleteDialog && audioToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                audioToDelete = null
            },
            title = { Text("确认删除", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "确定要移除\"${audioToDelete!!.title}\"吗？",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteCacheFile = !deleteCacheFile }
                    ) {
                        Checkbox(
                            checked = deleteCacheFile,
                            onCheckedChange = { deleteCacheFile = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentPurple)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "同时删除本地缓存文件",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    audioToDelete?.let { onDeleteAudio(it, deleteCacheFile) }
                    showDeleteDialog = false
                    audioToDelete = null
                }) {
                    Text("移除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    audioToDelete = null
                }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoAudioCard(
    videoAudio: VideoAudio,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Platform icon
            val platformIcon = when (videoAudio.platform) {
                "bilibili" -> "📺"
                "youtube" -> "▶️"
                "douyin" -> "🎵"
                else -> "🔗"
            }
            Text(platformIcon, fontSize = 22.sp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    videoAudio.title, color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    videoAudio.sourceUrl.take(50),
                    color = TextHint, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoAudio.sourceUrl))
                        context.startActivity(intent)
                    }
                )
            }

            // Duration
            Text(
                formatDuration(videoAudio.durationMs),
                color = TextHint, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Favorite
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (videoAudio.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (videoAudio.isFavorite) ErrorRed else TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "移除",
                    tint = TextHint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
```

---

### Task 10: Add 4th tab to LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Add "视频音频" as 4th tab**

Change the tab titles list:
```kotlin
val tabTitles = listOf("全部音频", "我的收藏", "文件管理", "视频音频")
```

- [ ] **Step 2: Add the 4th `when` branch and wire up state/ViewModel**

Add state:
```kotlin
var showDownloadDialog by remember { mutableStateOf(false) }
var downloadUrl by remember { mutableStateOf("") }
val videoAudioViewModel: VideoAudioViewModel = hiltViewModel()
val videoAudios by videoAudioViewModel.videoAudios.collectAsStateWithLifecycle()
val downloadState by videoAudioViewModel.downloadState.collectAsStateWithLifecycle()
```

Add switch branch after `2 ->`:
```kotlin
3 -> VideoAudioTab(
    videoAudios = videoAudios,
    onPlayAudio = { va -> /* reuse existing player */ },
    onDeleteAudio = { va, deleteFile ->
        videoAudioViewModel.deleteVideoAudio(va, deleteFile)
    },
    onToggleFavorite = { id, fav ->
        videoAudioViewModel.toggleFavorite(id, fav)
    }
)
```

- [ ] **Step 3: Add FAB for video audio tab**

When `selectedTabIndex == 3`, show a FAB in the bottom-end that opens `DownloadDialog`:
```kotlin
if (selectedTabIndex == 3) {
    FloatingActionButton(
        onClick = { showDownloadDialog = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        containerColor = AccentPurple,
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = "提取视频音频")
    }
}
```

- [ ] **Step 4: Show DownloadDialog**

```kotlin
if (showDownloadDialog) {
    DownloadDialog(
        initialUrl = downloadUrl,
        downloadState = downloadState,
        onStartDownload = { url ->
            videoAudioViewModel.startDownload(url) { success ->
                if (!success) {
                    Toast.makeText(context, "该链接无法提取或已被移除", Toast.LENGTH_SHORT).show()
                }
                showDownloadDialog = false
            }
        },
        onCancel = { videoAudioViewModel.cancelDownload() },
        onDismiss = { showDownloadDialog = false }
    )
}
```

---

### Task 11: Handle Share Intent in MainActivity

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add intent filter to AndroidManifest.xml**

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

- [ ] **Step 2: Handle share intent and pass URL to navigation**

In `MainActivity.onCreate()`, after `setContent`:

```kotlin
// Handle share intent (video URL from external apps)
if (Intent.ACTION_SEND == intent?.action && intent?.type == "text/plain") {
    val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
    if (sharedUrl.startsWith("http")) {
        // Navigate to library → video audio tab → download dialog with URL
    }
}
```

Share the URL via a shared ViewModel or composable state that `DownloadDialog` can read.

---

### Task 12: Add "视频音频" to PlayScreen menu

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`

- [ ] **Step 1: Add menu item**

```kotlin
MenuItem("video_audio", "视频音频"),
```

- [ ] **Step 2: Add navigation handler**

```kotlin
"video_audio" -> onNavigateToLibrary(3) // tab index 3
```

---

### Task 13: Wire play via existing ExoPlayer

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`

- [ ] **Step 1: Add play method for VideoAudio in PlayViewModel**

```kotlin
fun playVideoAudio(videoAudio: VideoAudio) {
    // Create a temporary Audio from VideoAudio to reuse existing player
    val audio = Audio(
        id = videoAudio.filePath.hashCode().toLong(),
        title = videoAudio.title,
        artist = videoAudio.platform,
        filePath = videoAudio.filePath,
        durationMs = videoAudio.durationMs,
        isFavorite = videoAudio.isFavorite
    )
    play(audio)
}
```

- [ ] **Step 2: Wire in AsmrNavHost**

In the LibraryScreen section, change `onPlayAudio` to detect VideoAudio tab and call `playVideoAudio`:

```kotlin
onPlayAudio = { a -> playViewModel.play(a) },
// For video audio tab, need a new callback
onPlayVideoAudio = { va -> playViewModel.playVideoAudio(va) }
```

The LibraryScreen already supports `onPlayAudio: (Audio) -> Unit`. For VideoAudio, we create an Audio wrapper internally so the existing player works without changes.

---

### Task 14: Build and verify

- [ ] **Step 1: Build**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify features**

App launches → Library → "视频音频" tab visible → FAB opens download dialog → paste URL → extract → plays audio → delete with cache option
