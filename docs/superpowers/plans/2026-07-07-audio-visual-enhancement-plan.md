# Audio Richness + Visual Ambiance — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add built-in nature sounds (6 OGG loops), 3-band EQ, particle effects/breathing glow, and real-time waveform via AudioProcessor.

**Architecture:** Four independent features. B1 extends the existing ambient audio system with built-in resources. B2 wraps Android's Equalizer API in a new controller. C1 is a pure Compose Canvas composable. C2 adds a Media3 AudioProcessor that intercepts PCM frames during playback — zero extra decode cost, replaces the simulated sine-wave fallback.

**Tech Stack:** Android Equalizer API, Media3 AudioProcessor, Compose Canvas + Animatable, Room/SharedPreferences

## Global Constraints

- minSdk 26, targetSdk 35, compileSdk 35
- Follow existing patterns: controller classes in `player/`, composables in `ui/components/`
- All strings in Chinese
- 6 OGG files already in `res/raw/`
- Background player (not main) plays ambient sounds

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/asmrhelper/domain/model/AmbientSource.kt` | **Create** | Data model: built-in vs user-imported ambient sounds |
| `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` | **Modify** | Expose built-in ambient list, handle source-type-aware selection |
| `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` | **Modify** | Show built-in entries in ambient dialog, add EQ UI, add AmbianceOverlay |
| `app/src/main/java/com/asmrhelper/player/EqualizerController.kt` | **Create** | Wraps Android Equalizer, exposes 3-band levels as StateFlow |
| `app/src/main/java/com/asmrhelper/di/PlayerModule.kt` | **Modify** | Provide EqualizerController |
| `app/src/main/java/com/asmrhelper/ui/components/AmbianceOverlay.kt` | **Create** | Canvas particle system + breathing glow |
| `app/src/main/java/com/asmrhelper/player/WaveformCaptureProcessor.kt` | **Create** | AudioProcessor that captures amplitude in real-time |
| `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt` | **Modify** | Add "播放界面特效" toggle |
| `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt` | **Modify** | Persist effects toggle pref |
| `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt` | **Modify** | Initialize built-in ambient entries on first launch |

---

### Task 1: AmbientSource Model + Built-In Ambient Registration

**Files:**
- Create: `app/src/main/java/com/asmrhelper/domain/model/AmbientSource.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt:86-111`

**Interfaces:**
- Produces: `AmbientSource` sealed class consumed by Task 2 (PlayViewModel)
- Produces: `SettingsRepositoryImpl` now seeds built-in entries into `_ambientAudios` on init

- [ ] **Step 1: Create AmbientSource.kt**

```kotlin
package com.asmrhelper.domain.model

/**
 * Represents an ambient sound source — either a user-imported file
 * or a built-in OGG resource shipped with the app.
 */
data class AmbientSource(
    val label: String,
    val sourcePath: String,   // file path for playback (cache path or user path)
    val isBuiltIn: Boolean
) {
    companion object {
        /** 6 built-in nature sound loops in res/raw/. */
        val BUILT_IN = listOf(
            AmbientSource("🌧️ 雨声",     "builtin:ambient_rain",     isBuiltIn = true),
            AmbientSource("🌊 溪流",     "builtin:ambient_stream",   isBuiltIn = true),
            AmbientSource("🔥 篝火",     "builtin:ambient_campfire", isBuiltIn = true),
            AmbientSource("💨 风声",     "builtin:ambient_wind",     isBuiltIn = true),
            AmbientSource("⚡ 雷声",     "builtin:ambient_thunder",  isBuiltIn = true),
            AmbientSource("🌊 海浪",     "builtin:ambient_ocean",    isBuiltIn = true),
        )
    }
}
```

- [ ] **Step 2: Modify SettingsRepositoryImpl to seed built-in entries**

Read `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt`. The current `_ambientAudios` is a `MutableStateFlow<List<String>>`. We need to add a companion list of built-in resource IDs and export them.

Add a new function and expose built-in list:

```kotlin
// At class level, add after _ambientAudios:
private val builtInAmbientResIds = mapOf(
    "builtin:ambient_rain" to R.raw.ambient_rain,
    "builtin:ambient_stream" to R.raw.ambient_stream,
    "builtin:ambient_campfire" to R.raw.ambient_campfire,
    "builtin:ambient_wind" to R.raw.ambient_wind,
    "builtin:ambient_thunder" to R.raw.ambient_thunder,
    "builtin:ambient_ocean" to R.raw.ambient_ocean,
)

/** Returns the res/raw ID for a built-in ambient, or 0 if not found. */
fun getBuiltInResId(path: String): Int = builtInAmbientResIds[path] ?: 0

/** All built-in ambient paths, always available. */
fun getBuiltInAmbients(): List<String> = builtInAmbientResIds.keys.toList()
```

Note: The `getAmbientAudios()` flow remains unchanged — it returns user-imported paths. The built-in list is fetched separately via `getBuiltInAmbients()`.

---

### Task 2: PlayViewModel + PlayScreen — Show Built-In Ambients

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt:383-403`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt:841-919`

**Interfaces:**
- Consumes: `AmbientSource.BUILT_IN` (Task 1), `SettingsRepositoryImpl.getBuiltInResId()` (Task 1)
- Produces: Built-in entries visible in ambient sound dialog

- [ ] **Step 1: Add built-in ambient handling in PlayViewModel**

Read the `PlayViewModel` around lines 383-403. Add a new function to handle built-in ambient selection:

```kotlin
/** Built-in ambient paths (always available alongside user imports). */
fun getBuiltInAmbients(): List<AmbientSource> = AmbientSource.BUILT_IN

/** Play a built-in ambient from res/raw. Copies to cache on first use. */
fun playBuiltInAmbient(source: AmbientSource) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val resId = settingsRepository.getBuiltInResId(source.sourcePath)
            if (resId == 0) return@launch

            // Copy res/raw to cache so ExoPlayer can play it as a file URI
            val cacheFile = java.io.File(context.cacheDir, source.sourcePath.substringAfter("builtin:"))
            if (!cacheFile.exists()) {
                context.resources.openRawResource(resId).use { input ->
                    java.io.FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                playerManager.handleEvent(
                    PlayerEvent.SetBackgroundAudio(cacheFile.absolutePath)
                )
            }
        } catch (_: Exception) { }
    }
}
```

Need to add `context` injection — the PlayViewModel doesn't currently have `Context`. Add `@ApplicationContext private val context: Context` to constructor, and `import android.content.Context`, `import dagger.hilt.android.qualifiers.ApplicationContext`.

Also need to add `import com.asmrhelper.domain.model.AmbientSource` and `import kotlinx.coroutines.Dispatchers`, `import kotlinx.coroutines.withContext`.

- [ ] **Step 2: Update ambient dialog in PlayScreen to show built-in entries**

Read PlayScreen lines 841-919 (ambient dialog section). Add built-in entries above the user-imported list. In the dialog's `text` block, after `Column(...)` opening, before `if (state.ambientAudios.isEmpty())`:

```kotlin
// Built-in ambient sounds (always available)
Text(
    "内置音效",
    color = TextHint,
    fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
    modifier = Modifier.padding(bottom = 4.dp)
)
AmbientSource.BUILT_IN.forEach { source ->
    val isSelected = state.playerState.isBackgroundPlaying &&
                     state.selectedAmbientPath == source.sourcePath
    TextButton(
        onClick = {
            viewModel.playBuiltInAmbient(source)
            showAmbientDialog = false
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = source.label,
                color = if (isSelected) LocalAccentColor.current else TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (isSelected) "▶ 播放中" else "",
                color = LocalAccentColor.current,
                fontSize = 11.sp
            )
        }
    }
}

HorizontalDivider(
    color = DarkSurfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.padding(vertical = 8.dp)
)

Text(
    "导入的环境音",
    color = TextHint,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    modifier = Modifier.padding(bottom = 4.dp)
)
```

Add import at top of PlayScreen: `import com.asmrhelper.domain.model.AmbientSource`

Also add `import androidx.compose.ui.text.font.FontWeight` if not already imported.

---

### Task 3: EqualizerController

**Files:**
- Create: `app/src/main/java/com/asmrhelper/player/EqualizerController.kt`
- Modify: `app/src/main/java/com/asmrhelper/di/PlayerModule.kt`

**Interfaces:**
- Consumes: mainPlayer audio session ID
- Produces: `EqualizerController.bandLevels: StateFlow<List<Float>>`, `isEnabled: StateFlow<Boolean>`, `setBandLevel(band: Int, levelDb: Float)`, `reset()`

- [ ] **Step 1: Create EqualizerController.kt**

```kotlin
package com.asmrhelper.player

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.di.MainPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerController @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var equalizer: Equalizer? = null

    private val _bandLevels = MutableStateFlow(listOf(0f, 0f, 0f))
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    /** Band center frequencies in mHz. */
    val bandFrequencies = listOf(250_000, 1_500_000, 8_000_000)

    /** Human-readable band names. */
    val bandLabels = listOf("低音", "中音", "高音")

    init {
        scope.launch {
            // Retry until audio session is ready
            var attempts = 0
            while (attempts < 20) {
                val sessionId = mainPlayer.audioSessionId
                if (sessionId > 0) {
                    try {
                        val eq = Equalizer(0, sessionId).apply { enabled = true }
                        // Configure 3 bands at our desired center frequencies
                        val numBands = eq.numberOfBands.toInt()
                        if (numBands >= 3) {
                            // Find bands closest to our targets
                            val bandMap = mutableListOf<Short>()
                            for (target in bandFrequencies) {
                                var closest: Short = 0
                                var closestDiff = Int.MAX_VALUE
                                for (b in 0 until numBands) {
                                    val cf = eq.getCenterFreq(b.toShort()) / 1000
                                    val diff = kotlin.math.abs(cf - target / 1000)
                                    if (diff < closestDiff) {
                                        closestDiff = diff
                                        closest = b.toShort()
                                    }
                                }
                                bandMap.add(closest)
                            }
                            // Store the actual band indices we'll use
                            // (we just use the native presets — bands 0, 1, 2 if available)
                            for (b in 0 until 3.coerceAtMost(numBands)) {
                                eq.setBandLevel(b.toShort(), 0)
                            }
                        }
                        equalizer = eq
                        _isEnabled.value = true
                        break
                    } catch (_: Exception) {
                        _isEnabled.value = false
                    }
                }
                attempts++
                delay(500L)
            }
        }
    }

    fun setBandLevel(band: Int, levelDb: Float) {
        val clamped = levelDb.coerceIn(-10f, 10f)
        val newLevels = _bandLevels.value.toMutableList()
        if (band in newLevels.indices) {
            newLevels[band] = clamped
            _bandLevels.value = newLevels
        }
        try {
            equalizer?.setBandLevel(band.toShort(), (clamped * 100).toShort())
        } catch (_: Exception) { }
    }

    fun reset() {
        for (i in 0 until 3) {
            try { equalizer?.setBandLevel(i.toShort(), 0) } catch (_: Exception) { }
        }
        _bandLevels.value = listOf(0f, 0f, 0f)
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) { }
        equalizer = null
        _isEnabled.value = false
    }
}
```

- [ ] **Step 2: Provide EqualizerController in PlayerModule**

Read `app/src/main/java/com/asmrhelper/di/PlayerModule.kt`. Add after the `provideBinauralBeatEngine` function:

```kotlin
@Provides
@Singleton
fun provideEqualizerController(
    @MainPlayer mainPlayer: ExoPlayer
): EqualizerController = EqualizerController(mainPlayer)
```

Also add import: `import com.asmrhelper.player.EqualizerController`

---

### Task 4: EQ UI in PlayScreen

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — add EQ section
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` — delegate to EqualizerController

**Interfaces:**
- Consumes: `EqualizerController` (Task 3)
- Produces: EQ sliders visible on PlayScreen

- [ ] **Step 1: Add EqualizerController to PlayViewModel constructor**

Read `PlayViewModel` constructor (lines 97-108). Add parameter:

```kotlin
private val equalizerController: EqualizerController,
```

And add import: `import com.asmrhelper.player.EqualizerController`

- [ ] **Step 2: Expose EQ state from PlayViewModel**

Add properties in PlayViewModel body:

```kotlin
val eqBandLevels: StateFlow<List<Float>> = equalizerController.bandLevels
val eqEnabled: StateFlow<Boolean> = equalizerController.isEnabled
```

- [ ] **Step 3: Add EQ UI section to PlayScreen**

Find a good insertion point in PlayScreen — after the timer/pomodoro section, before the action buttons. Add a collapsible EQ card. The exact insertion point is around line 630 (after pomodoro section closes). Add:

```kotlin
// ── Equalizer ──
val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
val eqLevels by viewModel.eqBandLevels.collectAsStateWithLifecycle()
var eqExpanded by remember { mutableStateOf(false) }

if (eqEnabled) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.3f))
            .clickable { eqExpanded = !eqExpanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎚️ 均衡器", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(
            if (eqExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            null, tint = TextHint, modifier = Modifier.size(20.dp)
        )
    }

    AnimatedVisibility(visible = eqExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val labels = listOf("低音", "中音", "高音")
                eqLevels.forEachIndexed { i, level ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(labels[i], color = TextSecondary, fontSize = 12.sp)
                        Slider(
                            value = level,
                            onValueChange = { equalizerController.setBandLevel(i, it) },
                            valueRange = -10f..10f,
                            modifier = Modifier.height(120.dp).then(
                                Modifier
                            ),
                            // vertical slider workaround
                        )
                        Text(
                            "${if (level >= 0) "+" else ""}${"%.0f".format(level)}dB",
                            color = if (level != 0f) LocalAccentColor.current else TextHint,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            TextButton(
                onClick = { equalizerController.reset() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("重置", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
```

Wait — Slider in Compose is horizontal by default. For vertical sliders we need rotation. Simpler approach: use horizontal sliders with labels, arranged in a column of 3 rows.

Revised EQ UI (simpler, horizontal sliders in a column):

```kotlin
// ── Equalizer ──
val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
val eqLevels by viewModel.eqBandLevels.collectAsStateWithLifecycle()
var eqExpanded by remember { mutableStateOf(false) }

if (eqEnabled) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.3f))
            .clickable { eqExpanded = !eqExpanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎚️ 均衡器", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Icon(
            if (eqExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            null, tint = TextHint, modifier = Modifier.size(20.dp)
        )
    }

    AnimatedVisibility(visible = eqExpanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val bandLabels = listOf("🔈 低音", "🎵 中音", "🔔 高音")
            eqLevels.forEachIndexed { i, level ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(bandLabels[i], color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = level,
                        onValueChange = { viewModel.setEqBand(i, it) },
                        valueRange = -10f..10f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = LocalAccentColor.current,
                            activeTrackColor = LocalAccentColor.current
                        )
                    )
                    Text(
                        "${if (level >= 0) "+" else ""}${"%.0f".format(level)}",
                        color = if (level != 0f) LocalAccentColor.current else TextHint,
                        fontSize = 11.sp,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
            TextButton(
                onClick = { viewModel.resetEq() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("重置", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
```

- [ ] **Step 4: Add delegate methods in PlayViewModel**

```kotlin
fun setEqBand(band: Int, levelDb: Float) = equalizerController.setBandLevel(band, levelDb)
fun resetEq() = equalizerController.reset()
```

---

### Task 5: AmbianceOverlay — Particle Effects + Breathing Glow

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/components/AmbianceOverlay.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — add behind content
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt` — add toggle
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt` — persist pref

**Interfaces:**
- Consumes: `isPlaying: Boolean`, `accentColor: Color`
- Produces: `AmbianceOverlay` composable rendered behind all interactive UI

- [ ] **Step 1: Create AmbianceOverlay.kt**

```kotlin
package com.asmrhelper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Immersive particle system + breathing glow for the ASMR play screen.
 * Particles float upward; glow pulses when playing.
 */
@Composable
fun AmbianceOverlay(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // --- Breathing glow animation ---
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val effectiveGlowAlpha = if (isPlaying) glowAlpha else 0f

    // --- Particles ---
    data class Particle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float,
        val alpha: Float,
        val phase: Float
    )

    val particles = remember {
        (0 until 12).map {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = 0.2f + Random.nextFloat() * 0.4f,
                size = 2f + Random.nextFloat() * 4f,
                alpha = 0.1f + Random.nextFloat() * 0.3f,
                phase = Random.nextFloat() * 6.28f
            )
        }.toMutableList()
    }

    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { } // wait for next frame
            time += 0.016f
            // Update particles
            for (p in particles) {
                p.y -= p.speed * 0.003f
                p.x += sin(time * 2f + p.phase) * 0.0015f
                if (p.y < -0.05f) {
                    p.y = 1.05f
                    p.x = Random.nextFloat()
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // --- Breathing glow ---
        if (effectiveGlowAlpha > 0.01f) {
            val glowRadius = w * 0.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = effectiveGlowAlpha),
                        accentColor.copy(alpha = effectiveGlowAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )
        }

        // --- Particles ---
        for (p in particles) {
            val px = p.x * w
            val py = p.y * h
            drawCircle(
                color = accentColor.copy(alpha = p.alpha * (if (isPlaying) 1f else 0f)),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}
```

- [ ] **Step 2: Add AmbianceOverlay to PlayScreen**

Read PlayScreen around line 119 (after the Box opening). Add AmbianceOverlay behind all content but above the background:

In PlayScreen composable, the structure is roughly:
```kotlin
Box(modifier = Modifier.fillMaxSize().background(...)) {
    // Dropdown menu at top
    // Visualizer
    // Trigger overlay
    // Main content Column
}
```

Add after the Box opening and background setup, before all other content:

```kotlin
// Ambiance particle effects (behind all UI)
val effectsEnabled by viewModel.ambianceEffectsEnabled.collectAsStateWithLifecycle()
if (effectsEnabled) {
    AmbianceOverlay(
        isPlaying = state.playerState.isPlaying,
        accentColor = LocalAccentColor.current,
        modifier = Modifier.fillMaxSize()
    )
}
```

Add import: `import com.asmrhelper.ui.components.AmbianceOverlay`

- [ ] **Step 3: Add persistence for effects toggle**

In `PlayViewModel`, add:

```kotlin
private val _ambianceEffectsEnabled = MutableStateFlow(true)
val ambianceEffectsEnabled: StateFlow<Boolean> = _ambianceEffectsEnabled

init {
    // ... existing init code ...
    viewModelScope.launch {
        _ambianceEffectsEnabled.value = settingsRepository.getPlayEffectsEnabled()
    }
}

fun setAmbianceEffectsEnabled(enabled: Boolean) {
    _ambianceEffectsEnabled.value = enabled
    viewModelScope.launch { settingsRepository.setPlayEffectsEnabled(enabled) }
}
```

- [ ] **Step 4: Add settings repository methods**

In `SettingsRepository.kt` (interface), add:

```kotlin
fun getPlayEffectsEnabled(): Boolean
suspend fun setPlayEffectsEnabled(enabled: Boolean)
```

In `SettingsRepositoryImpl.kt`, implement:

```kotlin
override fun getPlayEffectsEnabled(): Boolean =
    prefs.getBoolean("play_effects", true)

override suspend fun setPlayEffectsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("play_effects", enabled).apply()
}
```

- [ ] **Step 5: Add toggle in SettingsScreen**

Find the "显示" section in SettingsScreen (around line 860-905). Add after the hypnosis mode toggle's closing card:

```kotlin
// 播放界面特效 toggle
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("播放界面特效", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "浮动粒子和呼吸光晕效果",
            color = TextHint,
            fontSize = 12.sp
        )
    }
    Switch(
        checked = playEffectsEnabled,
        onCheckedChange = { viewModel.setPlayEffectsEnabled(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = LocalAccentColor.current,
            checkedTrackColor = LocalAccentColor.current.copy(alpha = 0.4f),
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = DarkSurfaceVariant
        )
    )
}
```

Add state collection in SettingsScreen composable:
```kotlin
val playEffectsEnabled by settingsViewModel.playEffectsEnabled.collectAsStateWithLifecycle()
```

Add in SettingsViewModel:
```kotlin
private val _playEffectsEnabled = MutableStateFlow(true)
val playEffectsEnabled: StateFlow<Boolean> = _playEffectsEnabled

init {
    viewModelScope.launch {
        _playEffectsEnabled.value = settingsRepository.getPlayEffectsEnabled()
    }
}

fun setPlayEffectsEnabled(enabled: Boolean) {
    _playEffectsEnabled.value = enabled
    viewModelScope.launch { settingsRepository.setPlayEffectsEnabled(enabled) }
}
```

---

### Task 6: WaveformCaptureProcessor — Real-Time Waveform via AudioProcessor

**Files:**
- Create: `app/src/main/java/com/asmrhelper/player/WaveformCaptureProcessor.kt`
- Modify: `app/src/main/java/com/asmrhelper/di/PlayerModule.kt` — register processor
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — use real data
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` — expose waveform data

**Interfaces:**
- Consumes: ExoPlayer PCM frames (transparent pass-through)
- Produces: `WaveformCaptureProcessor.waveformAmplitudes: StateFlow<FloatArray>` (128 values, ~30 fps)

- [ ] **Step 1: Create WaveformCaptureProcessor.kt**

```kotlin
package com.asmrhelper.player

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorInputResult
import androidx.media3.common.audio.AudioFormat
import androidx.media3.common.audio.AudioProcessingContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Transparent AudioProcessor that captures amplitude data from
 * ExoPlayer's audio pipeline without modifying the audio.
 * Provides real-time waveform data for visualization.
 */
class WaveformCaptureProcessor : AudioProcessor {

    private val _waveformAmplitudes = MutableStateFlow(FloatArray(128))
    val waveformAmplitudes: StateFlow<FloatArray> = _waveformAmplitudes.asStateFlow()

    private var sampleRate = 44100
    private var channelCount = 2
    private var samplesSinceLastOutput = 0
    private var amplitudeAccumulator = 0f
    private var amplitudeCount = 0
    private val tempAmps = FloatArray(128)
    private var tempAmpIndex = 0

    override fun configure(inputFormat: AudioFormat): AudioFormat {
        sampleRate = inputFormat.sampleRate
        channelCount = inputFormat.channelCount
        return inputFormat // Pass through unchanged
    }

    override fun onQueueInputBuffer(): AudioProcessorInputResult {
        return AudioProcessorInputResult.ADVANCE
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Read PCM samples and compute amplitude
        try {
            val order = inputBuffer.order()
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val sampleCount = inputBuffer.remaining() / 2 // 16-bit PCM

            var sum = 0f
            var count = 0
            val step = (sampleCount / 256).coerceAtLeast(1)

            for (i in 0 until sampleCount step step) {
                if (inputBuffer.remaining() >= 2) {
                    val sample = inputBuffer.short.toFloat() / 32768f
                    sum += abs(sample)
                    count++
                }
            }

            inputBuffer.order(order) // restore
            inputBuffer.rewind()

            if (count > 0) {
                val amp = (sum / count).coerceIn(0f, 1f)
                tempAmps[tempAmpIndex] = amp
                tempAmpIndex = (tempAmpIndex + 1) % 128

                if (tempAmpIndex == 0) {
                    _waveformAmplitudes.value = tempAmps.copyOf()
                }
            }
        } catch (_: Exception) { }
    }

    override fun queueInput(inputBuffer: ByteBuffer, presentationTimeUs: Long) {
        queueInput(inputBuffer)
    }

    override fun getOutput(): ByteBuffer? = AudioProcessor.NO_OUTPUT
    override fun onAdvancePosition(positionUs: Long) {}
    override fun onRelease() {}
    override fun onReset() {}
    override fun onFlush() {}
    override fun isActive(): Boolean = true
    override fun isEnded(): Boolean = false
    override fun getOutputAudioFormat(): AudioFormat? = null
    override fun getOutputBufferSize(): Int = 0
    override fun hasPendingOutput(): Boolean = false
    override fun needsMoreInput(): Boolean = true
}
```

- [ ] **Step 2: Register WaveformCaptureProcessor in PlayerModule**

Read `app/src/main/java/com/asmrhelper/di/PlayerModule.kt`. Modify the `provideMainPlayer` function to create and register the processor. Since the processor needs to be the same instance that consumers read from, we need a slightly different approach — create the processor as a separate @Provides @Singleton:

```kotlin
@Provides
@Singleton
fun provideWaveformCaptureProcessor(): WaveformCaptureProcessor =
    WaveformCaptureProcessor()

@Provides
@Singleton
@MainPlayer
fun provideMainPlayer(
    @ApplicationContext context: Context,
    waveformProcessor: WaveformCaptureProcessor
): ExoPlayer =
    ExoPlayer.Builder(context)
        .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ true)
        .setAudioProcessors(arrayOf(waveformProcessor))
        .build()
```

Add import: `import com.asmrhelper.player.WaveformCaptureProcessor`

- [ ] **Step 3: Expose waveform data in PlayViewModel**

Add dependency to constructor:
```kotlin
private val waveformCaptureProcessor: WaveformCaptureProcessor,
```

Add public property:
```kotlin
/** Real-time waveform amplitudes from AudioProcessor (replaces simulated sine waves). */
val waveformAmplitudes: StateFlow<FloatArray> = waveformCaptureProcessor.waveformAmplitudes
```

- [ ] **Step 4: Update PlayScreen to use real waveform data**

Read lines 215-233 (visualizer section). Change from `viewModel.waveformBytes` to `viewModel.waveformAmplitudes`. Also update `SoundCloudWaveform` to accept `FloatArray`:

Modify `SoundCloudWaveform` signature to accept both byte array (legacy) and float array (real-time):

```kotlin
@Composable
private fun SoundCloudWaveform(
    waveformBytes: ByteArray?,
    waveformFloats: FloatArray?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
)
```

In the LaunchedEffect that converts bytes, add float handling:
```kotlin
LaunchedEffect(waveformFloats) {
    if (waveformFloats != null && waveformFloats.isNotEmpty()) {
        targetData.value = waveformFloats
        hasRealData.value = true
    }
}
```

In the caller (line 226-232), pass the new param:
```kotlin
val waveformAmps by viewModel.waveformAmplitudes.collectAsStateWithLifecycle()
// ... inside Box ...
SoundCloudWaveform(
    waveformBytes = waveformBytes,
    waveformFloats = waveformAmps,
    isPlaying = state.playerState.isPlaying,
    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
)
```

---

### Task 7: Build and Verify

**Files:** None (build step only)

- [ ] **Step 1: Clean rebuild APK**

```bash
.\gradlew.bat assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Verify APK output exists**

```bash
dir app\build\outputs\apk\debug\app-debug.apk
```
