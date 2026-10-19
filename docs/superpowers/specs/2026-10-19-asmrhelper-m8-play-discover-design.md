# ASMRHelper M8 设计：播放页悬浮组件增强 + 发现页改版 + 在线播放修复

日期：2026-10-19
状态：待批准

## 意图与动机

用户在真机上验收 M7 后反馈了一批体验问题，核心诉求有三类：

1. **在线歌曲全部无法播放**（本地歌正常）——这是阻断级 bug，必须最先解决。
2. **播放页（MUSIC 模式）悬浮组件不够好用**：缺爱心/播放模式，菜单项太少，进度条样式不对，背景不够 Folia。
3. **发现页信息层级差**：四个板块每次要往下拉，搜索框贴状态栏，模块不可定制。

## 目标 / 非目标 / 反目标

**目标**
- 在线（网易云/QQ）免费歌曲能正常播放，VIP 歌给出明确提示。
- 悬浮组件：爱心收藏、播放顺序四模式、⋮ 菜单含歌曲信息/背景/音质/播放队列/歌词偏移、网易云式进度条。
- 背景三模式：虚化封面（默认）/ Folia 双色油画漫游（概念复刻）/ 跟随系统深浅纯色。
- 发现页：横向四 Tab（每日推荐/热门推荐/排行榜/电台）+ 下方可折叠用户歌单；搜索框下移；设置里可开关各模块。

**非目标**
- 不做 Folia 的 GPU shader 原样移植（WebGPU/GLSL 无法在 Compose 运行），双色油画按「概念复刻」。
- 不做平台在线红心（爱心=本地「我喜欢」收藏）。
- 音质切换不重播当前曲目（只影响下一首）。

**反目标（做了就算失败）**
- 在线歌曲继续静默失败、无任何提示。
- 英文歌词单词粘连（helloworld）。
- 改版后信息层级反而更乱、更慢。

## 已拍板决策（用户 2026-10-19 确认）

| 主题 | 决策 |
|---|---|
| 发现页布局 | 横向 Tab 切换（每日推荐/热门推荐/排行榜/电台），用户歌单下方可折叠 |
| 悬浮 ⋮ 菜单 | 独立菜单项：歌曲信息 / 背景 / 音质 / 播放队列 / 歌词偏移，各自打开小弹层 |
| 背景模式 | 三种全要：虚化封面 / Folia 双色油画漫游 / 跟随系统深浅纯色，默认虚化封面 |
| 音质位置 | 悬浮 ⋮ 菜单/弹层里临时选 |
| 爱心按钮 | 收藏到本地「我喜欢」（跨音源、本地持久化，无需登录） |
| 播放模式 | 顺序播放 / 列表循环 / 单曲循环 / 随机播放，默认列表循环 |
| 纯色背景 | 跟随系统深/浅色（浅色模式浅底、深色模式深底） |
| 音质切换 | 只影响下一首 |
| 播放队列 | 点发现页某首歌 → 当前可见列表成为队列，从点击项开始播 |

## 约束

- 现有技术栈不动：Kotlin + Compose + Material3 + Media3/ExoPlayer + Hilt + Room + OkHttp + org.json。
- 字符串全部走 `values/strings.xml` + `values-en/strings.xml`（`StringResourcesTest` 守卫 parity）。
- 每个里程碑 commit + push 到 `main`，提交信息以 `Co-Authored-By: Claude Code <noreply@anthropic.com>` 结尾。

## 各部分设计

### 1. 阻断 bug：在线歌曲无法播放（根因已定位）

**症状**：所有在线（网易云/QQ）歌曲播放失败，本地文件正常。

**根因**：网易云/QQ 的流媒体 CDN（`music.126.net` / `y.qq.com`）会校验请求的 `Referer` 与 `User-Agent` 头，缺失时返回 403。当前 `PlayerManager` 用 `MediaItem.fromUri(url)` 直接构建，ExoPlayer 用默认 `DefaultHttpDataSource` 发起请求，不带任何 Referer/User-Agent，导致所有在线流被 403 拒绝。本地文件不经 HTTP，故不受影响。

**修复**：给在线流的 `MediaItem` 按 `audio.sourceId` 设置请求头：

- 网易云（`sourceId == "netease"`）：`Referer: https://music.163.com` + 浏览器 `User-Agent`。
- QQ（`sourceId == "qq"`）：`Referer: https://y.qq.com/` + 浏览器 `User-Agent`。

实现：`PlayerManager` 中新增 `buildMediaItem(audio)` 统一构建（`MediaItem.Builder().setUri(...).setHttpRequestHeaders(...)`，精确 API 在实现时按项目 Media3 版本核对），替换现有 3 处主播放器的 `MediaItem.fromUri(audio.filePath)`。本地文件（`audio.isRemoteSong()` 为假）保持 `fromUri` 不带头。

**附带**：点播放后加「正在获取歌曲链接」加载态（取流 `getSongUrl` 期间的进度反馈）；取流超时/空结果仍走现有 `discover_play_failed` 提示，不再静默。

### 2. 悬浮组件（FoliaCommandBar）增强

在现有「折叠=歌名胶囊 / 展开=歌名+作者+进度+上一曲/播放/下一曲/更多」基础上：

- **爱心按钮**：展开区控制行加一个爱心 `IconButton`，点按把当前歌收藏到本地「我喜欢」收藏夹（复用现有收藏仓储，收藏/取消切换，填充/描边心形）。
- **播放模式按钮**：展开区加循环图标按钮，点按在 顺序播放→列表循环→单曲循环→随机播放 间循环切换，图标随模式变化。映射到 `PlayerManager` 的 `repeatMode` / `shuffleModeEnabled`：顺序=`REPEAT_MODE_OFF`+shuffle off；列表循环=`REPEAT_MODE_ALL`；单曲循环=`REPEAT_MODE_ONE`；随机=`shuffle on`（列表循环基底）。默认列表循环。
- **进度条**：把 Material3 默认 `Slider` 的自定义 `thumb`/`track` 换成「细横杠 + 圆点」：`track` 高约 2–3dp 的细条（active 用 accent、inactive 用半透明），`thumb` 为实心小圆点（约 10dp），贴合网易云观感。
- **⋮ 更多菜单**：从「复用全量 AsmrDropdownMenu」改为**只挂本页相关项**——歌曲信息 / 背景 / 音质 / 播放队列 / 歌词偏移（见下）。原 ASMR 菜单（图片/收藏/扫描/文件管理/播放列表/背景图库/触发垫/睡眠日志/视频音频/设置）保留在页面右上角入口，不动。

### 3. 背景系统（三种模式，可切换）

新增 `PlaybackBackgroundMode` 枚举：`BLURRED_COVER`（默认）/ `FOLIA_DUOTONE` / `SYSTEM_SOLID`，持久化到设置。

- `BLURRED_COVER`：现有 `AudioReactiveBackground`（虚化封面 + 双色调渐变 + FFT 柔光柱）。
- `FOLIA_DUOTONE`：**概念复刻** Folia 的 `VividLightBlend`/`LatticePosterTintSurfaceView` 观感——提取封面两种主色（`rememberCoverPalette` 已有 vibrant/darkMuted），用两张「油画笔触」质感的低速漂移渐变层（不同方向/速度的 `Brush.linearGradient`/`radialGradient`，`rememberInfiniteTransition` 驱动 offset/rotation）叠出「两种色调像油画一样漫游」，不用封面底图、不显示封面。无封面时退回 accent 派生的双色。
- `SYSTEM_SOLID`：跟随系统深浅色——浅色模式用浅色底、深色模式用深色底（读 `ThemeMode`/系统 isSystemInDarkTheme），纯色，不随音频。

切换入口：⋮ 菜单 →「背景」小弹层（三个选项 + 当前选中勾）。切换即时生效并持久化。

### 4. 音质选择

- `MusicSource.getSongUrl(songId, quality)` 已有 `quality` 参数（默认 `"standard"`），但 `NeteaseMusicSource` 目前忽略它、固定 999000→320000→128000 回退。改为按 `quality` 选择 `br`：
  - 网易云：`standard=128000` / `higher=192000` / `excellent=320000` / `lossless=999000`。
  - QQ：`standard` / `higher` / `lossless` 映射到 vkey 请求的音质档。
- 新增 `PlaybackQuality` 枚举 + 持久化；`PlayViewModel`/`DiscoverViewModel` 取流时传入用户所选档位。
- 语义：**只影响下一首**——切换后不重播当前曲目，从下一首起用新档位（当前曲目继续按原档位播放）。
- 入口：⋮ 菜单 →「音质」小弹层（档位列表 + 当前选中勾；VIP 档位拿不到地址时逐档回退到可用档，最终回退免费外链）。

### 5. 发现页改版

- **搜索框下移**：`DiscoverScreen` 顶部加 `statusBarsPadding()`（或 windowInsets），避免贴状态栏。
- **四 Tab 横向切换**：搜索框下 `每日推荐 / 热门推荐 / 排行榜 / 电台` 四个 `Tab`/chip 横排，选中项高亮；下方 `HorizontalPager` 或 `when(selectedTab)` 只显示选中板块的内容。
- **用户歌单可折叠**：四 Tab 内容区下方固定「我的歌单」板块，标题可点折叠/展开（复用现有 `expandedPlaylistId`/`toggleUserPlaylist` 逻辑）。
- **最近播放**（现有模块）：**默认保留为一个可折叠板块**，放在「我的歌单」下方（不在四 Tab 里）。见「我的默认值」——如需改可提出。
- **发现页设置**：设置页新增「发现页设置」板块，六个开关：每日推荐 / 热门推荐 / 排行榜 / 电台 / 最近播放 / 我的歌单。关闭后对应 Tab/板块不渲染；持久化。设置改完回发现页即时生效（读同一个 StateFlow/DataStore）。

### 6. 播放队列

- 点发现页某首歌 → 把**当前可见列表**（该板块当前展开/显示的所有歌）作为队列，从点击项开始播（`PlayerEvent.Play(audio, fullList)`，并定位起始索引）。
- ⋮ 菜单 →「播放队列」弹层：列出当前队列，高亮正在播放项，点其他项跳播。
- 队列使 列表循环/随机 真正作用于一组歌（不再永远只有 1 首）。

### 7. 歌曲信息弹层

⋮ 菜单 →「歌曲信息」弹层，展示：
- 大封面、歌名、作者、所属专辑、发行时间（`publishTime` 转日期；拿不到隐藏该行）。
- 需要 `RemoteSong` 增加 `publishTime`（秒级时间戳）字段，`NeteaseJson.parseSong` 从 `al.publishTime`/`s.publishTime` 解析；QQ 侧能取到就取、取不到留空。

### 8. 歌词偏移校准 + 英文空格 bug

- **英文歌词粘连（helloworld）根因**：`WordLyric.tokenizeWords` 把空格当词分隔符丢弃，展示时 `CenteredLyrics` 直接 `append(word.text)` 拼接，导致 `hello world → helloworld`。修复：`tokenizeWords` 保留词间空格（把空白附着到前一个词的尾部），CJK 不受影响（中文无空格）。
- **歌词偏移校准**：⋮ 菜单 →「歌词偏移」弹层，滑块 ±2000ms、步进 100ms，全局生效（`PlayViewModel` 取词时把 `progressMs + offset` 作为歌词时间），持久化到设置。默认 0。

## 我的默认值（未逐一追问，实现时以此为准，可改）

1. **最近播放**：保留为「我的歌单」下方的一个可折叠板块，不进四 Tab；在「发现页设置」里可关。
2. **音质档位命名**：网易云 标准/较高/极高/无损；QQ 标准/高品/无损。
3. **音质持久化**：全局一个档位（不按音源分），存设置；QQ 无对应档时自动回退到最接近档。
4. **歌词偏移**：全局（不分歌曲），持久化。
5. **发行时间**：显示到「年」或「年-月」（拿到的精度而定）；取不到整行隐藏。
6. **背景切换入口**：仅 ⋮ 菜单，不进设置页（设置页只做「发现页设置」）。
7. **爱心去重**：同一首歌收藏为幂等（再点取消收藏）。

## 留给实现者的

- 具体 Media3 `MediaItem` 请求头 API 名与 `setHttpRequestHeaders` 的 Map 类型（`Map<String,List<String>>` 或 `Map<String,String>`）按项目 Media3 版本核对。
- `repeatMode`/`shuffleModeEnabled` 与四种模式的映射、切歌时 shuffle 状态的处理。
- 播放队列的数据源（发现页「当前可见列表」如何准确捕获）。
- 新增字符串键的命名与中英 parity（跟随现有 `discover_*` / `play_*` 前缀）。
