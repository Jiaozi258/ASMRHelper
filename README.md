<div align="center">

# ASMRHelper · ASMR 助手

*沉浸式音频体验 · Immersive Audio Experience*

<p>
  <a href="#english">🇺🇸 English</a> &nbsp;|&nbsp;
  <a href="#chinese">🇨🇳 中文</a>
</p>

</div>

---

<h2 id="english">🇺🇸 English</h2>

A feature-rich Android ASMR audio player for relaxation, meditation, focus, and sleep. Built with Kotlin + Jetpack Compose, powered by Media3 ExoPlayer with dual-player mixing, real-time audio effects, and interactive visual ambiance.

> **Note:** This is a personal *vibe-coding* learning project.

## 🎯 Feature Overview

### 🎵 Core Playback
| Feature | Detail |
|---------|--------|
| **Dual-Player Engine** | Independent main audio + background ambient, mixed simultaneously |
| **Crossfade** | Smooth transitions between tracks with configurable duration |
| **Fade In/Out** | Gradual volume ramping for sleep-friendly playback |
| **Loop Modes** | None / Single / List — per-session configurable |
| **Background Survival** | Foreground Service + WakeLock + MediaSession — plays continuously with screen off |
| **Battery Optimization Guide** | Automatic detection on Chinese ROMs (MIUI/ColorOS/OriginOS), guided whitelist setup |
| **Media Controls** | Notification (MediaStyle), lock screen, Bluetooth, wired headset |

### 🌿 Built-in Nature Sounds (6 FREE OGG loops)
| 🌧️ Rain | 🌊 Stream | 🔥 Campfire |
|----------|-----------|-------------|
| 💨 Wind | ⚡ Thunder | 🌊 Ocean |

→ Mix any nature sound as background ambiance alongside your main audio.

### 🎛️ Real-Time Audio Effects
| Effect | Description |
|--------|-------------|
| **3-Band Equalizer** | Bass / Mid / Treble ±10dB, auto-detects device EQ bands |
| **Binaural Beats** | 10+ presets: Delta (sleep) → Theta (meditation) → Alpha (calm) → Beta (focus) → Gamma |
| **Spatial Audio** | 3D positional modes — left-right sweep, circular rotation, spatial widening |
| **Noise Generator** | White / Pink / Brown noise with independent volume control |
| **Haptic Feedback** | Vibration sync with beat detection — intensity adjustable |

### ✨ Visual Ambiance
| Effect | Description |
|--------|-------------|
| **Particle System** | Volume-triggered spray/flash/fountain effects, color + emoji customizable |
| **Breathing Glow** | Animated radial glow overlay for relaxation |
| **Hypnosis Mode** | Gradient background animations with auto-hide UI |
| **Audio Visualizer** | Real-time FFT waveform rendered on playback screen |
| **Image Slideshow** | Full-screen image carousel for visual relaxation |
| **Background Gallery** | Import images, bind to specific audio tracks or set globally |
| **4 Theme Presets** | Deep Space / Deep Blue / Dark Violet / Ink Green |

### ⏱️ Productivity Tools
| Tool | Description |
|------|-------------|
| **Sleep Timer** | Auto-stop after N minutes with countdown display |
| **Pomodoro Timer** | Focus/break cycles (configurable 1–120 min focus, 1–30 min break) |
| **Sleep Journal** | Log and review sleep sessions with timestamps |

### 📂 Content Management
| Feature | Description |
|---------|-------------|
| **Audio Library** | Auto-scan device storage, import individual files |
| **Playlists** | Create / edit / play custom playlists |
| **Favorites** | Star tracks for quick access |
| **Bookmarks** | Save playback positions with names |
| **Video Audio Extraction** | Extract audio tracks from video files |
| **Play History** | View recent plays with timestamps |

### 🔧 Convenience
| Feature | Description |
|---------|-------------|
| **Desktop Widget** | Play/pause + current track info on home screen |
| **App Shortcuts** | Long-press icon → Resume / 30-min Timer / Favorites / History |
| **Share Integration** | Share URLs from other apps → auto-extract audio |
| **Privacy Mode** | Mask audio titles in screenshots and app switcher |
| **Trigger Pad** | Custom sound trigger buttons with independent/parallel play modes |
| **Scene Presets** | Save entire playback state (audio, EQ, noise, binaural, background) and restore in one tap |
| **Lock Screen Display** | Optional track info on lock screen |

## 🏗️ Architecture

```
app/src/main/java/com/asmrhelper/
├── data/                    # Data layer
│   ├── local/db/           # Room database, DAOs, entities (11 tables)
│   ├── local/scanner/      # Filesystem audio scanner
│   ├── mapper/             # Entity ↔ Domain model mapping
│   └── repository/         # Repository implementations (10 repositories)
├── di/                     # Hilt DI modules (Player, Database, Repository)
├── domain/
│   ├── model/              # Domain models (Audio, Playlist, PlayerState, ...)
│   └── repository/         # Repository interfaces
├── player/                 # Audio engine layer
│   ├── PlayerManager.kt    # Core playback orchestrator (dual ExoPlayer)
│   ├── AsmrMediaService.kt # Foreground service + WakeLock + MediaSession
│   ├── EqualizerController.kt    # 3-band EQ via Android Equalizer API
│   ├── BinauralBeatEngine.kt     # Sine-wave binaural synthesis via AudioTrack
│   ├── NoiseGenerator.kt         # White/Pink/Brown noise via AudioTrack
│   ├── SpatialAudioController.kt # 3D positional audio via Virtualizer API
│   ├── AudioVisualizerController.kt # FFT waveform capture
│   ├── HapticFeedbackController.kt  # Beat-synced vibration
│   └── VideoAudioExtractor.kt    # MediaMetadataRetriever audio extraction
├── ui/                     # Presentation layer (Compose)
│   ├── play/               # Main playback screen + ViewModel
│   ├── library/            # Audio library, file browser, video extraction
│   ├── playlist/           # Playlist management
│   ├── settings/           # Settings screen + ViewModel
│   ├── background/         # Background image gallery
│   ├── triggerpad/         # Custom trigger pad
│   ├── sleep/              # Sleep journal
│   ├── slideshow/          # Image slideshow
│   ├── profile/            # Profile screen
│   ├── history/            # Play history
│   ├── navigation/         # NavHost, bottom bar, screen routes
│   ├── components/         # Shared composables (MiniPlayer, AmbianceOverlay, etc.)
│   └── theme/              # Material 3 theme, color presets, typography
├── util/                   # Utilities (Battery helper, privacy mask, constants)
├── widget/                 # Desktop AppWidget
├── MainActivity.kt         # Single activity, edge-to-edge
├── AsmrApplication.kt      # Hilt app, notification channel init
└── CrashHandler.kt         # Global crash capture
```

## 🧰 Tech Stack

| Category | Library |
|----------|---------|
| **Language** | Kotlin 2.x |
| **UI** | Jetpack Compose + Material 3 |
| **Navigation** | Jetpack Navigation Compose |
| **DI** | Hilt + KSP |
| **Database** | Room + KSP (11 entities, 10 DAOs) |
| **Media Playback** | Media3 ExoPlayer 1.5.1 (dual instance) |
| **Audio Synthesis** | Android AudioTrack API |
| **Audio Effects** | Android Equalizer, Visualizer, Virtualizer APIs |
| **Async** | Kotlin Coroutines + StateFlow |
| **Image Loading** | Coil |
| **Foreground Service** | android.app.Service + MediaSessionCompat |
| **Widget** | AppWidgetProvider + RemoteViews |
| **Haptics** | Vibrator / VibratorManager |

## 📦 Requirements & Setup

```bash
# Clone
git clone https://github.com/JiaoZi258/ASMRHelper.git

# Open in Android Studio Hedgehog+ → Sync Gradle → Run
```

| Requirement | Version |
|-------------|---------|
| Android OS | 8.0+ (API 26) |
| Android Studio | Hedgehog or newer |
| JDK | 17 |
| Gradle | 8.x |
| Kotlin | 2.x |

## 📄 License

This project is for personal and educational use. Not open for commercial redistribution.

---

<h2 id="chinese">🇨🇳 中文</h2>

一款功能丰富的 Android ASMR 音频播放器，用于放松、冥想、专注和助眠。基于 Kotlin + Jetpack Compose 构建，采用 Media3 ExoPlayer 双播放器架构，支持实时音频效果和交互式视觉氛围。

> **注：** 本项目为个人 vibe-coding 学习项目。

## 🎯 功能总览

### 🎵 核心播放
| 功能 | 说明 |
|------|------|
| **双播放器引擎** | 主音频 + 背景环境音独立播放，可同时混音输出 |
| **交叉淡入淡出** | 切歌时平滑过渡，时长可配置 |
| **淡入/淡出** | 渐进式音量调整，适合睡前使用 |
| **循环模式** | 无循环 / 单曲循环 / 列表循环 |
| **后台保活** | 前台服务 + WakeLock + MediaSession，息屏持续播放 |
| **电池优化引导** | 自动检测国产 ROM（MIUI/ColorOS/OriginOS），引导加入白名单 |
| **媒体控制** | 通知栏（MediaStyle）、锁屏、蓝牙耳机、线控 |

### 🌿 内置自然音效（6 个免费 OGG 循环音频）
| 🌧️ 雨声 | 🌊 溪流 | 🔥 篝火 |
|----------|-----------|-------------|
| 💨 风声 | ⚡ 雷声 | 🌊 海浪 |

→ 可作为环境音与主音频同时播放，营造沉浸氛围。

### 🎛️ 实时音频特效
| 特效 | 说明 |
|------|------|
| **3 段均衡器** | 低音 / 中音 / 高音 ±10dB，自动适配设备频段 |
| **双耳节拍** | 10+ 预设：Delta（助眠）→ Theta（冥想）→ Alpha（放松）→ Beta（专注）→ Gamma |
| **3D 空间音效** | 左右扫掠 / 环绕旋转 / 空间扩展三种模式 |
| **噪音生成器** | 白噪音 / 粉红噪音 / 棕色噪音，独立音量调节 |
| **触觉反馈** | 跟随节拍振动同步，强度可调 |

### ✨ 视觉氛围
| 效果 | 说明 |
|------|------|
| **粒子系统** | 音量触发喷洒/闪烁/喷泉动画，颜色和 Emoji 可自定义 |
| **呼吸光晕** | 径向渐变呼吸动画，营造放松感 |
| **催眠模式** | 渐变动画背景 + 自动隐藏 UI |
| **音频可视化** | 实时 FFT 波形渲染 |
| **图片幻灯片** | 全屏图片轮播，视觉放松体验 |
| **背景图库** | 导入图片，可绑定到特定音频或全局使用 |
| **4 套主题预设** | 深空黑 / 深邃蓝 / 暗紫 / 墨绿 |

### ⏱️ 效率工具
| 工具 | 说明 |
|------|------|
| **睡眠定时器** | 设定 N 分钟后自动停止播放，带倒计时 |
| **番茄钟** | 专注/休息循环（可配置 1–120 分钟专注，1–30 分钟休息） |
| **睡眠日志** | 记录和回顾睡眠时段 |

### 📂 内容管理
| 功能 | 说明 |
|------|------|
| **音频库** | 自动扫描设备存储，手动导入文件 |
| **播放列表** | 创建 / 编辑 / 播放自定义歌单 |
| **收藏夹** | 星标收藏，快速访问 |
| **书签** | 保存带名称的播放位置 |
| **视频音频提取** | 从视频文件中提取音频轨道 |
| **播放历史** | 带时间戳的播放记录 |

### 🔧 便捷功能
| 功能 | 说明 |
|------|------|
| **桌面小组件** | 主屏幕显示当前播放，一键 播放/暂停 |
| **应用快捷方式** | 长按图标 → 继续播放 / 30分钟定时 / 收藏 / 历史 |
| **分享集成** | 从其他 App 分享链接 → 自动提取音频 |
| **隐私模式** | 截图和应用切换器中隐藏音频标题 |
| **触发板** | 自定义音效按键，支持独立/并行播放模式 |
| **场景预设** | 一键保存和恢复完整播放状态（音频、均衡器、噪音、双耳节拍、背景） |
| **锁屏展示** | 可选在锁屏上显示当前播放信息 |

## 🏗️ 项目架构

```
app/src/main/java/com/asmrhelper/
├── data/                    # 数据层
│   ├── local/db/           # Room 数据库、DAO、实体（11 张表）
│   ├── local/scanner/      # 文件系统音频扫描
│   ├── mapper/             # 实体 ↔ 领域模型映射
│   └── repository/         # 仓库实现（10 个仓库）
├── di/                     # Hilt 依赖注入模块
├── domain/
│   ├── model/              # 领域模型（Audio, Playlist, PlayerState 等）
│   └── repository/         # 仓库接口
├── player/                 # 音频引擎层
│   ├── PlayerManager.kt    # 核心播放编排（双 ExoPlayer）
│   ├── AsmrMediaService.kt # 前台服务 + WakeLock + MediaSession
│   ├── EqualizerController.kt    # 3 段均衡器
│   ├── BinauralBeatEngine.kt     # 双耳节拍合成（AudioTrack）
│   ├── NoiseGenerator.kt         # 白/粉/棕色噪音（AudioTrack）
│   ├── SpatialAudioController.kt # 3D 空间音效（Virtualizer API）
│   ├── AudioVisualizerController.kt # FFT 波形采集
│   ├── HapticFeedbackController.kt  # 节拍同步振动
│   └── VideoAudioExtractor.kt    # 视频音频提取
├── ui/                     # 表现层（Compose）
│   ├── play/               # 主播放界面 + ViewModel
│   ├── library/            # 音频库、文件浏览、视频提取
│   ├── playlist/           # 播放列表管理
│   ├── settings/           # 设置界面
│   ├── background/         # 背景图库
│   ├── triggerpad/         # 触发板
│   ├── sleep/              # 睡眠日志
│   ├── slideshow/          # 图片幻灯片
│   ├── profile/            # 个人主页
│   ├── history/            # 播放历史
│   ├── navigation/         # 导航、底部栏
│   ├── components/         # 共享组件（MiniPlayer, AmbianceOverlay 等）
│   └── theme/              # Material 3 主题、色彩预设
├── util/                   # 工具类（电池优化、隐私遮罩等）
├── widget/                 # 桌面小组件
├── MainActivity.kt         # 单 Activity，边到边显示
├── AsmrApplication.kt      # Hilt 应用 + 通知渠道初始化
└── CrashHandler.kt         # 全局崩溃捕获
```

## 🧰 技术栈

| 类别 | 技术 |
|------|------|
| **语言** | Kotlin 2.x |
| **UI** | Jetpack Compose + Material 3 |
| **导航** | Jetpack Navigation Compose |
| **依赖注入** | Hilt + KSP |
| **数据库** | Room + KSP（11 实体，10 DAO） |
| **媒体播放** | Media3 ExoPlayer 1.5.1（双实例） |
| **音频合成** | Android AudioTrack API |
| **音频特效** | Android Equalizer / Visualizer / Virtualizer API |
| **异步** | Kotlin Coroutines + StateFlow |
| **图片加载** | Coil |
| **前台服务** | android.app.Service + MediaSessionCompat |
| **小组件** | AppWidgetProvider + RemoteViews |
| **触觉** | Vibrator / VibratorManager |

## 📦 环境与构建

```bash
# 克隆项目
git clone https://github.com/JiaoZi258/ASMRHelper.git

# 用 Android Studio Hedgehog+ 打开 → 同步 Gradle → 运行
```

| 要求 | 版本 |
|------|------|
| 系统 | Android 8.0+ (API 26) |
| IDE | Android Studio Hedgehog 或更新 |
| JDK | 17 |
| Gradle | 8.x |
| Kotlin | 2.x |

## 📄 许可证

本项目仅供个人学习和教育用途。不得用于商业再分发。

---

<div align="center">
  <br>
  <p>Made with ❤️ and ☕ · Powered by Kotlin & Compose</p>
</div>
