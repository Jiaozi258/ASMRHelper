# ASMRHelper 全局改版设计文档

- 日期：2026-10-17
- 状态：待审批（未获批前不写任何产品代码）
- 参考实现：参考 App A（React+Electron 歌词类桌面播放器）与参考 App B（Electron 音乐播放器）——均为逆向分析所得，仅作 UI/架构参考，**不在 App 内出现任何竞品名称**。

---

## 1. 意图与背景

ASMRHelper 目前是「单模式 ASMR 播放器」：底部 4 个标签（播放 / 图片 / 主页 / 设置），
播放页有两个版本（经典 `PlayScreen` 与沉浸式 `PlayScreenV2`）。用户要把它升级为一个
**双模式（ASMR + MUSIC）的现代化播放器**，MUSIC 模式的播放页采用参考 App A 的视觉语言：
封面双色调、流动/流体背景、音频可视化、居中歌词、悬浮控制、灵动岛式提示、透明 UI。

两个已确认的 bug 已在本次会话修复并推送（commit b777213），本设计不涉及回归。

## 2. 目标 / 非目标 / 反目标

### 目标
1. 去底部导航，改为**全屏页面 + 左右滑动**切换；图片页入口移到播放页菜单；消除顶部黑条。
2. 左上角**圆形按钮**在 ASMR / MUSIC 两种模式间切换。
3. 音效器 + 切片在**两种模式**都可用。
4. MUSIC 播放页采用参考 App A 布局：底部悬浮组件、封面双色调 + 流动/音频可视化背景、居中歌词（跳转/滚动两种）、模式按钮 2 秒后自动淡出。
5. 新增**发现页**（播放与个人主页之间）+ 音源设置 + 在线搜索/流媒体播放。
6. 个人主页登录在线音乐平台（网易云/QQ 必做，账号密码 + 扫码；Spotify/酷狗可选）。
7. Apple 风格**弹性按压**按钮。
8. 白天/夜晚主题切换 + 英文全量翻译。
9. 关于页加 GitHub 链接（https://github.com/JiaoZi258/ASMRHelper）。

### 非目标（本次不做）
- 不逐字移植参考 App 的 GPU shader（WebGPU/Pixi.js），改为**概念复刻**（Compose 动画 + 取色渐变 + ExoPlayer 音量驱动）。
- 不嵌入 JS 引擎跑任意音源脚本（用户已选方案 B：音源地址/配置）。
- 不做桌面壁纸、Discord RPC、mod 插件系统、AI 歌词分段（参考 App A 的这些能力不移植）。

### 反目标（做成这样就算失败）
- 破坏既有 ASMR 特色功能（环境音/白噪音、催眠、双耳节拍、音量触发、可视化、空间音频）。
- 回归刚修好的两个 bug（音量阈值归属、断开设备停止/其他应用暂停）。
- 在 App 内出现竞品名称或近似标识。

## 3. 约束

- 技术栈不变：Kotlin + Jetpack Compose + Material 3 + Media3 ExoPlayer + Hilt + Room + OkHttp。
- `minSdk = 26`（`AudioDeviceCallback` 无需版本守卫）。
- 所有在线平台 API 均为**第三方/非官方**，存在风控与 ToS 风险（见 §8 待确认）。
- 语言资源集中在 `strings.xml`，主题集中 `ui/theme/`。

## 4. 已确认决策（用户拍板，改版以此为准）

| 项 | 决策 |
|---|---|
| 登录平台 | 网易云 + QQ 音乐必做（账号密码 + 扫码都要）；Spotify、酷狗可选 |
| 自定义音源 | 方案 B：音源地址/配置（用户填 API 端点），不嵌 JS 引擎 |
| 图片模块 | 保留原功能，入口移到播放页菜单 |
| 音效器 + 切片 | ASMR / MUSIC 两种模式都可用 |
| ASMR 特色功能 | 环境音/白噪音、催眠、双耳节拍、音量触发特效、音频可视化、空间音频——只留 ASMR 模式，MUSIC 模式隐藏 |
| 主题 | 手动 + 跟随系统（默认跟随系统，可手动覆盖白天/夜晚） |
| 语言 | 英文全量翻译 |
| 优先级 | 先修 bug（已完成）→ 再全局改造 |
| 音源落地 | **全部走「音源地址」**：App 只做通用 HTTP 适配器 + 平台预设（网易云/QQ/酷狗/Spotify 各给一个默认地址，可改），不做原生加密/抓流 |
| 发现页内容 | 搜索 + 热门推荐 + 排行榜 + 最近播放 |
| 第三方 API 风险 | 接受 ToS/风控风险，正常接入（预设默认地址，开箱即用） |

## 5. 总体架构变化

### 5.1 双模式系统（ASMR / MUSIC）

- 新增 `PlaybackMode` 枚举：`ASMR` / `MUSIC`，持久化到 SharedPreferences（默认 ASMR）。
- 左上角圆形按钮切换，状态用 `StateFlow` 广播。
- **功能可见性矩阵**（MUSIC 模式隐藏 ASMR 专属项）：

| 功能 | ASMR | MUSIC |
|---|---|---|
| 音效器（响度/音量阈值/均衡等） | ✅ | ✅ |
| 切片 | ✅ | ✅ |
| 环境音 / 白噪音 | ✅ | ❌ |
| 催眠模式 | ✅ | ❌ |
| 双耳节拍 | ✅ | ❌ |
| 音量触发特效 | ✅ | ❌ |
| 音频可视化 | ✅ | ❌ |
| 空间音频 | ✅ | ❌ |

### 5.2 页面结构与导航（去底部栏 → 全屏滑动）

- 顶层 3 个全屏页，用 `HorizontalPager` 左右滑动切换：**播放 → 发现 → 个人主页**。
- 删除 `BottomNavBar` 与 `Scaffold` bottomBar；`AnimatedContent` 换为 Pager 手势 + 分页动画。
- **设置**不再是顶层标签：改为全屏页，从「播放页菜单」和「个人主页齿轮图标」两处进入。
- **图片（slideshow）**入口移到播放页菜单（保留 `ImageSlideshowContent` 全部功能）。
- 顶部黑条：沉浸式全屏 `edge-to-edge`，状态栏透明（现有 `PlayScreenV2` 已有雏形，推广到所有顶层页）。
- 播放页子页（播放列表 / 曲库 / 背景图库 / 触发板 / 睡眠日志 / 图片 / 设置）沿用现有 `SubScreen` 机制。

### 5.3 音乐音源层（核心新增）

逆向确认：参考 App A 用 4 个现成 npm 包做音源后端（网易云 `@neteasecloudmusicapienhanced/api`、QQ `@yakult-green-tea/qq-music-api`、酷狗 `kugoumusicapi`、波点 `bodian-music-api`）；参考 App B 用 `musicSdk/<平台>` 统一接口。二者证明「统一接口 + 多平台实现」是正确抽象。

**落地方式（用户拍板：全部走「音源地址」）**：App 内不实现任何平台的加密登录或抓流协议，
只做一个**通用 HTTP 适配器**，把「音源地址」上的 HTTP/JSON 接口归一化为统一接口。
加密、风控、Cookie 维护全部交给音源地址对应的服务端完成。

```kotlin
interface MusicSource {
    val id: String                    // "netease" | "qq" | "kugou" | "spotify" | "custom"
    val displayName: String
    fun search(keyword: String, page: Int): Flow<List<RemoteSong>>
    fun getSongUrl(songId: String, quality: String): Flow<String>
    fun getLyric(songId: String): Flow<RemoteLyric>
    fun getCoverUrl(songId: String): Flow<String>
    fun getSongDetail(songId: String): Flow<RemoteSong>
    fun getPlaylist(playlistId: String): Flow<RemotePlaylist>
    fun getRecommendations(): Flow<List<RemoteSong>>
    fun getCharts(): Flow<List<RemoteChart>>
    fun loginAccount(username: String, password: String): Flow<Result<Unit>>
    fun loginQr(): Flow<QrLoginState>       // 二维码会话（服务端生成 key，App 轮询状态）
    fun logout()
    val isLoggedIn: StateFlow<Boolean>
}
```

- **唯一实现 `HttpMusicSource`**：持有一个 `SourceConfig`（baseUrl + 协议类型 + 登录配置），用 OkHttp 发请求并解析 JSON。
- **平台预设 `SourcePreset`**：网易云 / QQ / 酷狗 / Spotify / 自定义，每个预设 = 一个**默认音源地址** + 协议适配器。
  内置平台只是「预填了一个默认地址」，用户可在「音源设置」里改地址或新增自定义源。
- 协议适配器：针对常见约定（参考 App A/B 的 API 契约）做归一化，把不同返回结构映射到统一实体 `RemoteSong` / `RemotePlaylist` / `RemoteLyric`。
- 播放：把 `getSongUrl` 返回的 URL 交给 ExoPlayer 流式播放。
- 登录：账号密码 / 扫码都通过音源地址的登录接口完成（服务端做加密），App 只保存返回的 Cookie/Token（Room + 加密 SharedPreferences）。

> 好处：无原生加密工作量、无风控封号风险（在 App 侧）、与自定义音源完全统一。
> 代价：依赖用户配置的音源服务可用性；内置平台的开箱体验取决于预设默认地址的稳定性。

### 5.4 主题与语言

- 新增 `ThemeMode`：`FOLLOW_SYSTEM`（默认）/ `LIGHT` / `DARK`，持久化。
- 现有 `DarkAppColors` + 新增 `LightAppColors`，`MaterialTheme` 按 ThemeMode 选择。
- 沉浸式播放页保持「视觉深色」但不再写死 `DarkAppColors`（改为跟随主题 + 封面取色的动态叠加）。
- 所有硬编码中文迁移到 `values/strings.xml` + `values-en/strings.xml`。

## 6. 十一项需求逐一落地

1. **去底部导航 / 全屏 / 滑动 / 图片入菜单 / 去黑条**：见 §5.2。
2. **模式切换圆形按钮**：播放页左上角圆形按钮，图标随模式变化（ASMR ↔ MUSIC），弹性按压（见第 9 项）。
3. **音效器 + 切片双模式可用**：把音效器面板与切片入口从 ASMR 专属逻辑中解耦，放入两模式共用的播放页菜单。
4. **Folia 风格 UI + 透明按钮**：半透明 Surface、毛玻璃（`Modifier.blur` + 半透明背景）、去多余边框。
5. **发现页**：位于播放与个人主页之间；四个内容块自上而下 = **搜索框 → 热门推荐 → 排行榜 → 最近播放**；点任意歌曲/榜单进入在线流媒体播放；「音源设置」在设置页新增一节，管理各平台音源地址（预设默认地址、可改、可加自定义源）与登录态。
6. **平台登录**：个人主页新增登录入口，网易云/QQ 支持账号密码 + 扫码；Spotify/酷狗为可选占位。
7. **MUSIC 播放页 Folia 布局**（重点，见下 §6.7 分解）。
8. **无竞品名**：UI/文案/字符串中不出现任何竞品名，仅用「歌词模式」「沉浸播放」等中性词。
9. **Apple 弹性按压**：自定义 `Modifier`（`pressScale` + 弹簧回弹 `spring`），全局按钮统一替换。
10. **白天/夜晚 + 英文**：见 §5.4。
11. **关于页 GitHub 链接**：`ProfileScreen` 关于区 + `SettingsScreen` 关于项，可点击跳转 `https://github.com/JiaoZi258/ASMRHelper`。

### 6.7 MUSIC 播放页 Folia 布局分解（概念复刻）

| 参考 App A 组件 | 复刻方案（Compose） |
|---|---|
| 封面双色调 `VividLightBlend` + `LatticePosterTintSurfaceView` | 用 `androidx.palette` 从封面提取主/次色，生成双色径向/线性渐变背景 |
| 流动背景 `bravaisAlbumWall` / `Lattice` | 封面图模糊放大 + 缓慢位移/缩放动画（`rememberInfiniteTransition`） |
| 音频可视化 `Visualizer*`（15 风格） | ExoPlayer `AudioLevel` + 采样，`Canvas` 绘制波形/频谱，保留 2~3 个内置风格 |
| 居中歌词 `LatticeLyrics` / `monetLyricMotion` + `glowWordVariants` | 居中 `LazyColumn`，高亮当前行 + 弹性滚动；「跳转/滚动」两种模式；当前词发光 |
| 悬浮底部控制 `FoliumCommandSurfaceView` | 底部悬浮半透明控制条（播放/暂停、进度、上下曲） |
| 灵动岛式 toast `NowPlayingToastTransitionBorder` | 切歌时顶部胶囊提示，带圆角描边 + 进出场动画 |
| 模式按钮自动淡出 | 2 秒无操作后 `animateFloatAsState` 淡出左上角按钮，触摸时恢复 |

## 7. 里程碑划分（分阶段实施，每阶段可独立编译/验证）

- **M0（已完成）**：修 Bug 1 + Bug 2，已推送。
- **M1 全局改造**：双模式系统 + 去底部导航（Pager 三页）+ 图片/设置入口调整 + 主题（白天/夜晚）+ 英文 + 关于页 GitHub + Apple 弹性按压。**无网络功能**，风险最低，可先在真机验证。
- **M2 音源层 + 发现页**：`MusicSource` 抽象 + 网易云/QQ 实现（含登录）+ 自定义音源地址 + 发现页搜索/推荐 + 在线流播放。
- **M3 MUSIC 播放页视觉**：封面双色调 + 流动背景 + 音频可视化 + 居中歌词 + 悬浮控制 + 灵动岛提示 + 模式按钮淡出。
- **M4（可选）**：酷狗/Spotify、更多可视化风格、打磨动画。

> 建议先做 M1 并真机验收，再进 M2/M3（网络与视觉风险更高）。

## 8. 已拍板决策（2026-10-17 会话确认）

1. **音源落地 = 全部走「音源地址」**：App 只做通用 HTTP 适配器 + 平台预设，不做原生加密/抓流。
2. **发现页内容 = 搜索 + 热门推荐 + 排行榜 + 最近播放**。
3. **第三方 API 风险 = 接受**，正常接入，预设默认地址开箱即用。

> 无其他未决项。设计已完整，待批准后进入 superpowers:writing-plans 出实施计划。
