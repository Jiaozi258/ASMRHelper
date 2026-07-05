# Feature Round 3 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 5 new features: SoundCloud waveform visualization, custom timer, trigger pad individual volume, playback history, and image slideshow.

**Architecture:** Modify existing PlayScreen composables for viz/timer; add volume map to TriggerPadViewModel; create new Room-backed modules for history and slideshow (6 new files each).

**Tech Stack:** Kotlin 1.9, Jetpack Compose, Hilt DI, Room (version 4 → 6 migration), Media3 ExoPlayer, Coil, Android Visualizer API

## Global Constraints

- Room DB version: 4 → bump to 6 (add play_history + image_library tables)
- All new entities use `@PrimaryKey(autoGenerate = true) val id: Long = 0`
- Repository pattern: domain/repository interface + data/repository impl
- UI follows existing dark theme (DarkBackground, DarkSurface, AccentPurple, etc.)
- Compose Navigation: new screens registered in AsmrNavHost
- No external library additions beyond current deps

---

### Task 1: SoundCloud Waveform Visualizer

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`

**Interfaces:**
- Consumes: `AudioVisualizerController.waveformBytes: StateFlow<ByteArray?>` (already exists)
- Produces: `WaveformBar()` composable replaces `VolumeVisualizer()` and `AudioVisualizer()`

- [ ] **Step 1: Read current state of PlayScreen.kt visualizer toggle area (lines 210–225)**

The current code uses `VolumeVisualizer` with FFT data. Replace with waveform-based approach.

- [ ] **Step 2: Replace `VolumeVisualizer` call site at lines 211–225**

Replace:
```kotlin
val visualizerOn by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
if (visualizerOn) {
    val fftMags by viewModel.fftMagnitudes.collectAsStateWithLifecycle()
    VolumeVisualizer(
        isPlaying = state.playerState.isPlaying,
        progress = if (state.playerState.durationMs > 0)
            state.playerState.progressMs.toFloat() / state.playerState.durationMs else 0f,
        fftMagnitudes = fftMags,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 56.dp)
            .fillMaxWidth()
            .height(48.dp)
    )
}
```

With:
```kotlin
val visualizerOn by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
if (visualizerOn) {
    val waveformBytes by viewModel.waveformBytes.collectAsStateWithLifecycle()
    SoundCloudWaveform(
        waveformBytes = waveformBytes,
        isPlaying = state.playerState.isPlaying,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 56.dp)
            .fillMaxWidth()
            .height(64.dp)
    )
}
```

- [ ] **Step 3: Replace bottom `AudioVisualizer` call at lines 494–500**

Replace:
```kotlin
AudioVisualizer(
    progress = progressFraction,
    isPlaying = isPlaying,
    modifier = Modifier
        .fillMaxWidth()
        .height(24.dp)
)
```

With same `SoundCloudWaveform` but smaller:
```kotlin
SoundCloudWaveform(
    waveformBytes = waveformBytes,
    isPlaying = isPlaying,
    modifier = Modifier
        .fillMaxWidth()
        .height(32.dp)
)
```

Note: need to move `val waveformBytes` collection up to parent scope or create it again.

- [ ] **Step 4: Add `SoundCloudWaveform` composable implementation**

Add before `BreathingOverlay()` (around line 1120). Replace existing `AudioVisualizer()` and `VolumeVisualizer()`:

```kotlin
@Composable
private fun SoundCloudWaveform(
    waveformBytes: ByteArray?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth interpolation state — lerp toward latest waveform data
    val currentData = remember { mutableStateOf(FloatArray(0)) }
    val targetData = remember { mutableStateOf(FloatArray(0)) }

    // Convert raw ByteArray to normalized floats when new data arrives
    LaunchedEffect(waveformBytes) {
        if (waveformBytes != null && waveformBytes!!.isNotEmpty()) {
            val floats = FloatArray(waveformBytes!!.size)
            for (i in floats.indices) {
                // Byte 0-255 → float -1.0 to 1.0
                floats[i] = ((waveformBytes!![i].toInt() and 0xFF) - 128) / 128f
            }
            targetData.value = floats
        }
    }

    // Smooth animation: lerp current → target each frame
    LaunchedEffect(Unit) {
        while (true) {
            val cur = currentData.value
            val tgt = targetData.value
            if (cur.size != tgt.size && tgt.isNotEmpty()) {
                currentData.value = tgt.copyOf()
            } else if (cur.isNotEmpty() && tgt.isNotEmpty()) {
                val smoothed = FloatArray(cur.size)
                for (i in smoothed.indices) {
                    if (i < tgt.size) {
                        smoothed[i] = cur[i] + (tgt[i] - cur[i]) * 0.35f
                    }
                }
                currentData.value = smoothed
            }
            delay(16L) // ~60fps
        }
    }

    val data = currentData.value

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val centerY = size.height / 2f
        val maxAmp = size.height / 2f - 2.dp.toPx()

        // Draw center line (faint guide)
        drawLine(
            color = Color(0xFFBB86FC).copy(alpha = 0.15f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        if (!isPlaying && data.all { kotlin.math.abs(it) < 0.02f }) return@Canvas

        // Build path: wave oscillates around centerY
        val path = Path()
        val step = size.width / data.size.coerceAtLeast(1)

        path.moveTo(0f, centerY)
        for (i in data.indices) {
            val x = i * step
            // Apply exponential scaling so quiet parts sit near center
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.pow(kotlin.math.abs(normalized), 0.7f).toFloat()
            val y = centerY + scaled * maxAmp
            path.lineTo(x, y)
        }
        // Complete the symmetric shape (mirror below center line)
        for (i in data.indices.reversed()) {
            val x = i * step
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.pow(kotlin.math.abs(normalized), 0.7f).toFloat()
            val y = centerY - scaled * maxAmp  // mirror
            path.lineTo(x, y)
        }
        path.close()

        // Fill the waveform with gradient
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFBB86FC).copy(alpha = 0.6f),
                    Color(0xFFBB86FC).copy(alpha = 0.15f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        // Draw the top edge line (bright)
        val edgePath = Path()
        edgePath.moveTo(0f, centerY)
        for (i in data.indices) {
            val x = i * step
            val normalized = data[i].coerceIn(-1f, 1f)
            val scaled = kotlin.math.sign(normalized) *
                kotlin.math.pow(kotlin.math.abs(normalized), 0.7f).toFloat()
            val y = centerY + scaled * maxAmp
            edgePath.lineTo(x, y)
        }
        drawPath(
            path = edgePath,
            color = Color(0xFFBB86FC).copy(alpha = 0.9f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
```

Add required imports at top of file:
```kotlin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
```

- [ ] **Step 5: Remove old composable functions**

Delete `AudioVisualizer()` (lines 1061–1112), `VolumeVisualizer()` (lines 1177–1238), and `TimerOption()` (lines 1042–1060 — keep TimerOption for timer task).

Actually, keep `TimerOption` — we need it for the timer task.

- [ ] **Step 6: Add waveformBytes to PlayViewModel if not already exposed**

Check if `viewModel.waveformBytes` exists in PlayViewModel. If not, add:
```kotlin
val waveformBytes: StateFlow<ByteArray?> = visualizerController.waveformBytes
```

- [ ] **Step 7: Build and verify**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt
git commit -m "feat: SoundCloud-style waveform visualizer replacing bar charts"
```

---

### Task 2: Custom Timer

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`

**Interfaces:**
- Consumes: `PlayViewModel.setTimer(minutes)` → change to `setTimerSeconds(totalSeconds: Int)`
- Produces: Custom time picker dialog, pomodoro custom sliders

- [ ] **Step 1: Update PlayViewModel.setTimer to accept seconds**

Replace `setTimer(minutes: Int)`:
```kotlin
fun setTimerSeconds(totalSeconds: Int) {
    cancelTimer()
    val minutes = totalSeconds / 60
    val totalMs = totalSeconds * 1000L
    _uiState.update { it.copy(timerMinutes = minutes, timerRemainingMs = totalMs, timerActive = true) }
    timerJob = viewModelScope.launch {
        while (_uiState.value.timerRemainingMs > 0) {
            delay(1000L)
            _uiState.update { current ->
                val remaining = (current.timerRemainingMs - 1000L).coerceAtLeast(0L)
                current.copy(timerRemainingMs = remaining)
            }
        }
        _uiState.update { it.copy(timerActive = false, timerMinutes = 0, timerRemainingMs = 0L) }
        playerManager.handleEvent(PlayerEvent.Pause)
    }
}
```

Keep old `setTimer(minutes)` as deprecated bridge:
```kotlin
@Deprecated("Use setTimerSeconds", ReplaceWith("setTimerSeconds(minutes * 60)"))
fun setTimer(minutes: Int) = setTimerSeconds(minutes * 60)
```

- [ ] **Step 2: Add pomodoro custom duration state and start method**

Add to `PlayUiState`:
```kotlin
val pomodoroCustomFocusMin: Int = 25,
val pomodoroCustomBreakMin: Int = 5,
```

Add to `PlayViewModel`:
```kotlin
fun updatePomodoroCustomFocus(minutes: Int) {
    _uiState.update { it.copy(pomodoroCustomFocusMin = minutes.coerceIn(1, 120)) }
}

fun updatePomodoroCustomBreak(minutes: Int) {
    _uiState.update { it.copy(pomodoroCustomBreakMin = minutes.coerceIn(1, 30)) }
}
```

- [ ] **Step 3: Rewrite timer dialog UI in PlayScreen.kt**

Replace the timer dialog section (lines 538–607) with:

```kotlin
if (showTimerDialog) {
    var showCustomPicker by remember { mutableStateOf(false) }
    var showPomodoroCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { showTimerDialog = false },
        title = {
            Text("定时器", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Sleep Timer ──
                Text("睡眠定时", color = TextHint, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))

                if (showCustomPicker) {
                    // Custom time: hours + minutes sliders
                    var customHours by remember { mutableIntStateOf(0) }
                    var customMinutes by remember { mutableIntStateOf(15) }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("小时", color = TextSecondary, fontSize = 13.sp)
                            Slider(value = customHours.toFloat(), onValueChange = { customHours = it.toInt() },
                                valueRange = 0f..3f, steps = 2,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                            Text("${customHours}h", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(36.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("分钟", color = TextSecondary, fontSize = 13.sp)
                            Slider(value = customMinutes.toFloat(), onValueChange = { customMinutes = it.toInt() },
                                valueRange = 0f..59f, steps = 58,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                            Text("${customMinutes}m", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showCustomPicker = false }) {
                                Text("取消", color = TextSecondary)
                            }
                            Button(onClick = {
                                val total = customHours * 3600 + customMinutes * 60
                                if (total > 0 && total <= 10800) {
                                    viewModel.setTimerSeconds(total)
                                    showTimerDialog = false
                                }
                            }, enabled = (customHours * 60 + customMinutes) > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                                Text("开始")
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTimerChip("15分") { viewModel.setTimerSeconds(15 * 60); showTimerDialog = false }
                        QuickTimerChip("30分") { viewModel.setTimerSeconds(30 * 60); showTimerDialog = false }
                        QuickTimerChip("45分") { viewModel.setTimerSeconds(45 * 60); showTimerDialog = false }
                        QuickTimerChip("60分") { viewModel.setTimerSeconds(60 * 60); showTimerDialog = false }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { showCustomPicker = true }) {
                        Text("⏱ 自定义...", color = AccentPurple, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(2.dp))
                    TimerOption(
                        text = if (state.stopAfterCurrent) "✓ 播完当前停止" else "播完当前停止",
                        color = if (state.stopAfterCurrent) AccentPurple else TextSecondary
                    ) {
                        viewModel.toggleStopAfterCurrent(); showTimerDialog = false
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Pomodoro ──
                Text("番茄钟", color = TextHint, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))

                if (showPomodoroCustom) {
                    var focusMin by remember { mutableIntStateOf(state.pomodoroCustomFocusMin) }
                    var breakMin by remember { mutableIntStateOf(state.pomodoroCustomBreakMin) }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("专注时长: ${focusMin}分钟", color = TextSecondary, fontSize = 13.sp)
                        Slider(value = focusMin.toFloat(), onValueChange = { focusMin = it.toInt() },
                            valueRange = 1f..120f, steps = 118,
                            colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                        Spacer(Modifier.height(8.dp))
                        Text("休息时长: ${breakMin}分钟", color = TextSecondary, fontSize = 13.sp)
                        Slider(value = breakMin.toFloat(), onValueChange = { breakMin = it.toInt() },
                            valueRange = 1f..30f, steps = 28,
                            colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showPomodoroCustom = false }) {
                                Text("取消", color = TextSecondary)
                            }
                            Button(onClick = {
                                viewModel.updatePomodoroCustomFocus(focusMin)
                                viewModel.updatePomodoroCustomBreak(breakMin)
                                viewModel.startPomodoro(focusMin, breakMin)
                                showTimerDialog = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                                Text("🍅 开始")
                            }
                        }
                    }
                } else {
                    TimerOption("🍅 ${state.pomodoroCustomFocusMin}分钟专注 + ${state.pomodoroCustomBreakMin}分钟休息") {
                        viewModel.startPomodoro(state.pomodoroCustomFocusMin, state.pomodoroCustomBreakMin)
                        showTimerDialog = false
                    }
                    TextButton(onClick = { showPomodoroCustom = true }) {
                        Text("⚙ 自定义番茄钟...", color = AccentPurple, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showTimerDialog = false }) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
```

- [ ] **Step 4: Add `QuickTimerChip` composable**

Add before `TimerOption`:
```kotlin
@Composable
private fun QuickTimerChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
```

- [ ] **Step 5: Build and verify**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt
git commit -m "feat: custom timer with hours/minutes picker and pomodoro duration sliders"
```

---

### Task 3: Trigger Pad Individual Volume

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/triggerpad/TriggerPadViewModel.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/triggerpad/TriggerPadScreen.kt`

**Interfaces:**
- Consumes: `parallelPlayers: Map<Int, ExoPlayer>` (existing)
- Produces: `slotVolumes: Map<Int, Float>`, `setSlotVolume(slotIndex, volume)`, UI slider per active pad

- [ ] **Step 1: Add volume state to TriggerPadViewModel**

Add to `TriggerPadUiState`:
```kotlin
val slotVolumes: Map<Int, Float> = emptyMap()
```

Add to class body:
```kotlin
fun setSlotVolume(slotIndex: Int, volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _uiState.update { it.copy(slotVolumes = it.slotVolumes + (slotIndex to clamped)) }
    parallelPlayers[slotIndex]?.volume = clamped
}
```

Update `startParallelSlot` to apply stored volume:
```kotlin
private fun startParallelSlot(filePath: String, slotIndex: Int) {
    val player = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(filePath))
        repeatMode = Player.REPEAT_MODE_ONE
        volume = _uiState.value.slotVolumes[slotIndex] ?: 1f  // apply stored volume
        prepare()
        play()
    }
    parallelPlayers[slotIndex] = player
}
```

- [ ] **Step 2: Add volume slider to PadButton in TriggerPadScreen.kt**

Extend `PadButton` signature:
```kotlin
@Composable
private fun PadButton(
    index: Int,
    pad: TriggerPadEntity?,
    isActive: Boolean,
    mode: TriggerPadMode,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    volume: Float = 1f,                     // NEW
    onVolumeChange: (Float) -> Unit = {},    // NEW
    modifier: Modifier = Modifier
)
```

Inside PadButton, after the name text, add volume slider visible only when active AND in parallel mode:
```kotlin
// Volume slider — only in parallel mode when active
if (mode == TriggerPadMode.Parallel && isActive) {
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = volume,
        onValueChange = onVolumeChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(16.dp),
        colors = SliderDefaults.colors(
            thumbColor = AccentPurple,
            activeTrackColor = AccentPurple,
            inactiveTrackColor = DarkSurfaceVariant
        )
    )
}
```

- [ ] **Step 3: Update PadButton call sites**

In the LazyVerticalGrid items block, pass volume:
```kotlin
PadButton(
    index = index,
    pad = pad,
    isActive = isActive,
    mode = state.mode,
    volume = state.slotVolumes[index] ?: 1f,
    onVolumeChange = { vol -> viewModel.setSlotVolume(index, vol) },
    onTap = { ... },
    onRemove = { ... }
)
```

- [ ] **Step 4: Build and verify**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/ui/triggerpad/
git commit -m "feat: per-slot volume control in trigger parallel mode"
```

---

### Task 4: Playback History

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/PlayHistoryEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/PlayHistoryDao.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/PlayHistoryRepository.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/PlayHistoryRepositoryImpl.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt`
- Modify: `app/src/main/java/com/asmrhelper/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/asmrhelper/player/PlayerManager.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create PlayHistoryEntity**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioTitle: String,
    val audioArtist: String,
    val filePath: String,
    val playedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)
```

- [ ] **Step 2: Create PlayHistoryDao**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun getAll(): Flow<List<PlayHistoryEntity>>

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM play_history")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Create PlayHistoryRepository interface**

```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

interface PlayHistoryRepository {
    fun getAll(): Flow<List<PlayHistoryEntity>>
    suspend fun insert(entry: PlayHistoryEntity)
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}
```

- [ ] **Step 4: Create PlayHistoryRepositoryImpl**

```kotlin
package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.PlayHistoryDao
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.domain.repository.PlayHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayHistoryRepositoryImpl @Inject constructor(
    private val dao: PlayHistoryDao
) : PlayHistoryRepository {
    override fun getAll(): Flow<List<PlayHistoryEntity>> = dao.getAll()
    override suspend fun insert(entry: PlayHistoryEntity) = dao.insert(entry)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
```

- [ ] **Step 5: Update AsmrDatabase (add entity + dao + bump version)**

Add entity:
```kotlin
PlayHistoryEntity::class,
```
Add abstract DAO method:
```kotlin
abstract fun playHistoryDao(): PlayHistoryDao
```
Bump version: `version = 4` → `version = 5`

- [ ] **Step 6: Update DatabaseModule (add migration + DAO provider)**

Add migration 4→5:
```kotlin
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS play_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                audioTitle TEXT NOT NULL,
                audioArtist TEXT NOT NULL,
                filePath TEXT NOT NULL,
                playedAt INTEGER NOT NULL DEFAULT 0,
                durationMs INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
```

Update `.addMigrations(MIGRATION_2_3, MIGRATION_3_4)` → `.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`

Add provider:
```kotlin
@Provides
fun providePlayHistoryDao(db: AsmrDatabase): PlayHistoryDao = db.playHistoryDao()
```

Add `Binds` for repository:
```kotlin
@Binds
abstract fun bindPlayHistoryRepository(impl: PlayHistoryRepositoryImpl): PlayHistoryRepository
```

Actually, for simplicity, inject the impl directly via `@Inject constructor` (already done above). Or add a bind module. Let me use the simplest approach — just inject `PlayHistoryRepositoryImpl` in the ViewModel directly, since that's the existing pattern (see TriggerPadViewModel injecting `TriggerPadRepositoryImpl`).

- [ ] **Step 7: Create HistoryViewModel**

```kotlin
package com.asmrhelper.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.db.entity.PlayHistoryEntity
import com.asmrhelper.data.repository.PlayHistoryRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: PlayHistoryRepositoryImpl
) : ViewModel() {
    val entries: StateFlow<List<PlayHistoryEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
```

- [ ] **Step 8: Create HistoryScreen**

```kotlin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
            }
            Text("播放历史", style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("清除全部", color = ErrorRed, fontSize = 13.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播放记录", color = TextHint, style = MaterialTheme.typography.bodyLarge)
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
                                    id = entry.id,
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
                                Text(entry.audioArtist.ifEmpty { "未知艺术家" } + " · $dateStr",
                                    color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.delete(entry.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "删除", tint = TextHint, modifier = Modifier.size(18.dp))
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
            title = { Text("清除全部历史", color = TextPrimary) },
            text = { Text("确定要清除所有播放记录吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAll(); showClearDialog = false }) {
                    Text("清除", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = DarkSurface, shape = RoundedCornerShape(16.dp)
        )
    }
}
```

- [ ] **Step 9: Record history in PlayerManager.play()**

Add dependency:
```kotlin
@Inject lateinit var playHistoryRepository: PlayHistoryRepositoryImpl
```

In `play()` method, after `_state.update { it.copy(currentAudio = audio) }`:
```kotlin
// Record playback history (fire-and-forget)
scope.launch(Dispatchers.IO) {
    try {
        playHistoryRepository.insert(
            com.asmrhelper.data.local.db.entity.PlayHistoryEntity(
                audioTitle = audio.title,
                audioArtist = audio.artist,
                filePath = audio.filePath,
                durationMs = audio.durationMs,
                playedAt = System.currentTimeMillis()
            )
        )
    } catch (_: Exception) { /* best-effort */ }
}
```

Add import for `Dispatchers` (already imported).

- [ ] **Step 10: Add navigation entry for HistoryScreen**

In `AsmrNavHost.kt`, add route and composable. In `SettingsScreen.kt`, add clickable entry "播放历史" that navigates.

SettingsScreen entry addition (find list of settings items, add):
```kotlin
// "播放历史" row
Row(
    modifier = Modifier.fillMaxWidth().clickable { onNavigateToHistory() }.padding(...),
    ...
) {
    Text("播放历史", color = TextPrimary)
    Icon(Icons.AutoMirrored.Filled.ArrowForward, ...)
}
```

Pass `onNavigateToHistory: () -> Unit` callback through SettingsScreen.

- [ ] **Step 11: Build and verify**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/local/db/entity/PlayHistoryEntity.kt \
        app/src/main/java/com/asmrhelper/data/local/db/dao/PlayHistoryDao.kt \
        app/src/main/java/com/asmrhelper/domain/repository/PlayHistoryRepository.kt \
        app/src/main/java/com/asmrhelper/data/repository/PlayHistoryRepositoryImpl.kt \
        app/src/main/java/com/asmrhelper/ui/history/ \
        app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt \
        app/src/main/java/com/asmrhelper/di/DatabaseModule.kt \
        app/src/main/java/com/asmrhelper/player/PlayerManager.kt \
        app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt
git commit -m "feat: playback history with Room persistence, settings entry, tap-to-replay"
```

---

### Task 5: Image Slideshow

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/local/db/entity/ImageLibraryEntity.kt`
- Create: `app/src/main/java/com/asmrhelper/data/local/db/dao/ImageLibraryDao.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/repository/ImageLibraryRepository.kt`
- Create: `app/src/main/java/com/asmrhelper/data/repository/ImageLibraryRepositoryImpl.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/slideshow/ImageSlideshowViewModel.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/slideshow/ImageSlideshowContent.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt`
- Modify: `app/src/main/java/com/asmrhelper/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`

- [ ] **Step 1: Create ImageLibraryEntity**

```kotlin
package com.asmrhelper.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_library")
data class ImageLibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create ImageLibraryDao**

```kotlin
package com.asmrhelper.data.local.db.dao

import androidx.room.*
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageLibraryDao {
    @Query("SELECT * FROM image_library ORDER BY addedAt ASC")
    fun getAll(): Flow<List<ImageLibraryEntity>>

    @Insert
    suspend fun insert(entity: ImageLibraryEntity): Long

    @Delete
    suspend fun delete(entity: ImageLibraryEntity)

    @Query("DELETE FROM image_library WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM image_library ORDER BY addedAt ASC LIMIT 1")
    suspend fun getFirst(): ImageLibraryEntity?
}

- [ ] **Step 3: Create ImageLibraryRepository interface + impl**

Interface:
```kotlin
package com.asmrhelper.domain.repository

import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import kotlinx.coroutines.flow.Flow

interface ImageLibraryRepository {
    fun getAll(): Flow<List<ImageLibraryEntity>>
    suspend fun insert(entity: ImageLibraryEntity): Long
    suspend fun deleteById(id: Long)
}
```

Impl:
```kotlin
package com.asmrhelper.data.repository

import com.asmrhelper.data.local.db.dao.ImageLibraryDao
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.domain.repository.ImageLibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageLibraryRepositoryImpl @Inject constructor(
    private val dao: ImageLibraryDao
) : ImageLibraryRepository {
    override fun getAll(): Flow<List<ImageLibraryEntity>> = dao.getAll()
    override suspend fun insert(entity: ImageLibraryEntity) = dao.insert(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
```

- [ ] **Step 4: Update AsmrDatabase + DatabaseModule**

AsmrDatabase: add entity `ImageLibraryEntity::class`, add `abstract fun imageLibraryDao(): ImageLibraryDao`, bump version to 6.

DatabaseModule: add migration 5→6:
```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS image_library (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                filePath TEXT NOT NULL,
                addedAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
```

Add to migrations list, add DAO provider.

- [ ] **Step 5: Create ImageSlideshowViewModel**

```kotlin
package com.asmrhelper.ui.slideshow

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmrhelper.data.local.db.entity.ImageLibraryEntity
import com.asmrhelper.data.repository.ImageLibraryRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SlideshowMode { Manual, Auto, Timed }

data class SlideshowState(
    val images: List<ImageLibraryEntity> = emptyList(),
    val currentIndex: Int = 0,
    val mode: SlideshowMode = SlideshowMode.Manual,
    val autoIntervalSec: Int = 5,
    val timePoints: List<Long> = emptyList(),  // in ms from song start
    val isImporting: Boolean = false
)

@HiltViewModel
class ImageSlideshowViewModel @Inject constructor(
    private val repository: ImageLibraryRepositoryImpl,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { images ->
                _state.value = _state.value.copy(images = images)
            }
        }
    }

    fun importFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isImporting = true)
            val dir = File(context.filesDir, "slideshow")
            if (!dir.exists()) dir.mkdirs()
            var count = 0
            for (uri in uris) {
                try {
                    val name = "${System.currentTimeMillis()}_${count}.jpg"
                    val dest = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                    if (dest.length() > 0) {
                        repository.insert(ImageLibraryEntity(filePath = dest.absolutePath))
                        count++
                    }
                } catch (_: Exception) { }
            }
            _state.value = _state.value.copy(isImporting = false)
            _toastMessage.emit(if (count > 0) "已导入 $count 张图片" else "未导入任何图片")
        }
    }

    fun deleteImage(id: Long) {
        viewModelScope.launch {
            val img = _state.value.images.find { it.id == id }
            if (img != null) {
                File(img.filePath).delete()
                repository.deleteById(id)
            }
        }
    }

    fun setMode(mode: SlideshowMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setAutoInterval(seconds: Int) {
        _state.value = _state.value.copy(autoIntervalSec = seconds.coerceIn(1, 60))
    }

    fun nextImage() {
        val images = _state.value.images
        if (images.isEmpty()) return
        val next = (_state.value.currentIndex + 1) % images.size
        _state.value = _state.value.copy(currentIndex = next)
    }

    fun prevImage() {
        val images = _state.value.images
        if (images.isEmpty()) return
        val prev = if (_state.value.currentIndex == 0) images.size - 1 else _state.value.currentIndex - 1
        _state.value = _state.value.copy(currentIndex = prev)
    }

    fun addTimePoint(positionMs: Long) {
        val tp = _state.value.timePoints.toMutableList()
        tp.add(positionMs)
        tp.sort()
        _state.value = _state.value.copy(timePoints = tp)
    }

    fun removeTimePoint(index: Int) {
        val tp = _state.value.timePoints.toMutableList()
        if (index in tp.indices) tp.removeAt(index)
        _state.value = _state.value.copy(timePoints = tp)
    }

    fun checkTimedAdvance(progressMs: Long) {
        if (_state.value.mode != SlideshowMode.Timed) return
        val tp = _state.value.timePoints
        if (tp.isEmpty()) return
        val idx = _state.value.currentIndex
        if (idx < tp.size && progressMs >= tp[idx]) {
            nextImage()
        }
    }
}
```

- [ ] **Step 6: Create ImageSlideshowContent composable**

This is an embedded composable for the PlayScreen's bottom tab. Let me write it compactly:

```kotlin
package com.asmrhelper.ui.slideshow

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.asmrhelper.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ImageSlideshowContent(
    progressMs: Long,
    viewModel: ImageSlideshowViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(progressMs) {
        viewModel.checkTimedAdvance(progressMs)
    }

    // SAF import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromUris(uris)
    }

    // Toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Current image display
        val currentImage = state.images.getOrNull(state.currentIndex)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (currentImage != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentImage.filePath)
                        .crossfade(300)
                        .build(),
                    contentDescription = "幻灯片图片",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Image, null, tint = TextHint, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { importLauncher.launch(arrayOf("image/*")) }) {
                        Text("+ 导入图片", color = AccentPurple)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Controls bar
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            // Mode chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("手动", state.mode == SlideshowMode.Manual) { viewModel.setMode(SlideshowMode.Manual) }
                ModeChip("自动", state.mode == SlideshowMode.Auto) { viewModel.setMode(SlideshowMode.Auto) }
                ModeChip("时间点", state.mode == SlideshowMode.Timed) { viewModel.setMode(SlideshowMode.Timed) }
                Spacer(Modifier.weight(1f))
                if (state.images.isNotEmpty()) {
                    IconButton(onClick = { importLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Add, "导入", tint = AccentPurple, modifier = Modifier.size(20.dp))
                    }
                }
            }

            when (state.mode) {
                SlideshowMode.Auto -> {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("间隔: ${state.autoIntervalSec}秒", color = TextSecondary, fontSize = 13.sp)
                        Slider(value = state.autoIntervalSec.toFloat(),
                            onValueChange = { viewModel.setAutoInterval(it.toInt()) },
                            valueRange = 1f..60f, steps = 58,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple))
                    }
                }
                SlideshowMode.Timed -> {
                    Spacer(Modifier.height(4.dp))
                    // Show time points
                    if (state.timePoints.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(state.timePoints.size) { idx ->
                                val ms = state.timePoints[idx]
                                val sec = ms / 1000
                                val label = "${sec / 60}:${String.format("%02d", sec % 60)}"
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(DarkSurfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = TextPrimary, fontSize = 12.sp)
                                        IconButton(onClick = { viewModel.removeTimePoint(idx) },
                                            modifier = Modifier.size(18.dp)) {
                                            Icon(Icons.Filled.Close, null, tint = TextHint, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.addTimePoint(progressMs) }) {
                        val sec = progressMs / 1000
                        Text("+ 当前时间 (${sec / 60}:${String.format("%02d", sec % 60)})",
                            color = AccentPurple, fontSize = 13.sp)
                    }
                }
                else -> {}
            }

            // Navigation row
            if (state.images.size > 1) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { viewModel.prevImage() }) {
                        Icon(Icons.Filled.SkipPrevious, "上一张", tint = TextPrimary)
                    }
                    Text("${state.currentIndex + 1}/${state.images.size}",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp))
                    IconButton(onClick = { viewModel.nextImage() }) {
                        Icon(Icons.Filled.SkipNext, "下一张", tint = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (selected) AccentPurple.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) AccentPurple else TextSecondary,
            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
```

- [ ] **Step 7: Add slideshow tab to PlayScreen bottom area**

After the "场景 + 书签" row (around line 451), add a bottom section with tabs. The simplest approach: add a horizontal tab row above the progress bar area.

Add state:
```kotlin
var bottomTab by remember { mutableIntStateOf(0) } // 0=进度条, 1=图片
```

In the bottom Column (around line 460), wrap content with tab selection:

```kotlin
// Bottom tab selector
Row(
    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
    horizontalArrangement = Arrangement.Center
) {
    TabChip("进度", bottomTab == 0) { bottomTab = 0 }
    TabChip("图片", bottomTab == 1) { bottomTab = 1 }
}

// Content based on tab
if (bottomTab == 0) {
    // Existing progress bar + time display (lines 460–521)
    Column(
        modifier = Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 24.dp).padding(bottom = 16.dp)
    ) {
        // ... existing code ...
    }
} else {
    // Slideshow tab
    ImageSlideshowContent(
        progressMs = state.playerState.progressMs,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp)
    )
}
```

Add TabChip composable:
```kotlin
@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(if (selected) DarkSurface else DarkSurfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) AccentPurple else TextHint, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
```

- [ ] **Step 8: Build and verify**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/local/db/entity/ImageLibraryEntity.kt \
        app/src/main/java/com/asmrhelper/data/local/db/dao/ImageLibraryDao.kt \
        app/src/main/java/com/asmrhelper/domain/repository/ImageLibraryRepository.kt \
        app/src/main/java/com/asmrhelper/data/repository/ImageLibraryRepositoryImpl.kt \
        app/src/main/java/com/asmrhelper/ui/slideshow/ \
        app/src/main/java/com/asmrhelper/data/local/db/AsmrDatabase.kt \
        app/src/main/java/com/asmrhelper/di/DatabaseModule.kt \
        app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt
git commit -m "feat: image slideshow with manual/auto/timed modes, tab integration in player"
```

---

### Final verification

After all 5 tasks:
```bash
.\gradlew.bat clean assembleDebug
```
Expected: BUILD SUCCESSFUL, all features integrated.
