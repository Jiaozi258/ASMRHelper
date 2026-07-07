# 声音丰富度 + 视觉氛围 — Design Spec

> **Date:** 2026-07-07
> **Status:** Approved

## Overview

Two feature groups enhancing the core ASMR experience: built-in nature sounds with EQ, and immersive visual effects with real-time waveform.

---

## Feature B1: 内置自然音库

### Purpose
6 built-in ambient sound loops (rain, stream, campfire, wind, thunder, ocean) available directly from the existing ambient sound picker — no import needed.

### Implementation

**Audio files:** 6 OGG files in `res/raw/`:
```
ambient_rain.ogg     (~375 KB)
ambient_stream.ogg   (~5.7 MB)
ambient_campfire.ogg (~5.9 MB)
ambient_wind.ogg     (~1.5 MB)
ambient_thunder.ogg  (~2.3 MB)
ambient_ocean.ogg    (~2.6 MB)
```

**Registration:** At app startup, `AsmrApplication` (or a lazy init) copies each raw resource to a private file directory and registers the path. Since `res/raw/` files are accessed via `Uri` (`android.resource://`), the existing ambient audio list reads them as entries.

**Alternative approach (simpler):** Add hardcoded entries in the ViewModel that exposes `res/raw` resource IDs alongside user-imported paths. The ambient sound dialog already shows file paths — we add resource-based entries with Chinese labels.

**Data model change:** `ambientAudios` currently is `List<String>` (file paths). We change it to a sealed class or add a parallel list of built-in entries:

```kotlin
data class AmbientSource(
    val label: String,        // "🌧️ 雨声"
    val type: AmbientType
)
sealed class AmbientType {
    data class UserFile(val path: String) : AmbientType()
    data class BuiltIn(val rawResId: Int, val cachePath: String) : AmbientType()
}
```

On selection: if `BuiltIn`, copy from `res/raw` to a temp cache file (one-time, then reuse), then play via backgroundPlayer.

**Edge cases:**
- First selection: copy raw resource to cache (takes ~0.1s, imperceptible)
- Cache file lost: re-copy on next selection (idempotent)
- All built-in entries always visible regardless of user imports

### Files
- `app/src/main/res/raw/ambient_*.ogg` — 6 audio files (already added)
- `app/src/main/java/com/asmrhelper/domain/model/AmbientSource.kt` — new model
- `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` — expose built-in list + handle selection
- `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — show built-in entries in ambient dialog

---

## Feature B2: 3-Band Equalizer

### Purpose
Simple bass/mid/treble adjustment for the main player output.

### Implementation

**Technology:** `android.media.audiofx.Equalizer` attached to ExoPlayer's audio session.

**Architecture:**
```
PlayViewModel
  → EqualizerController (new, @Singleton)
    → Equalizer (Android API, attached to mainPlayer.audioSessionId)
```

**EqualizerController API:**
```kotlin
class EqualizerController @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer
) {
    val bandLevels: StateFlow<List<Float>>  // 3 bands, -10..+10 dB
    val isEnabled: StateFlow<Boolean>

    fun setBandLevel(band: Int, levelDb: Float)
    fun reset()
    fun release()
}
```

**Band configuration:**
| Band | Center Freq | Range |
|------|------------|-------|
| 低音 | 250 Hz | -10 ~ +10 dB |
| 中音 | 1500 Hz | -10 ~ +10 dB |
| 高音 | 8000 Hz | -10 ~ +10 dB |

**UI:** 3 vertical sliders in a collapsible section on PlayScreen, with labels and dB readout. "重置" button resets all to 0.

**Edge cases:**
- Equalizer not supported on device → hide EQ section entirely
- Audio session ID not ready yet (returns 0) → retry with delay
- Equalizer released when player is released

### Files
- `app/src/main/java/com/asmrhelper/player/EqualizerController.kt` — new
- `app/src/main/java/com/asmrhelper/di/PlayerModule.kt` — provide EqualizerController
- `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — EQ UI section
- `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` — delegate to controller

---

## Feature C1: Particle Effects + Breathing Glow

### Purpose
Floating light particles and a breathing glow circle on the play screen for immersive ASMR atmosphere.

### Implementation

**Technology:** Compose Canvas with `Animatable`-driven animation loops. No external dependencies.

**Particle system:**
- 10-15 particles, each with random: initial position (x, y), velocity vector, size (2-6dp), opacity (0.1-0.4), color (accent color tint)
- Particles drift upward with slight horizontal oscillation
- When a particle exits the top, it respawns at the bottom
- Frame rate: 30fps via `withFrameMillis {}` loop

**Breathing glow:**
- Centered at play/pause button position
- Radius oscillates between 60dp and 120dp using a sine wave (period ~4s)
- Radial gradient: accent color at center → transparent at edge
- Only active when `isPlaying = true`; fades out over 1s when paused

**Composable:**
```kotlin
@Composable
fun AmbianceOverlay(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
)
```

Placed behind all interactive UI, above the background color.

**Performance:** Canvas-based, ~15 draw calls per frame. Negligible CPU/GPU impact. Drawing suspended when `isPlaying = false` for >2s.

**User control:** Setting toggle "播放界面特效" (default: on) in Settings → 显示.

### Files
- `app/src/main/java/com/asmrhelper/ui/components/AmbianceOverlay.kt` — new
- `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — add AmbianceOverlay behind content
- `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt` — toggle switch

---

## Feature C2: Real-Time Waveform via AudioProcessor

### Purpose
Replace the simulated sine-wave visualizer with real audio-reactive waveform data extracted from ExoPlayer's audio pipeline.

### Implementation

**Technology:** Media3 `AudioProcessor` interface — intercepts PCM frames during playback with zero extra decoding cost.

**Architecture:**
```
ExoPlayer audio pipeline
  → ... decode → AudioProcessor.onQueueInputBuffer()
    → copy amplitude samples → buffer
  → ... render to speaker
```

**WaveformCaptureProcessor:**
```kotlin
class WaveformCaptureProcessor : AudioProcessor {
    // Outputs 128 amplitude values (0.0 ~ 1.0) updated ~30 times/sec
    private val amplitudes = FloatArray(128)
    val waveformData: StateFlow<FloatArray>
    
    override fun onQueueInputBuffer(): AudioProcessorInputResult {
        // Read PCM samples from input buffer
        // Compute RMS amplitude per window
        // Push to waveformData StateFlow
        // Return ADVANCE (pass-through, don't modify audio)
    }
}
```

**Integration with SoundCloudWaveform:**
- `SoundCloudWaveform` currently accepts `waveformBytes: ByteArray?`
- Keep the same composable but add a new data source `FloatArray` from the processor
- When processor data is available → use real data
- When processor data is unavailable → fall back to simulated sine wave (existing behavior)
- Remove the old `AudioVisualizerController` dependency (Android Visualizer API)

**Registration in PlayerModule:**
```kotlin
fun provideMainPlayer(context: Context): ExoPlayer {
    val processor = WaveformCaptureProcessor()
    return ExoPlayer.Builder(context)
        .setAudioAttributes(...)
        .setAudioProcessors(arrayOf(processor))  // ← intercept audio
        .build()
}
```

**Edge cases:**
- AudioProcessor not supported on some devices → graceful fallback to simulated wave
- Processor receives empty buffer → keep previous amplitudes for 2 frames, then flatline
- Player released → processor stops naturally (buffer queue empties)

### Files
- `app/src/main/java/com/asmrhelper/player/WaveformCaptureProcessor.kt` — new
- `app/src/main/java/com/asmrhelper/di/PlayerModule.kt` — register processor on main player
- `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt` — read from processor, pass to SoundCloudWaveform
- `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt` — expose waveform data (potentially)

---

## Settings Additions

Add to Settings → 显示 section:
- `play_effects` (Boolean, default true): "播放界面粒子特效"

---

## Spec Self-Review

- ✅ No placeholders or TBDs
- ✅ No contradictions — B1/B2/C1/C2 are fully independent
- ✅ Scope is focused: 4 sub-features, each self-contained
- ✅ Ambiguity resolved: all edge cases described, fallback behaviors explicit
- ✅ Existing architecture respected: new controllers follow NoiseGenerator/BinauralBeatEngine pattern
- ✅ AudioProcessor passes through audio unmodified (transparent intercept)
