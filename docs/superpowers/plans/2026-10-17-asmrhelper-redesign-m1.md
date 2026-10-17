# ASMRHelper 全局改版 M1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把单模式 ASMR 播放器升级为双模式（ASMR/MUSIC）播放器：三页全屏滑动导航、三态主题（跟随系统/白天/夜晚）、播放页菜单入口调整、关于页 GitHub 链接、Apple 弹性按压、英文翻译基础设施。

**Architecture:** 现有 Kotlin + Jetpack Compose + Material 3 + Hilt + Room 栈不变。M1 只做结构/行为改造与主题/语言基础设施，不引入网络音源（M2）与 Folia 视觉（M3）。用 `HorizontalPager` 替代 `Scaffold`+`BottomNavBar`；用 `ThemeMode` 三态替代现有 `dark_theme` 布尔；用 `PlaybackMode` 枚举承载双模式与功能可见性矩阵。

**Tech Stack:** Kotlin、Jetpack Compose（Material 3）、Hilt、kotlinx.coroutines Flow、SharedPreferences、JUnit4（本计划新增最小单测）。

**Spec:** `docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md`

## Global Constraints

- `minSdk = 26`、`compileSdk = 35`、`targetSdk = 35`、Java/Kotlin `jvmTarget = 17`。
- 任何 UI 文案/字符串不得出现竞品名（Folia / LX Music），只用「歌词模式」「沉浸播放」等中性词。
- 用户可见字符串进入 `res/values/strings.xml`（中文）与 `res/values-en/strings.xml`（英文）。
- 设置持久化统一走 `SettingsRepository`（SharedPreferences `"asmr_settings"`），StateFlow 广播。
- 提交信息结尾必须带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`；永远不 stage `.claude/settings.local.json`。
- 验证命令（本机无 device，真机验收由用户后续接 OPPO 后执行）：
  `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin`
  单测：`JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest`

## Review Focus

本计划最容易咬人的五类输入/条件（对应任务已各加测试）：

1. **主题跟随系统要在运行时响应系统切换**，不只是启动时读一次 → 用 `isSystemInDarkTheme()` 在 Compose 里响应式求值（Task 2）。
2. **主题迁移不得把老用户从深色改浅**：已存 `dark_theme=true` 的老用户迁移后必须仍为深色（DARK）→ 迁移纯函数单测（Task 2）。
3. **Pager 滑动不得与播放页自身横向手势冲突**（歌词滚动、滑杆）→ Pager 只拦截整页手势，页内滚动由子组件消费；手动验证 + 编译（Task 3）。
4. **模式切换要跨重启/进程死亡持久化**：`playback_mode` 存 SharedPreferences（Task 1）。
5. **英文切换不得因残留硬编码中文而崩溃**：字符串迁移覆盖导航/设置/菜单/关于/个人主页五处 chrome，深层屏幕（播放页/曲库/播放列表/睡眠/触发器/图片）列为后续（Task 6，如实标注）。

---

### Task 1: 双模式系统（PlaybackMode 数据层 + 可见性矩阵）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/domain/model/PlaybackMode.kt`
- Modify: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/build.gradle.kts`（新增 `testImplementation`）
- Test: `app/src/test/java/com/asmrhelper/domain/model/PlaybackModeTest.kt`

**Interfaces:**
- Produces: `enum class PlaybackMode { ASMR, MUSIC }`；`fun PlaybackMode.isMenuItemVisible(itemId: String): Boolean`；`SettingsRepository.getPlaybackMode(): Flow<Int>`、`suspend fun setPlaybackMode(mode: Int)`。常量 `val ASMR_ONLY_MENU_IDS = setOf("trigger_pad", "sleep_journal")`。

- [ ] **Step 1: 加最小单测依赖**

在 `app/build.gradle.kts` 的 `dependencies {}` 加一行 `testImplementation("junit:junit:4.13.2")`。

- [ ] **Step 2: 写失败测试**

`PlaybackModeTest.kt`：

```kotlin
class PlaybackModeTest {
    @Test fun asmrShowsAllMenuItems() {
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("trigger_pad"))
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("sleep_journal"))
        assertTrue(PlaybackMode.ASMR.isMenuItemVisible("favorites"))
    }
    @Test fun musicHidesAsmrOnlyItems() {
        assertFalse(PlaybackMode.MUSIC.isMenuItemVisible("trigger_pad"))
        assertFalse(PlaybackMode.MUSIC.isMenuItemVisible("sleep_journal"))
        assertTrue(PlaybackMode.MUSIC.isMenuItemVisible("favorites"))
        assertTrue(PlaybackMode.MUSIC.isMenuItemVisible("background"))
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest`
Expected: 编译失败（`PlaybackMode` 未定义）。

- [ ] **Step 4: 实现 `PlaybackMode.kt`**

```kotlin
enum class PlaybackMode { ASMR, MUSIC }
val ASMR_ONLY_MENU_IDS = setOf("trigger_pad", "sleep_journal")
fun PlaybackMode.isMenuItemVisible(itemId: String): Boolean =
    this == PlaybackMode.ASMR || itemId !in ASMR_ONLY_MENU_IDS
```

- [ ] **Step 5: 跑测试确认通过**

Run: `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest`
Expected: 3 断言全 PASS。

- [ ] **Step 6: 持久化到 `SettingsRepository` + `Impl`**

在接口加 `fun getPlaybackMode(): Flow<Int>` / `suspend fun setPlaybackMode(mode: Int)`；在 `Impl` 加 `private val _playbackMode = MutableStateFlow(prefs.getInt("playback_mode", 0))` 及两个 override（`putInt("playback_mode", mode)` + 更新 StateFlow）。沿用现有 `themePresetOrdinal` 的同款写法。

- [ ] **Step 7: 编译 + 提交**

Run: `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。然后 `git add` 四个文件（不含 `.claude/settings.local.json`）并提交：
`feat: 双模式 PlaybackMode 数据层与功能可见性矩阵`（结尾带 Co-Authored-By 行）。

---

### Task 2: 三态主题（ThemeMode + 跟随系统 + 迁移）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/theme/ThemeMode.kt`
- Modify: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt`（`getDarkTheme`/`setDarkTheme` → `getThemeMode`/`setThemeMode`）
- Modify: `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/asmrhelper/MainActivity.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt`（外观页深色 Switch → 三态选择）
- Test: `app/src/test/java/com/asmrhelper/ui/theme/ThemeModeTest.kt`

**Interfaces:**
- Consumes: Task 1 的仓库模式（同款 StateFlow 写法）。
- Produces: `enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }`；`fun ThemeMode.resolveDark(isSystemDark: Boolean): Boolean`；`fun migrateDarkThemeToThemeMode(saved: Boolean?): ThemeMode`；`SettingsRepository.getThemeMode(): Flow<Int>`、`suspend fun setThemeMode(mode: Int)`。

- [ ] **Step 1: 写失败测试**

`ThemeModeTest.kt`：

```kotlin
class ThemeModeTest {
    @Test fun followSystemResolvesToSystem() {
        assertTrue(ThemeMode.FOLLOW_SYSTEM.resolveDark(true))
        assertFalse(ThemeMode.FOLLOW_SYSTEM.resolveDark(false))
    }
    @Test fun manualOverridesSystem() {
        assertTrue(ThemeMode.DARK.resolveDark(false))
        assertFalse(ThemeMode.LIGHT.resolveDark(true))
    }
    @Test fun migrateKeepsExistingDarkUsersDark() {
        assertEquals(ThemeMode.DARK, migrateDarkThemeToThemeMode(true))
        assertEquals(ThemeMode.LIGHT, migrateDarkThemeToThemeMode(false))
        assertEquals(ThemeMode.FOLLOW_SYSTEM, migrateDarkThemeToThemeMode(null))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `:app:testDebugUnitTest`，Expected: 编译失败（`ThemeMode` 未定义）。

- [ ] **Step 3: 实现 `ThemeMode.kt`**

```kotlin
enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }
fun ThemeMode.resolveDark(isSystemDark: Boolean): Boolean =
    when (this) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
fun migrateDarkThemeToThemeMode(saved: Boolean?): ThemeMode =
    when (saved) { true -> ThemeMode.DARK; false -> ThemeMode.LIGHT; null -> ThemeMode.FOLLOW_SYSTEM }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `:app:testDebugUnitTest`，Expected: 3 断言全 PASS。

- [ ] **Step 5: 仓库替换 `dark_theme` → `theme_mode`（含一次性迁移）**

`SettingsRepositoryImpl`：删 `_darkTheme`/`getDarkTheme`/`setDarkTheme`；加
`private val _themeMode = MutableStateFlow(migrateDarkThemeToThemeMode(prefs.run { if (contains("dark_theme")) getBoolean("dark_theme", true) else null }).ordinal)`
及 `getThemeMode(): Flow<Int>` / `setThemeMode(mode: Int)`（`putInt("theme_mode", mode)`）。迁移只读不写回 `dark_theme`（保留旧键无害）。

- [ ] **Step 6: 更新 `SettingsViewModel`**

把 `darkTheme: StateFlow<Boolean>` 与 `setDarkTheme` 换成 `themeMode: StateFlow<Int>` 与 `setThemeMode(mode: Int)`（`stateIn(..., 0)`），沿用现有 `viewModelScope.launch` 模式。

- [ ] **Step 7: 更新 `MainActivity` 用三态 + 跟随系统**

`MainActivity.setContent`：收集 `themeMode` 的 ordinal；`val darkTheme = ThemeMode.entries.getOrElse(mode) { FOLLOW_SYSTEM }.resolveDark(isSystemInDarkTheme())`（`isSystemInDarkTheme` 来自 `androidx.compose.foundation.isSystemInDarkTheme`）；把 `darkTheme` 继续传给 `ASMRHelperTheme` 与 `SideEffect` 状态栏图标。删除对 `getDarkTheme()` 的引用。

- [ ] **Step 8: 更新 `SettingsScreen` 外观页**

`AppearanceSettingsScreen`（约 897–935 行）：`val darkTheme by viewModel.darkTheme` → `val themeMode by viewModel.themeMode`；把「深色模式」Switch 替换为三态选择（跟随系统 / 浅色 / 深色，`FilterChip` 或 Row 三按钮），`onClick` 调 `viewModel.setThemeMode(mode.ordinal)`，副标题显示当前态中文名。

- [ ] **Step 9: 编译 + 提交**

Run: `:app:compileDebugKotlin`，Expected: BUILD SUCCESSFUL。提交：`feat: 三态主题（跟随系统/白天/夜晚）+ 老用户迁移`。

---

### Task 3: 导航重构（Pager 三页 + 去底栏 + 模式按钮 + 图片/设置入口 + 发现页占位）

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`
- Delete: `app/src/main/java/com/asmrhelper/ui/navigation/BottomNavBar.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`（加 `playbackMode` StateFlow）
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreenV2.kt`（左上角模式按钮 + 菜单加「图片/设置」+ 按模式过滤菜单）
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`（菜单加「图片/设置」，与 V2 对齐）
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/ProfileScreen.kt`（顶栏齿轮 → 设置 + 关于区）

**Interfaces:**
- Consumes: Task 1 的 `PlaybackMode`、`isMenuItemVisible`；Task 2 的 `ThemeMode`（无直接依赖）。
- Produces: `Screen` 三页枚举 `Play / Discover / Profile`（`bottomNavItems` 删除）；`SubScreen` 新增 `Slideshow("slideshow")`、`Settings("settings")`；`PlayViewModel.playbackMode: StateFlow<Int>`、`fun setPlaybackMode(mode: Int)`。

- [ ] **Step 1: 改 `Screen.kt`**

`Screen` 保留 `Play/Profile`，删 `Slideshow`、`Settings`（它们不再是顶层标签），新增 `Discover("discover", "发现", ...)`；删除 `bottomNavItems` companion 属性。`SubScreen` 新增 `Slideshow("slideshow")`、`Settings("settings")`。

- [ ] **Step 2: 加 `PlayViewModel.playbackMode`**

`PlayViewModel` 加 `val playbackMode: StateFlow<Int> = settingsRepository.getPlaybackMode().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)` 与 `fun setPlaybackMode(mode: Int) { viewModelScope.launch { settingsRepository.setPlaybackMode(mode) } }`（确保 `settingsRepository` 已在构造器注入；若未注入则加 `@Inject`）。

- [ ] **Step 3: 建 `DiscoverScreen.kt` 占位**

`@Composable fun DiscoverScreen(modifier: Modifier = Modifier)`：居中显示「发现」标题 + 一句占位副标题（内容 M2 填充），背景用 `DarkBackground`。

- [ ] **Step 4: 重写 `AsmrNavHost.kt` 为 Pager**

把 `Scaffold`+`BottomNavBar`+`AnimatedContent` 替换为 `HorizontalPager(state = rememberPagerState { 3 })`，三页 = Play / Discover / Profile；删 `BottomNavBar` 调用。保留 `playSubScreen`/`settingsSubScreen` 子页逻辑（Play 页内按 `playSubScreen` 渲染，含新增 `Slideshow`/`Settings` 分支）。设置页改由 `playSubScreen == SubScreen.Settings` 或 Profile 齿轮进入，渲染 `SettingsScreen` 全屏。播放页仍走 `PlayScreen`/`PlayScreenV2`（`playbackMode` 切换留给后续，M1 只保留现有 `playbackMode==0/1` 的经典/沉浸切换，不混用）。

- [ ] **Step 5: 删除 `BottomNavBar.kt`**

删除文件并确认无残留 import（`grep -r "BottomNavBar" app/src/main` 应为空）。

- [ ] **Step 6: 播放页左上角模式按钮 + 菜单入口**

`PlayScreenV2` 顶栏最左加圆形按钮：`collect` `viewModel.playbackMode`，点击在 `ASMR`/`MUSIC` 间 `viewModel.setPlaybackMode(...)`，显示当前模式图标/文案。菜单 `items` 列表加 `MenuItem("slideshow", "图片")` 与 `MenuItem("settings", "设置")`，`when` 分支分别 `onNavigateToSlideshow()`、`onNavigateToSettings()`；并按 `PlaybackMode.entries[playbackMode].isMenuItemVisible(id)` 过滤 `trigger_pad`/`sleep_journal`。`PlayScreen`（经典版）同样加「图片/设置」两项并加 `onNavigateToSlideshow` 参数。

- [ ] **Step 7: `AsmrNavHost` 接线新回调**

`PlayScreen`/`PlayScreenV2` 新增 `onNavigateToSlideshow = { playSubScreen = SubScreen.Slideshow }`，`onNavigateToSettings` 改为 `{ playSubScreen = SubScreen.Settings }`（不再 `currentScreen = Screen.Settings`）；`SubScreen.Slideshow` 分支渲染 `ImageSlideshowContent()`；`SubScreen.Settings` 分支渲染 `SettingsScreen(onNavigateToHistory = ...)`。`ProfileScreen()` 加 `onNavigateToSettings = { playSubScreen = SubScreen.Settings }`。

- [ ] **Step 8: 编译（含一次 clean 强制全量）**

Run: `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle clean :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL，无 `BottomNavBar`/`Screen.Settings`/`Screen.Slideshow` 残留引用。

- [ ] **Step 9: 提交**

`git add`（不含 `.claude/settings.local.json`），提交：`feat: 三页全屏滑动导航 + 双模式按钮 + 图片/设置入口调整`。

---

### Task 4: 关于页 GitHub 链接

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt`（`AboutSettingsScreen`，约 1693–1740 行）
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: 无（Task 3 引入的 `onNavigateToSettings` 已在 ProfileScreen 存在）。
- Produces: 常量 `val GITHUB_URL = "https://github.com/JiaoZi258/ASMRHelper"`（放 `AboutSettingsScreen` 文件内或 `util`）。

- [ ] **Step 1: 设置页「关于」加可点击 GitHub 行**

在 `AboutSettingsScreen` 的「版本信息」「开源许可」下方加一行 `AboutRow`（label=「GitHub 仓库」，value=`GITHUB_URL`），`Modifier.clickable` 用 `Intent(ACTION_VIEW, Uri.parse(GITHUB_URL))` 打开；文案不得含竞品名。

- [ ] **Step 2: 个人主页加「关于」卡片**

`ProfileScreen` 统计卡片下方加一张 `Card`：显示版本号（`context.packageManager.getPackageInfo(...).versionName`）与「GitHub 仓库」可点击行，同样 `ACTION_VIEW` 打开。

- [ ] **Step 3: 编译 + 提交**

Run: `:app:compileDebugKotlin`，Expected: BUILD SUCCESSFUL。提交：`feat: 关于页添加 GitHub 仓库链接`。

---

### Task 5: Apple 弹性按压 Modifier

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/theme/Animations.kt`
- Modify: 播放页关键按钮（`PlayScreenV2.kt` 模式按钮、`components/PlayPauseButton.kt`、`components/MiniPlayer.kt` 播放按钮）

**Interfaces:**
- Produces: `fun Modifier.pressScale(scale: Float = 0.94f, onPress: () -> Unit = {}): Modifier`（`pointerInput` + `detectTapGestures` + `animateFloatAsState`，按下缩到 `scale`、`spring()` 回弹）。语义：任何按钮 `Modifier.pressScale { onClick() }` 即可获得弹性按压。

- [ ] **Step 1: 实现 `pressScale`**

在 `Animations.kt` 加 `pressScale`，用 `detectTapGestures(onPress = { pressed = true }, onTap = { pressed = false; onPress() }, onRelease = { pressed = false })` + `graphicsLayer { scaleX = scaleY = s }` + `animateFloatAsState(targetValue = if (pressed) scale else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))`。

- [ ] **Step 2: 应用到关键按钮**

模式按钮、播放/暂停按钮（`PlayPauseButton`、`MiniPlayer`）改用 `pressScale`；其余按钮暂不动（全局替换留待视觉打磨）。

- [ ] **Step 3: 编译 + 提交**

Run: `:app:compileDebugKotlin`，Expected: BUILD SUCCESSFUL。提交：`feat: Apple 风格弹性按压按钮`。

---

### Task 6: 英文翻译基础设施 + 核心字符串迁移

**Files:**
- Create: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt` + `SettingsRepositoryImpl.kt`（加语言设置）
- Modify: `app/src/main/java/com/asmrhelper/AsmrApplication.kt`（可选：应用语言 override）
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/Screen.kt`、`ui/settings/SettingsScreen.kt`（分类/标题）、`ui/components/AsmrDropdownMenu.kt`（「菜单」标题）、`ui/profile/ProfileScreen.kt`（标题/关于）、`ui/discover/DiscoverScreen.kt`

**Interfaces:**
- Consumes: Task 3 的新 Screen 枚举与菜单项、Task 4 的 About。
- Produces: `enum class AppLanguage { FOLLOW_SYSTEM, ZH, EN }`；`SettingsRepository.getAppLanguage(): Flow<Int>` / `setAppLanguage(mode: Int)`；`stringResource(R.string.xxx)` 用法示例。

- [ ] **Step 1: 加语言设置（数据层）**

`SettingsRepository` 加 `getAppLanguage(): Flow<Int>` / `setAppLanguage(mode: Int)`；`Impl` 加 `_appLanguage = MutableStateFlow(prefs.getInt("app_language", 0))`。语言枚举放 `ui/theme/AppLanguage.kt`（`FOLLOW_SYSTEM / ZH / EN`）。

- [ ] **Step 2: 建 `values-en/strings.xml` + 扩充中文**

`strings.xml` 新增本任务要迁移的键（导航标签、设置分类 label/subtitle、菜单项、外观三态名、关于、发现占位、模式名）；`values-en` 写英文对应值。`app_name` 保持 `ASMRHelper`。不得出现竞品名。

- [ ] **Step 3: 迁移五处 chrome 字符串到 `stringResource`**

`Screen` 三页 label、`AsmrDropdownMenu` 的「菜单」、`SettingsCategory` 的 label/subtitle、`AppearanceSettingsScreen` 三态名、`AboutSettingsScreen` 标题、`ProfileScreen` 标题与「关于」、`DiscoverScreen` 标题，改为 `stringResource(R.string.xxx)`。深层屏幕（播放页/曲库/播放列表/睡眠/触发器/图片内部文案）不在本任务，注释标注为后续。

- [ ] **Step 4: 编译（中英文双资源校验）**

Run: `JAVA_HOME=/f/mc gradle-dist/gradle-8.11.1/bin/gradle clean :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（若 `stringResource` 引用缺失键会在此步报错，确保每个键中英文都存在）。

- [ ] **Step 5: 提交 + 更新记忆**

提交：`feat: 英文翻译基础设施 + 核心界面字符串迁移`。并在 `memory/asmr-helper-redesign.md` 记录「深层屏幕字符串迁移为后续」。

---

## Self-Review 结论

- **Spec 覆盖**：M1 的 7 项（双模式、三态主题、三页滑动导航、图片/设置入口、发现占位、关于 GitHub、英文、Apple 按压）均有对应任务；MUSIC 的 Folia 视觉（M3）、音源层与发现内容（M2）不在本计划，由 Spec 里程碑界定。
- **类型一致**：`PlaybackMode`/`ThemeMode`/`AppLanguage`/`getPlaybackMode`/`getThemeMode`/`getAppLanguage` 命名在任务间一致；`SubScreen.Slideshow`/`SubScreen.Settings` 在 Task 3 定义并被 Task 4/6 使用。
- **比例**：计划短于代码总量，任务步骤以签名+测试断言为主，未转录实现体。
