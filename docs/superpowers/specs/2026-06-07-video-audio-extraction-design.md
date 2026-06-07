# Video Audio Extraction Feature — Design Spec

**Date:** 2026-06-07  
**Status:** Approved, pending implementation plan

## Overview

Allow users to extract audio from video URLs (Douyin, Bilibili, YouTube, etc.) and play them within ASMRHelper. The extracted audio is stored persistently and managed in a dedicated "Video Audio" tab.

## Key Decisions

| Dimension | Decision |
|-----------|----------|
| Entry points | System Share Intent + paste URL dialog |
| Extraction engine | youtubedl-android (local yt-dlp wrapper for Android) |
| Audio management | Separate "Video Audio" tab in the library |
| Deletion | Confirmation dialog with optional cache file removal |
| Storage | Room persistence + filesDir m4a files |
| Playback | Reuse existing Media3 ExoPlayer |

## Architecture

```
Entry Layer
  ├── Share Intent receiver → DownloadDialog with pre-filled URL
  └── FAB "+" in Video Audio tab → DownloadDialog with URL input
              │
              ▼
VideoAudioExtractor (yt-dlp-android wrapper)
  ├── Parse video info (title, duration, platform, cover)
  ├── Download audio stream only (-x --audio-format m4a)
  └── Progress callbacks → DownloadManager
              │
              ▼
Storage Layer
  ├── Room: VideoAudioEntity
  ├── Files: filesDir/video_audio/{timestamp}_{title}.m4a
  └── SharedPreferences: deleted URLs set (prevent re-import)
              │
              ▼
Playback Layer
  └── Reuse existing PlayerManager (ExoPlayer supports m4a natively)
```

## Data Model

```kotlin
@Entity(tableName = "video_audio")
data class VideoAudioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platform: String,       // "bilibili" | "youtube" | "douyin" | "other"
    val sourceUrl: String,
    val filePath: String,       // extracted m4a path
    val coverPath: String?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

## UI Flow

### Entry 1: System Share
1. User taps "Share" in external app → selects "ASMRHelper"
2. DownloadDialog opens with pre-filled URL
3. User confirms → extraction begins with progress UI
4. On complete → Toast + audio appears in "Video Audio" tab

### Entry 2: Paste URL
1. User navigates to Library → "Video Audio" tab
2. Taps FAB "+" → DownloadDialog opens with empty URL input
3. Same extraction flow as above

### Download Dialog
- Shows video title, platform icon, progress bar with percentage
- Cancel button to abort and clean up partial files

### Video Audio Tab
- Each card: platform icon, title, source link (tappable), duration
- Actions: Play (tap card), Favorite (heart icon), Delete (trash icon)
- Delete confirmation: "Remove" only vs "Remove + delete cache file" checkbox

### Deletion Dialog
```
┌──────────────────────────────────┐
│          Confirm Delete           │
│                                  │
│   Remove "{title}" from app?     │
│                                  │
│   ☐ Also delete local cache file │
│                                  │
│     [Remove]          [Cancel]   │
└──────────────────────────────────┘
```
- Unchecked: delete DB record + add URL to deleted set, keep m4a file
- Checked: above + delete m4a file

## Download Engine

- **Library:** youtubedl-android (https://github.com/yausername/youtubedl-android)
- **Command:** `yt-dlp -x --audio-format m4a -o <path> <url>`
- **Pre-extraction:** `yt-dlp --dump-json <url>` for metadata
- **Download manager:** Singleton, max 1 concurrent download, StateFlow for status

## Error Handling

| Scenario | Response |
|----------|----------|
| Invalid/unsupported URL | Toast: "Unsupported platform or invalid link" |
| Network timeout (30s) | Toast: "Connection timed out, please retry" + cleanup |
| Disk space insufficient | Pre-check, Toast: "Insufficient storage space" |
| User cancel | Delete partial file, skip DB write |
| yt-dlp parse failure | Toast: "Cannot extract audio, video may be protected" |
| App killed during download | Download aborts; residual temp files cleaned on next launch |

## Integration Points

### Modified Files
| File | Change |
|------|--------|
| `LibraryScreen.kt` | Add 4th tab "Video Audio" |
| `FavoritesViewModel.kt` | Add video audio state + delete logic |
| `PlayViewModel.kt` | Support playing VideoAudio via ExoPlayer |
| `PlayScreen.kt` | Add "Video Audio" to dropdown menu |
| `AsmrNavHost.kt` | New navigation route |

### New Files
| File | Purpose |
|------|---------|
| `data/local/db/entity/VideoAudioEntity.kt` | Room entity |
| `data/local/db/dao/VideoAudioDao.kt` | CRUD operations |
| `domain/model/VideoAudio.kt` | Domain model |
| `player/VideoAudioExtractor.kt` | yt-dlp wrapper |
| `player/DownloadManager.kt` | Download queue + status |
| `ui/library/VideoAudioTab.kt` | Video audio list UI |
| `ui/library/DownloadDialog.kt` | URL input + download progress UI |
| `di/` modifications | Register new Daos, Repository, DownloadManager |

### New Dependency
```kotlin
// youtubedl-android for video audio extraction
implementation("com.github.yausername:youtubedl-android:latest")
```

## Permissions
- `INTERNET` (already declared in AndroidManifest.xml)
- No additional storage permissions required (writes to `filesDir`)
