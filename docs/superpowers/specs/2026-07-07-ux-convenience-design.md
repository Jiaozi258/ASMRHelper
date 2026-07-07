# UX 便捷操作增强 — Design Spec

> **Date:** 2026-07-07
> **Status:** Approved

## Overview

3 features to reduce friction for ASMR users who often operate the app in bed/dark: desktop widget, headset control verification, and app shortcut menu.

---

## Feature 1: Desktop Widget (1×1)

### Purpose
User can play/pause the last audio directly from the home screen without opening the app.

### Implementation

**Technology:** Android `AppWidgetProvider` + `RemoteViews` (no Glance — 1×1 layout is trivial and Glance adds unnecessary APK size).

**Layout:**
```
┌──────────────┐
│     ▶️/⏸      │  ← circular play/pause button (accent color)
│   歌曲标题    │  ← single line, elide end, 10sp
└──────────────┘
```

**Play/Pause button states:**
- Playing → show pause icon ⏸
- Paused/stopped → show play icon ▶️
- Unknown (after phone reboot) → show play icon ▶️

**Click targets:**
- Button area → toggle play/pause via `ACTION_MEDIA_BUTTON` PendingIntent
- Rest of widget → open MainActivity

**Data flow:**
```
PlayerManager.state → AsmrMediaService.onStateChanged
  → sendBroadcast("com.asmrhelper.WIDGET_UPDATE", {isPlaying, title})
  → AsmrWidgetProvider.onReceive() → AppWidgetManager.updateAppWidget()
```

**Files:**
- `app/src/main/java/com/asmrhelper/widget/AsmrWidgetProvider.kt` — new
- `app/src/main/res/xml/asmr_widget_info.xml` — new
- `app/src/main/res/layout/widget_asmr.xml` — new
- `app/src/main/AndroidManifest.xml` — register receiver
- `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt` — send broadcast on state change

**Edge cases:**
- After phone reboot → widget shows blank state, tap opens app
- Service not running → tap opens app instead of doing nothing
- No last-played audio → widget shows "ASMRHelper" as title

---

## Feature 2: Headset/Bluetooth Control Verification

### Purpose
Confirm that earphone buttons (play/pause/next/prev) work reliably, and add missing `SKIP_TO_PREVIOUS` action declaration.

### Implementation

**Root cause of potential issues:**
- `updateMediaSessionState()` declares actions in `PlaybackStateCompat` but `ACTION_SKIP_TO_PREVIOUS` was missing
- Without declaring it, the system won't send that intent to the service

**Changes:**
1. Add `ACTION_SKIP_TO_PREVIOUS` to the actions set in `updateMediaSessionState()`
2. Add diagnostic logging in MediaSession callbacks so the user can verify via `adb logcat` that button presses are received

**Files:**
- `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt` — add action + log

**No new files needed.**

---

## Feature 3: App Shortcuts (Long-Press Menu)

### Purpose
Long-press the app icon to jump directly into common actions.

### Implementation

**Technology:** Android App Shortcuts (`ShortcutManager` + `shortcuts.xml`)

**4 shortcuts:**

| id | Icon | Label | Intent extra | App behavior |
|----|------|-------|-------------|--------------|
| `resume` | ▶️ `ic_media_play` | 继续播放 | `action=resume` | Open app + auto-play last audio |
| `timer30` | ⏱️ `ic_timer` (custom) | 30分钟定时 | `action=timer30` | Open app + set 30min sleep timer + play |
| `favorites` | ❤️ `ic_favorite` | 收藏列表 | `action=favorites` | Open app + navigate to favorites playlist |
| `history` | 🕐 `ic_history` | 最近播放 | `action=history` | Open app + navigate to playback history |

**Shortcut XML references:**
- Use Android built-in icons via `@android:drawable/` where possible:
  - `ic_media_play` → `@android:drawable/ic_media_play`
  - `ic_menu_recent_history` → `@android:drawable/ic_menu_recent_history`
- Use app resources for timer and favorite icons

**Intent handling in MainActivity:**
```
onCreate / onNewIntent → check intent.getStringExtra("shortcut_action")
  → set initial navigation target via state
  → AsmrNavHost observes and navigates accordingly
```

**Files:**
- `app/src/main/res/xml/shortcuts.xml` — new
- `app/src/main/AndroidManifest.xml` — add `<meta-data>` to launcher activity
- `app/src/main/java/com/asmrhelper/MainActivity.kt` — parse shortcut action
- `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt` — observe shortcut action + navigate

### Shortcut action flow

```
User long-presses icon → taps shortcut
  → Launcher sends Intent with extra
  → MainActivity.onCreate/onNewIntent parses extra
  → Sets navigation target in state
  → AsmrNavHost reads target → navigates + performs action
```

**Edge cases:**
- "继续播放" with no history → navigate to Play screen (blank/default state)
- "收藏列表" with no favorites → navigate to library showing empty state
- "30分钟定时" → always works; set timer regardless of whether anything is playing
- Shortcut invoked while app already running → `onNewIntent` handles it

---

## Spec Self-Review

- ✅ No placeholders or TBDs
- ✅ No contradictions between sections
- ✅ Scope is focused — 3 small features sharing nothing more than a single intent-extra mechanism
- ✅ Ambiguity check: all behaviors described with edge cases
- ✅ All three features are independent — can be implemented, tested, and verified separately
- ✅ Existing architecture respected — widget uses proven AppWidgetProvider pattern; shortcuts use standard Android ShortcutManager; headset controls are a 2-line fix
