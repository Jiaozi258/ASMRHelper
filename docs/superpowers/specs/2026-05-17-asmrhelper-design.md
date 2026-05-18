# ASMRHelper 架构设计文档

**日期**: 2026-05-17
**状态**: 已确认

---

## 技术栈

| 层级 | 技术选型 |
|------|---------|
| UI | Jetpack Compose + Navigation Compose + Coil |
| 动画 | AnimatedContent, animateFloatAsState, spring, Crossfade |
| 架构 | Clean Architecture (UI → Domain → Data) |
| DI | Hilt |
| 数据库 | Room + KSP |
| 播放器 | Media3 ExoPlayer (双实例) |
| 异步 | Kotlin Coroutines + Flow |
| 构建 | Gradle KTS + Version Catalog (libs.versions.toml) |

**Android**: minSdk 26, targetSdk 35, compileSdk 35

---

## 包结构

单 Gradle module，靠 package 划分层级：

```
com.asmrhelper/
├── AsmrApplication.kt
├── MainActivity.kt
├── ui/
│   ├── navigation/ (AsmrNavHost, BottomNavBar)
│   ├── play/       (PlayScreen, PlayViewModel)
│   ├── profile/    (ProfileScreen, ProfileViewModel)
│   ├── settings/   (SettingsScreen, SettingsViewModel)
│   └── components/ (DropdownMenu, PlayButton, etc.)
├── domain/
│   ├── model/      (Audio, Playlist, BackgroundImage)
│   ├── usecase/
│   └── repository/ (接口定义)
├── data/
│   ├── local/db/   (Room DB, DAO, Entities)
│   ├── local/scanner/
│   ├── repository/ (接口实现)
│   └── mapper/
├── player/         (AsmrMediaService, PlayerManager, PlayerState)
├── di/             (Hilt Modules)
└── util/
```

---

## 导航设计

- **底部三 Tab**: Play (播放), Profile (主页), Settings (设置)
- 每个 Tab 通过 `AnimatedContent` 实现 fade+slide 过渡
- 右上角下拉菜单用 `DropdownMenu` + 弹性动画
- 播放按钮缩放反馈：`animateFloatAsState` + spring(0.85 ↔ 1.0)

---

## 数据流

```
User Action → ViewModel → UseCase → Repository → DataSource
                 ↑                                       ↓
            UiState (StateFlow) ← ← ← ← ← ← ← ← ← Flow<DomainModel>
```

UI 层通过 `collectAsState()` 收集 StateFlow，触发 Compose 重组。

---

## Room 实体

- **AudioEntity**: id, title, artist, path, duration, addedAt
- **PlaylistEntity**: id, name, createdAt
- **PlaylistAudioCrossRef**: playlistId, audioId
- **BackgroundImageEntity**: id, name, path, addedAt
- **AudioBgBinding**: audioId (or folderId), imageId

---

## 双轨播放系统

- `AsmrMediaService`: Foreground Service，保活播放
- `PlayerManager`: 持有 mainPlayer + backgroundPlayer 两个 ExoPlayer 实例
- 主音轨：完整播放控制（播放/暂停/上一首/下一首/循环）
- 背景音轨：仅播放/暂停，独立混音
- 音频焦点：通过 AudioManager 管理，尊重系统音频焦点变化

---

## 核心功能清单

1. **播放页**: 播放/暂停动效按钮、标题显示、上下首、右上角菜单
2. **双轨混音**: 主音轨 + 背景环境音独立播放
3. **定时器**: 倒计时关闭、播完当前停止
4. **循环模式**: 单曲循环、列表循环、播完即止
5. **本地扫描**: 扫描媒体库 / 指定文件夹
6. **Room 增删改查**: 播放列表、收藏、背景图库
7. **隐私模式**: 标题中间字符替换为 *
8. **动态背景**: 绑定音频与背景图，播放时平滑过渡
9. **主页**: 本地/模拟账户登录注册 UI
10. **设置**: 版本信息、缓存清理、纯色背景

---

## 实施步骤

1. 项目初始化与 Gradle 配置
2. Room 数据库与实体类
3. 核心播放引擎 (Foreground Service + 双 Player)
4. UI 框架搭建 (导航 + 播放器主界面 + 动效)
5. 播放设置与定时模块
6. 文件扫描与管理
7. 个性化设置与背景图库
8. 主页账户模块
