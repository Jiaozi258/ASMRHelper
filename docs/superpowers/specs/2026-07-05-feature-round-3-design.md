# Feature Round 3 — Design Spec

> **Date:** 2026-07-05
> **Status:** Approved — Ready for implementation plan

## Overview

5 new features plus 2 bug fixes for ASMRHelper Android app.

---

## Bug Fixes (already implemented)

### B1: Notification spam
**Root cause:** `PlayerManager` emits state every 200ms (progress polling) → `updateNotification()` rebuilds + `nm.notify()` on every tick → notification flickers/pops constantly.

**Fix:**
- Throttle: only rebuild notification when title/artist/playing state changes, OR at most once per second
- Added `setOnlyAlertOnce(true)` to prevent sound/vibration on update
- Files: `AsmrMediaService.kt`

### B2: Playback stops after ~15 minutes
**Root cause:** Chinese ROMs (MIUI, ColorOS) aggressively kill services despite foreground status. CPU sleep interrupts ExoPlayer.

**Fix:**
- Added `PARTIAL_WAKE_LOCK` — acquired during playback, released on pause/destroy
- Added `WAKE_LOCK` permission to manifest
- Files: `AsmrMediaService.kt`, `AndroidManifest.xml`

---

## Feature 3: Audio Visualization (SoundCloud Waveform)

### Current state
- `AudioVisualizerController` already captures `waveformBytes` (raw PCM) and `fftMagnitudes` (frequency spectrum) via Android Visualizer API
- UI currently uses `VolumeVisualizer` (bar chart) and `AudioVisualizer` (old style)

### Target behavior
- A thin horizontal center line
- Waveform data oscillates symmetrically above and below the center line
- Quiet audio → nearly flat line; loud audio → tall smooth peaks
- SoundCloud/audacity-style waveform

### Implementation
- **Data source:** `AudioVisualizerController.waveformBytes` (StateFlow<ByteArray?>)
- **Rendering:** Compose Canvas, draw path from connected points
  - X axis: 0 to canvas width, evenly divided by waveform data length
  - Y axis: each byte (0-255) mapped to offset from center (−height/2 to +height/2), normalized to canvas
  - Smooth connection via `path.lineTo()` or Catmull-Rom interpolation
  - Gradient color (AccentPurple → transparent at edges)
- **Animation:** Buffer smoothing — interpolate between frames for fluid motion (lerp current frame toward new data)
- **Files:** Replace visualization composables in `PlayScreen.kt`, no changes to `AudioVisualizerController.kt`

---

## Feature 4: Timer Enhancement

### Current state
- Hardcoded presets: 15/30/60 min + pomodoro 25/5 and 45/10
- No custom time input

### Target behavior
- **Sleep timer:** User can set any duration from 1 min to 3 hours. UI: quick presets row + "自定义" button → number picker (hours 0-3, minutes 0-59)
- **Pomodoro:** User can customize focus (1-120 min) and break (1-30 min) durations via sliders. Default: 25/5

### Implementation
- **UI:** Replace `TimerOption` list with:
  - Row of quick presets (15/30/45/60 min)
  - "自定义…" button → dialog with hour/minute columns (NumberPicker-style via `LazyColumn` + fling behavior, or two slider rows labeled hours/minutes)
  - Pomodoro section: focus slider (default 25, range 1-120), break slider (default 5, range 1-30), then start button
- **Logic:** `setTimer(totalSeconds: Int)` accepts any duration within 3-hour limit. Existing countdown loop unchanged.
- **Files:** `PlayScreen.kt` (timer dialog), `PlayViewModel.kt` (accept seconds)

---

## Feature 5: Image Slideshow (New Module)

### Architecture
```
data/local/db/
  entity/ImageLibraryEntity.kt     — Room entity (id, filePath, addedAt)
  dao/ImageLibraryDao.kt           — CRUD operations
data/repository/
  ImageLibraryRepositoryImpl.kt    — wraps DAO
domain/repository/
  ImageLibraryRepository.kt        — interface
ui/slideshow/
  ImageLibraryScreen.kt            — image library management (import/delete)
  ImageSlideshowViewModel.kt       — state management
  ImageSlideshowContent.kt         — embedded slideshow composable for PlayScreen
```

### Image Library
- SAF multi-select import (`ActivityResultContracts.OpenMultipleDocuments()` with `image/*`)
- Files copied to `context.filesDir/slideshow/`
- Library management: grid view, long-press to delete
- Accessible from slideshow tab + potentially settings

### Playback Modes (bottom tab "图片" in PlayScreen)
| Mode | Behavior |
|---|---|
| Manual | Swipe left/right or <- -> buttons to navigate |
| Auto | Slides advance on interval (configurable 1-60 seconds via slider) |
| Timed | User defines time-point list (e.g. 0:30, 1:15, 3:00). Each point triggers next image. After last point → manual mode |

### UI Layout (embedded in PlayScreen)
- Image display: `fillMaxWidth`, maintain aspect ratio, `ContentScale.Fit`
- Crossfade transition between images (300ms)
- Bottom toolbar: mode selector chips, interval slider (auto mode), time-point editor (timed mode), prev/next buttons
- Empty state: "点击 + 导入图片" hint

### Database
```kotlin
@Entity(tableName = "image_library")
data class ImageLibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val addedAt: Long = System.currentTimeMillis()
)
```

### Dependencies
- `coil` (already used in project) for image loading
- `Room` for persistence
- `Hilt` for DI
- No new external libraries

---

## Feature 6: Playback History

### Architecture
```
data/local/db/
  entity/PlayHistoryEntity.kt      — Room entity
  dao/PlayHistoryDao.kt            — CRUD
data/repository/
  PlayHistoryRepositoryImpl.kt     — wraps DAO
domain/repository/
  PlayHistoryRepository.kt         — interface
ui/history/
  HistoryScreen.kt                 — list UI
  HistoryViewModel.kt              — state
```

### Data Model
```kotlin
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

### Behavior
- **Record:** In `PlayerManager.play()`, insert history entry on IO dispatcher
- **View:** Settings → "播放历史" entry → `HistoryScreen` (LazyColumn, sorted by playedAt DESC, grouped by date)
- **Actions:** Tap entry → play that audio; swipe/button to delete single; "清除全部" button in header
- **Retention:** No auto-cleanup (unless user manually clears)

### Files
- New: `PlayHistoryEntity.kt`, `PlayHistoryDao.kt`, `PlayHistoryRepository.kt`, `PlayHistoryRepositoryImpl.kt`
- New: `HistoryScreen.kt`, `HistoryViewModel.kt`
- Modify: `PlayerManager.kt` (insert on play), `SettingsScreen.kt` (add entry), `DatabaseModule.kt` (DAO binding), `DatabaseMigrations.kt` or version bump

---

## Feature 7: Trigger Pad Individual Volume

### Current state
- Parallel mode: each slot has its own `ExoPlayer` in `parallelPlayers: Map<Int, ExoPlayer>`
- All players use default volume 1.0 — no per-slot control

### Target behavior
- In parallel mode, each active pad shows a volume slider
- Volume per slot (0.0–1.0), persisted in memory (not DB — resets on app restart)
- Default 1.0 (full volume)

### Implementation
- **ViewModel:** Add `slotVolumes: MutableMap<Int, Float>` (slotIndex → volume). `startParallelSlot()` applies stored volume. `setSlotVolume(slotIndex, volume)` updates map and player.
- **UI:** In `PadButton`, when mode is Parallel AND slot is active, overlay a small vertical slider on the pad. Expand animation — pad slightly enlarges to reveal slider at bottom.
- **Files:** `TriggerPadViewModel.kt` (volume map, player volume set), `TriggerPadScreen.kt` (slider UI on PadButton)

---

## File Change Summary

| Feature | New Files | Modified Files |
|---|---|---|
| Audio Viz | — | `PlayScreen.kt` |
| Timer | — | `PlayScreen.kt`, `PlayViewModel.kt` |
| Image Slideshow | `ImageLibraryEntity.kt`, `ImageLibraryDao.kt`, `ImageLibraryRepository.kt`, `ImageLibraryRepositoryImpl.kt`, `ImageLibraryScreen.kt`, `ImageSlideshowViewModel.kt` | `PlayScreen.kt`, `DatabaseModule.kt` |
| History | `PlayHistoryEntity.kt`, `PlayHistoryDao.kt`, `PlayHistoryRepository.kt`, `PlayHistoryRepositoryImpl.kt`, `HistoryScreen.kt`, `HistoryViewModel.kt` | `PlayerManager.kt`, `SettingsScreen.kt`, `DatabaseModule.kt` |
| Trigger Vol | — | `TriggerPadViewModel.kt`, `TriggerPadScreen.kt` |

Total: ~12 new files, ~7 modified files.

---

## Implementation Order

1. **Audio visualization** (quick win, visible improvement)
2. **Timer** (small scope, high user value)
3. **Trigger volume** (small scope)
4. **Playback history** (new module, but straightforward)
5. **Image slideshow** (largest new feature, most complex)

---

## Risk Notes

- **Audio visualization performance:** Canvas draw on every waveform update (~30fps). Use `remember` for path calculation, clip to visible area, avoid recomposition in parent.
- **Image slideshow storage:** Large image libraries could consume app storage. Consider optional "keep original" vs "copy to app dir" toggle.
- **Database migration:** Two new tables (image_library, play_history) require Room version bump + migration.
