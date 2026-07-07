# UX 便捷操作增强 — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add desktop widget (1×1), headset control diagnostics, and app shortcut menu to reduce friction for ASMR users.

**Architecture:** Three independent features sharing only ShortcutReceiver (reactive bridge). Widget uses Android AppWidgetProvider + RemoteViews with broadcast-based updates from AsmrMediaService. Shortcuts use Android AppShortcutManager with intent extras parsed in MainActivity, routed via ShortcutReceiver StateFlow to AsmrNavHost. Headset controls need only 3 log lines added to existing MediaSession callback.

**Tech Stack:** Android AppWidgetProvider, RemoteViews, App Shortcuts (shortcuts.xml), MediaSessionCompat, StateFlow, Jetpack Compose navigation

## Global Constraints

- minSdk 26, targetSdk 35, compileSdk 35
- Follow existing patterns: `ShareReceiver`-style StateFlow bridge, `SubScreen`-based navigation
- Shortcut icons use Android built-in (`@android:drawable/`) where possible
- Widget layout in XML (RemoteViews), not Compose
- All strings in Chinese (match existing UI language)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/asmrhelper/util/ShortcutReceiver.kt` | **Create** | Reactive StateFlow bridge for shortcut actions |
| `app/src/main/res/xml/shortcuts.xml` | **Create** | Define 4 app shortcuts |
| `app/src/main/AndroidManifest.xml` | **Modify** | Register shortcuts meta-data + widget receiver |
| `app/src/main/java/com/asmrhelper/MainActivity.kt` | **Modify** | Parse shortcut intent extras → ShortcutReceiver |
| `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt` | **Modify** | Observe ShortcutReceiver → navigate + perform actions |
| `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt` | **Modify** | Add diagnostic logs to MediaSession callbacks + send widget broadcast |
| `app/src/main/java/com/asmrhelper/widget/AsmrWidgetProvider.kt` | **Create** | AppWidgetProvider: handle update + button clicks |
| `app/src/main/res/layout/widget_asmr.xml` | **Create** | Widget layout: play/pause button + title text |
| `app/src/main/res/xml/asmr_widget_info.xml` | **Create** | Widget metadata (size, update interval) |

---

### Task 1: ShortcutReceiver — Reactive Bridge

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/ShortcutReceiver.kt`

**Interfaces:**
- Produces: `ShortcutReceiver.pendingAction: StateFlow<String>` — observed by AsmrNavHost
- Produces: `ShortcutReceiver.receive(action: String)` — called by MainActivity
- Produces: `ShortcutReceiver.consume()` — called by AsmrNavHost after handling

- [ ] **Step 1: Create ShortcutReceiver.kt**

```kotlin
package com.asmrhelper.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive bridge between app shortcut intents and Compose navigation.
 * When the user taps a long-press shortcut, MainActivity posts the action here.
 * AsmrNavHost observes the flow and navigates accordingly.
 */
object ShortcutReceiver {
    private val _pendingAction = MutableStateFlow("")
    val pendingAction: StateFlow<String> = _pendingAction.asStateFlow()

    fun receive(action: String) {
        _pendingAction.value = action
    }

    /** Call after the action has been consumed by the UI. */
    fun consume() {
        _pendingAction.value = ""
    }
}
```

---

### Task 2: App Shortcuts XML + Manifest Registration

**Files:**
- Create: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: 4 shortcuts with intent extras consumed by MainActivity in Task 3
- Consumes: nothing

- [ ] **Step 1: Create shortcuts.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="resume"
        android:enabled="true"
        android:icon="@android:drawable/ic_media_play"
        android:shortcutShortLabel="@string/shortcut_resume"
        android:shortcutLongLabel="@string/shortcut_resume_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="${applicationId}"
            android:targetClass="com.asmrhelper.MainActivity">
            <extra android:name="shortcut_action" android:value="resume" />
        </intent>
    </shortcut>

    <shortcut
        android:shortcutId="timer30"
        android:enabled="true"
        android:icon="@android:drawable/ic_lock_idle_alarm"
        android:shortcutShortLabel="@string/shortcut_timer30"
        android:shortcutLongLabel="@string/shortcut_timer30_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="${applicationId}"
            android:targetClass="com.asmrhelper.MainActivity">
            <extra android:name="shortcut_action" android:value="timer30" />
        </intent>
    </shortcut>

    <shortcut
        android:shortcutId="favorites"
        android:enabled="true"
        android:icon="@android:drawable/btn_star_big_on"
        android:shortcutShortLabel="@string/shortcut_favorites"
        android:shortcutLongLabel="@string/shortcut_favorites_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="${applicationId}"
            android:targetClass="com.asmrhelper.MainActivity">
            <extra android:name="shortcut_action" android:value="favorites" />
        </intent>
    </shortcut>

    <shortcut
        android:shortcutId="history"
        android:enabled="true"
        android:icon="@android:drawable/ic_menu_recent_history"
        android:shortcutShortLabel="@string/shortcut_history"
        android:shortcutLongLabel="@string/shortcut_history_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="${applicationId}"
            android:targetClass="com.asmrhelper.MainActivity">
            <extra android:name="shortcut_action" android:value="history" />
        </intent>
    </shortcut>
</shortcuts>
```

- [ ] **Step 2: Add string resources**

Read `app/src/main/res/values/strings.xml` and append:

```xml
<string name="shortcut_resume">继续播放</string>
<string name="shortcut_resume_long">继续播放上次的音频</string>
<string name="shortcut_timer30">30分钟定时</string>
<string name="shortcut_timer30_long">设置30分钟定时后开始播放</string>
<string name="shortcut_favorites">收藏列表</string>
<string name="shortcut_favorites_long">查看收藏的音频</string>
<string name="shortcut_history">最近播放</string>
<string name="shortcut_history_long">查看播放历史记录</string>
```

- [ ] **Step 3: Register shortcuts in AndroidManifest.xml**

Add `<meta-data>` inside the `<activity android:name=".MainActivity">` element, after the existing intent-filter blocks:

```xml
<meta-data
    android:name="android.app.shortcuts"
    android:resource="@xml/shortcuts" />
```

---

### Task 3: MainActivity — Parse Shortcut Intents

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/MainActivity.kt`

**Interfaces:**
- Consumes: Intent extras from shortcuts.xml (Task 2)
- Produces: Calls `ShortcutReceiver.receive(action)` (Task 1), consumed by AsmrNavHost (Task 4)

- [ ] **Step 1: Add shortcut parsing in onCreate and onNewIntent**

In `MainActivity.kt`, add after the existing `handleShareIntent(intent)` call in `onCreate`:

```kotlin
handleShortcutIntent(intent)
```

In `onNewIntent`, add after `handleShareIntent(intent)`:

```kotlin
handleShortcutIntent(intent)
```

- [ ] **Step 2: Add handleShortcutIntent method and import**

Add import at top:
```kotlin
import com.asmrhelper.util.ShortcutReceiver
```

Add method at bottom of class:
```kotlin
private fun handleShortcutIntent(intent: Intent?) {
    val action = intent?.getStringExtra("shortcut_action") ?: return
    ShortcutReceiver.receive(action)
}
```

---

### Task 4: AsmrNavHost — Handle Shortcut Navigation

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`

**Interfaces:**
- Consumes: `ShortcutReceiver.pendingAction` (Task 1)
- Produces: Navigation state changes + PlayViewModel actions

- [ ] **Step 1: Read current AsmrNavHost.kt**

The relevant section is lines 40-56 (the share intent observation pattern). We replicate this pattern for shortcuts.

- [ ] **Step 2: Add shortcut observation and handling**

Add import at top:
```kotlin
import com.asmrhelper.util.ShortcutReceiver
```

After the share URL observation block (after `LaunchedEffect(shareUrl) { ... }`), add:

```kotlin
// Observe app shortcut actions and auto-navigate
val shortcutAction by ShortcutReceiver.pendingAction.collectAsStateWithLifecycle()
LaunchedEffect(shortcutAction) {
    when (shortcutAction) {
        "resume" -> {
            currentScreen = Screen.Play
            playSubScreen = null
            val audio = playViewModel.playerManager.loadLastPlayback()
            if (audio != null) {
                playViewModel.play(audio)
            }
        }
        "timer30" -> {
            currentScreen = Screen.Play
            playSubScreen = null
            playViewModel.setTimerSeconds(30 * 60)
            val audio = playViewModel.playerManager.loadLastPlayback()
            if (audio != null) {
                playViewModel.play(audio)
            }
        }
        "favorites" -> {
            currentScreen = Screen.Play
            libraryInitialTab = 1 // "我的收藏" tab
            playSubScreen = SubScreen.Library
        }
        "history" -> {
            currentScreen = Screen.Settings
            settingsSubScreen = SubScreen.History
        }
    }
    if (shortcutAction.isNotEmpty()) {
        ShortcutReceiver.consume()
    }
}
```

Note: `playViewModel.playerManager` is currently private. Need to check access.

- [ ] **Step 3: Verify PlayViewModel exposes PlayerManager or add a helper**

Check `PlayViewModel.kt` — if `playerManager` is private, add this method to `PlayViewModel`:

```kotlin
/** Load the last-played audio info for shortcut resume. */
fun loadLastPlayback(): Audio? = playerManager.loadLastPlayback()
```

---

### Task 5: Headset Control Diagnostic Logging

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt`

**Interfaces:**
- Consumes: Existing MediaSession callback structure
- Produces: Logcat output for diagnostics

The `setupMediaSession()` method already has all callbacks registered. Only need to add 3 log lines.

- [ ] **Step 1: Add diagnostic logs to MediaSession callbacks**

In `setupMediaSession()`, modify the callback block:

```kotlin
setCallback(object : MediaSessionCompat.Callback() {
    override fun onPlay() {
        android.util.Log.d("AsmrMedia", "Headset: PLAY received")
        playerManager.handleEvent(PlayerEvent.Resume)
    }
    override fun onPause() {
        android.util.Log.d("AsmrMedia", "Headset: PAUSE received")
        playerManager.handleEvent(PlayerEvent.Pause)
    }
    override fun onSkipToNext() {
        android.util.Log.d("AsmrMedia", "Headset: SKIP_NEXT received")
        playerManager.handleEvent(PlayerEvent.Next)
    }
    override fun onSkipToPrevious() {
        android.util.Log.d("AsmrMedia", "Headset: SKIP_PREV received")
        playerManager.handleEvent(PlayerEvent.Previous)
    }
    override fun onStop() = stopSelf()
})
```

Note: The existing callbacks at lines 257-263 already call the right handlers — only adding log lines. SKIP_TO_PREVIOUS is already declared in both `setupMediaSession()` (line 252) and `updateMediaSessionState()` (line 278) — no action declaration changes needed.

---

### Task 6: Desktop Widget (1×1)

**Files:**
- Create: `app/src/main/java/com/asmrhelper/widget/AsmrWidgetProvider.kt`
- Create: `app/src/main/res/layout/widget_asmr.xml`
- Create: `app/src/main/res/xml/asmr_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml` — register widget receiver
- Modify: `app/src/main/java/com/asmrhelper/player/AsmrMediaService.kt` — send update broadcast

**Interfaces:**
- Consumes: Service broadcasts with `"com.asmrhelper.WIDGET_UPDATE"` action + extras `isPlaying: Boolean`, `title: String`
- Produces: Widget on home screen with play/pause button

- [ ] **Step 1: Create widget layout XML**

File: `app/src/main/res/layout/widget_asmr.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="#CC1A1A2E"
    android:padding="8dp">

    <ImageButton
        android:id="@+id/widget_play_pause"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@android:drawable/ic_media_play"
        android:background="@drawable/widget_button_bg"
        android:scaleType="centerInside"
        android:contentDescription="播放/暂停" />

    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="ASMRHelper"
        android:textColor="#E0E0E0"
        android:textSize="10sp"
        android:maxLines="1"
        android:ellipsize="end"
        android:gravity="center" />

</LinearLayout>
```

- [ ] **Step 2: Create widget button background drawable**

File: `app/src/main/res/drawable/widget_button_bg.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#BB86FC" />
</shape>
```

- [ ] **Step 3: Create widget info XML**

File: `app/src/main/res/xml/asmr_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_asmr"
    android:resizeMode="none"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

- [ ] **Step 4: Add widget description string**

In `app/src/main/res/values/strings.xml`:

```xml
<string name="widget_description">ASMRHelper 桌面快捷播放控制</string>
```

- [ ] **Step 5: Create AsmrWidgetProvider.kt**

File: `app/src/main/java/com/asmrhelper/widget/AsmrWidgetProvider.kt`

```kotlin
package com.asmrhelper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.media.session.MediaButtonReceiver
import com.asmrhelper.MainActivity
import com.asmrhelper.R

class AsmrWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE = "com.asmrhelper.WIDGET_UPDATE"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TITLE = "title"

        /**
         * Called from AsmrMediaService when playback state changes.
         * Updates all widget instances to reflect current state.
         */
        fun notifyStateChanged(context: Context, isPlaying: Boolean, title: String) {
            val intent = Intent(context, AsmrWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TITLE, title)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "ASMRHelper"
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, AsmrWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, appWidgetManager, id, isPlaying, title)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            // Initial state: paused, default title
            updateWidget(context, appWidgetManager, id, false, "ASMRHelper")
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        isPlaying: Boolean,
        title: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_asmr)

        // Title
        views.setTextViewText(R.id.widget_title, title)

        // Play/Pause button icon
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause
                      else android.R.drawable.ic_media_play
        views.setImageResource(R.id.widget_play_pause, iconRes)

        // Play/Pause button action
        val action = if (isPlaying) PlaybackStateCompat.ACTION_PAUSE
                     else PlaybackStateCompat.ACTION_PLAY
        val mediaIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, action)
        views.setOnClickPendingIntent(R.id.widget_play_pause, mediaIntent)

        // Click on widget background → open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Apply to the root layout (entire widget tappable except the button)
        views.setOnClickPendingIntent(android.R.id.content, openPendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
```

- [ ] **Step 6: Register widget provider in AndroidManifest.xml**

Add inside `<application>`, after the existing service:

```xml
<receiver
    android:name=".widget.AsmrWidgetProvider"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
        <action android:name="com.asmrhelper.WIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/asmr_widget_info" />
</receiver>
```

- [ ] **Step 7: Add widget update broadcast in AsmrMediaService**

In `AsmrMediaService.kt`, modify the state listener in `onCreate()` to also send widget broadcasts. After the existing listener block (lines 56-60):

Replace:
```kotlin
playerManager.setStateListener { state ->
    updateNotification(state)
    updateMediaSessionState(state)
    manageWakeLock(state.isPlaying)
}
```

With:
```kotlin
playerManager.setStateListener { state ->
    updateNotification(state)
    updateMediaSessionState(state)
    manageWakeLock(state.isPlaying)
    // Notify widget of state change
    com.asmrhelper.widget.AsmrWidgetProvider.notifyStateChanged(
        this, state.isPlaying, state.currentAudio?.title ?: "ASMRHelper"
    )
}
```

---

### Task 7: Build and Verify

**Files:** None (build step only)

- [ ] **Step 1: Clean rebuild APK**

```bash
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL, no compilation errors.

- [ ] **Step 2: Verify APK output exists**

```bash
dir app\build\outputs\apk\debug\app-debug.apk
```

Expected: File exists with recent timestamp.

---

## Verification Checklist

After installing the APK:

### Shortcuts
- [ ] Long-press app icon → 4 shortcuts appear with correct labels
- [ ] Tap "继续播放" → app opens to Play screen, last audio resumes
- [ ] Tap "30分钟定时" → app opens to Play screen, timer set to 30:00, audio plays
- [ ] Tap "收藏列表" → app opens to Library → "我的收藏" tab
- [ ] Tap "最近播放" → app opens to Settings → History screen

### Headset Controls
- [ ] Run `adb logcat -s AsmrMedia:D` and press headset buttons
- [ ] PLAY/PAUSE/NEXT/PREV log lines appear for each press
- [ ] Audio responds correctly to each button

### Widget
- [ ] Long-press home screen → Widgets → ASMRHelper → drag to home screen
- [ ] Widget shows play button + "ASMRHelper" title
- [ ] Play audio in app → widget button changes to pause icon + shows title
- [ ] Tap widget play/pause → audio toggles in app
- [ ] Tap widget background → app opens
