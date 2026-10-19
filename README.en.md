<div align="center">

# NACRE 🐚

**A healing Android audio app — fall asleep and relax quietly, and listen to music nicely too**

<p>
  🌏 English &nbsp;|&nbsp; <a href="README.md">中文</a>
</p>

</div>

---

## Foreword · About this project 🙋

Hi there, I'm the author — a student building this in my spare time. NACRE is a personal project, and also the first time I've seriously tried to build an Android app of this size.

To be honest, this is a **vibe-coding** project: a good chunk of the code was written together with an AI assistant (Claude Code). I describe what I want, it writes, and then I read, tweak, and debug line by line — lots of pitfalls, lots of rewrites. So it's **not perfect**; there may be bugs, and some implementations are pretty hacky and not textbook-clean architecture.

But I quite like it. It carries a small dream of mine about software that is "good-sounding, good-looking, and calming": blending sleep white noise, binaural beats, ambient sounds, online music, lyrics, and a beautiful player into one app.

If you'd take a look, drop a ⭐, or open an issue, I'd be really glad 🙏.

> The name comes from **NACRE (mother-of-pearl)** — I hope it can feel as warm, quiet, and softly lustrous as nacre.

---

## 🎯 What it is

NACRE is an Android audio app built around **two modes**:

| Mode | What it does | Great for |
|------|--------------|-----------|
| 🌙 **ASMR mode** | Local audio + white noise + binaural beats + sleep tools | Relaxing, meditating, focusing, sleeping |
| 🎵 **MUSIC mode** | Online music (NetEase / QQ Music) + lyrics + charts & recommendations | Everyday listening |

Both modes share one audio engine (Media3 ExoPlayer dual-player mixing), so you can layer a background ambient sound on top of the main audio for an immersive feel.

---

## ✨ Features

### 🎵 Online music (MUSIC mode)

| Feature | Description |
|---------|-------------|
| **Dual sources** | NetEase Cloud Music + QQ Music, implemented natively (no web shell) |
| **Search** | Cross-platform song search |
| **Playlists / charts / recommendations** | Leaderboards, daily recommendations, personal FM, user playlists |
| **Login** | NetEase account/password or QR login; QQ Music web login |
| **Lyrics** | Word-by-word / line-by-line lyrics, scroll lyrics (flat / fisheye styles), offset calibration |
| **Multiple qualities** | Standard / higher / very high / lossless (per-source fallback) |
| **Favorites** | Cross-source, login-free favorites saved to a local "Liked" list |

> The NetEase `weapi` / `xeapi` encryption and QQ Music `zzc` signature were implemented from scratch by reading through public documentation — this is the part of the project I'm proudest of, and also where I made the most mistakes (X25519, AES, HMAC are full of traps).

### 🌙 ASMR mode (sleep · relaxation)

| Feature | Description |
|---------|-------------|
| **Local audio library** | Auto-scans device storage; manual import also supported |
| **Built-in nature sounds** | 🌧️ Rain · 🌊 Stream · 🔥 Campfire · 💨 Wind · ⚡ Thunder · 🌊 Ocean waves (6 free OGGs) |
| **Ambient mixing** | Ambient sound can play alongside main audio with independent volume |
| **Binaural beats** | Delta / Theta / Alpha / Beta / Gamma presets |
| **Noise generator** | White / pink / brown noise, independent volume |
| **3D spatial audio** | Left-right sweep / surround rotation / spatial expansion |
| **Hypnosis mode** | Animated gradient background + auto-hiding UI |
| **Sleep timer** | Scheduled auto-stop with countdown |
| **Pomodoro timer** | Focus / break cycles |
| **Sleep journal** | Record and review sleep sessions |

### 🎛️ Audio effects

| Effect | Description |
|--------|-------------|
| **3-band equalizer** | Bass / mid / treble ±10dB, auto-adapts to device frequency ranges |
| **Volume-triggered effects** | Spray / sparkle / fountain animations with customizable colors & emoji |
| **Audio visualizer** | Real-time FFT; bar / wave / dot / ring / symmetric / glow — 6 styles |
| **Haptic feedback** | Beat-synced vibration with adjustable intensity |

### ✨ Visuals & themes

| Feature | Description |
|---------|-------------|
| **Duotone cover background** | Real color sampling from album art + FFT soft light bars, flowing with the audio |
| **7 theme colors** | Apple Blue / Midnight Purple / Warm Orange / Forest Green / Ocean Blue / Rose Gold / Minimal Gray |
| **Theme mode** | Follow system / light / dark |
| **Custom font** | Import `.ttf` / `.otf`, applied globally |
| **Background gallery** | Import images, bind to a specific audio or use globally |
| **Image slideshow** | Full-screen image carousel for visual relaxation |

### 📂 Content management

| Feature | Description |
|---------|-------------|
| **Playlists** | Create / edit / play custom playlists |
| **Favorites** | Starred favorites for quick access |
| **Bookmarks** | Save named playback positions |
| **Play history** | Timestamped playback records |
| **Extract audio from video** | Extract an audio track from a video file |
| **Share import** | Share a link from another app; auto-parsed into playable content |

### 🔧 Convenience

| Feature | Description |
|---------|-------------|
| **Background playback** | Foreground service + WakeLock + MediaSession; plays with screen off |
| **Media controls** | Notification (MediaStyle) / lock screen / Bluetooth / headset buttons |
| **Battery optimization guide** | Detects Chinese ROMs (MIUI / ColorOS / OriginOS) and guides whitelisting |
| **Home-screen widget** | One-tap play/pause from the launcher |
| **App shortcuts** | Long-press icon: resume / 30-min timer / favorite / history |
| **Privacy mode** | Hides audio title in screenshots and the app switcher |
| **Trigger pad** | Custom sound buttons, independent / parallel playback |
| **Scene presets** | Save / restore a complete playback state in one tap |

---

## 🏗️ Tech stack

| Category | Tech |
|----------|------|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose (three-page horizontal swipe + sub-screens) |
| DI | Hilt + KSP |
| Database | Room + KSP |
| Playback | Media3 ExoPlayer + MediaSession (dual-instance mixing) |
| Network | OkHttp (music source layer) |
| Crypto | Self-implemented: NetEase weapi / xeapi (RSA, AES, X25519 ECDH, HMAC), QQ zzc signature |
| Images | Coil + Palette |
| Async | Kotlin Coroutines + StateFlow |
| Audio effects | Android Equalizer / Visualizer / Virtualizer + AudioTrack |
| Widget | AppWidgetProvider + RemoteViews |
| Haptics | Vibrator / VibratorManager |

---

## 🧱 Project structure

```
app/src/main/java/com/asmrhelper/
├── data/                        # Data layer
│   ├── local/db/               # Room database, DAOs, entities
│   ├── local/scanner/          # Local audio scanning
│   ├── mapper/                 # Entity ↔ domain model mapping
│   ├── music/                  # Online music sources
│   │   ├── netease/            #   NetEase (weapi/xeapi/eapi)
│   │   ├── neteaselib/         #   X25519 / Xeapi / Weapi / Eapi crypto
│   │   └── qq/                 #   QQ Music (zzc signature)
│   └── repository/             # Repository implementations
├── di/                         # Hilt dependency injection
├── domain/
│   ├── model/                  # Domain models
│   └── repository/             # Repository interfaces
├── player/                     # Audio engine layer
│   ├── PlayerManager.kt        #   Playback orchestration (dual ExoPlayer)
│   ├── AsmrMediaService.kt     #   Foreground service + WakeLock + MediaSession
│   ├── EqualizerController.kt  #   Equalizer
│   ├── BinauralBeatEngine.kt   #   Binaural beat synthesis
│   ├── NoiseGenerator.kt       #   White/pink/brown noise
│   ├── SpatialAudioController.kt # Spatial audio
│   ├── AudioVisualizerController.kt # FFT capture
│   ├── SceneEffectsController.kt   # Trigger effects
│   ├── AudioFocusManager.kt    #   Audio focus
│   ├── AudioDeviceMonitor.kt   #   Headphone/Bluetooth plug monitoring
│   └── VideoAudioExtractor.kt  #   Extract audio from video
├── ui/                         # Presentation layer (Compose)
│   ├── play/                   #   Player (ASMR + MUSIC)
│   ├── discover/               #   Discover (online music)
│   ├── profile/                #   Profile
│   ├── library/ playlist/ history/ background/ triggerpad/ sleep/ slideshow/
│   ├── settings/               #   Settings
│   ├── components/             #   Shared components
│   └── theme/                  #   Material 3 theme, color presets, fonts
├── util/                       # Utilities (battery optimization, privacy mask, fonts, i18n, etc.)
├── widget/                     # Home-screen widget
├── MainActivity.kt             # Single activity
├── AsmrApplication.kt          # Hilt application + notification channels
└── CrashHandler.kt             # Global crash handler
```

---

## 🚀 Build & run

| Requirement | Version |
|-------------|---------|
| OS | Android 8.0+ (API 26) |
| IDE | Android Studio (recent version) |
| JDK | 17 |
| Gradle | 8.x |
| Kotlin | 2.x |

```bash
# Clone the repo
git clone https://github.com/JiaoZi258/NACRE.git

# Open with Android Studio → sync Gradle → run
```

---

## ⚠️ Disclaimer

- This is a **personal learning project**; code quality is uneven, so please don't use it for production or commercial purposes.
- The built-in online music sources are implemented by **reverse-engineering public third-party APIs**, for study/research only. The APIs may break at any time or carry account risk — use at your own discretion.
- No cracking of paid/copyrighted content is provided; VIP songs and other rights-restricted content will simply report that they can't be played.

---

## 🙏 Acknowledgements

- Thanks to Jetpack Compose, Media3, Room, Hilt, Coil, and the whole Android open-source ecosystem.
- Thanks to the AI assistant for helping me build this project from zero to where it is today.

---

<div align="center">
  <br>
  <p>Made with ❤️, ☕, and a little bit of vibe ✨</p>
  <p>If it's useful to you, a ⭐ is very welcome</p>
</div>
