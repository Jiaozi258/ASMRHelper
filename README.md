# ASMRHelper - ASMR助手

An Android ASMR audio player built with Kotlin and Jetpack Compose, featuring noise generation, binaural beats, spatial audio, and interactive trigger effects for relaxation, focus, and sleep aid.本项目是本人vibe-coding的学习项目。

## Features

### Audio Playback
- Media3 ExoPlayer-based playback with dual-player architecture (main audio + background ambient)
- Full playlist support with configurable loop modes (single, list, shuffle)
- Import audio files from device storage with automatic scanning
- Favorites, bookmarks, and sleep journal tracking

### Sound Generation
- **Noise Generator** — White, pink, and brown noise with adjustable volume
- **Binaural Beats** — 10 presets covering Delta, Theta, Alpha, Beta, Gamma, and SMR frequency bands
- **Spatial Audio** — 3D positional audio control

### Interactive Tools
- **Trigger Pad** — Tap-based sound triggers with independent and parallel play modes
- **Volume Trigger** — Beat-synced particle effects (spray, flash, fountain) with customizable colors and emoji
- **Audio Visualizer** — Real-time FFT and waveform visualization
- **Haptic Feedback** — Vibration synchronization with audio playback

### Scenes & Automation
- **Scene Presets** — Save and restore complete playback configurations (audio, noise, binaural, background)
- **Pomodoro Timer** — Focus sessions with timed intervals
- **Sleep Timer** — Auto-stop playback after a configurable duration
- **Hypnosis Mode** — Animated gradient backgrounds for relaxation

### Customization
- **Background Gallery** — Import images and bind them to specific audio tracks or set globally
- **Theme Presets** — Multiple color themes
- **Privacy Mode** — Obfuscate audio metadata in screenshots and recents

## Tech Stack

| Category        | Technology                          |
|-----------------|-------------------------------------|
| Language        | Kotlin 2.x                          |
| UI              | Jetpack Compose + Material 3        |
| DI              | Hilt (Dagger)                       |
| Database        | Room + KSP                          |
| Media           | Media3 ExoPlayer + AudioTrack       |
| Async           | Kotlin Coroutines + Flow            |
| Navigation      | Jetpack Navigation Compose          |
| Image Loading   | Coil                                |
| Audio FX        | Android Visualizer API              |
| Haptics         | Android Vibrator/VibratorManager    |

## Architecture

Clean Architecture with three layers:

- **domain/** — Models (`Audio`, `Playlist`, `PlayerState`), repository interfaces
- **data/** — Repository implementations, Room DAOs/entities, audio scanner, SharedPreferences
- **ui/** — Compose screens and ViewModels (Play, Library, Playlist, TriggerPad, Settings, Background, SleepJournal, Profile)
- **player/** — Audio engines (`PlayerManager`, `NoiseGenerator`, `BinauralBeatEngine`, `SpatialAudioController`, `AudioVisualizerController`, `HapticFeedbackController`)
- **di/** — Hilt modules for database, player, and repositories

## Requirements

- Android 8.0 (API 26) or higher
- Android Studio Hedgehog or newer
- JDK 17

## Setup

```bash
git clone https://github.com/JiaoZi258/ASMRHelper.git
```

Open the project in Android Studio, sync Gradle, and run on a device or emulator.

## License

This project is for personal and educational use.
