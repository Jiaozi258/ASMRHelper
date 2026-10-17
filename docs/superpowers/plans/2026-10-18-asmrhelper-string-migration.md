# ASMRHelper 深层字符串迁移 + AppLanguage 运行态 override Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把仍硬编码在界面里的中文文案全部迁到 `values/strings.xml` + `values-en/strings.xml`，并把已落库的 `AppLanguage`（跟随系统/中文/英文）接入运行态 locale override + 设置页选择器，让「英文全量翻译」真正按 App 内设置生效。

**Architecture:** `AppLocale` 纯工具函数 `localeFor(language)` 把持久化的 int 映射为 `Locale`，`wrap(context, language)` 用 `createConfigurationContext` 包装 base context 使资源按目标 locale 解析；`MainActivity.attachBaseContext` 读 `asmr_settings` 里的 `app_language` 应用 override，设置页选择语言后 `activity.recreate()` 让新 locale 生效。字符串迁移 = 逐屏把 `Text("中文")`/`Toast`/`label`/`title` 等字面量替换为 `stringResource(R.string.<key>)`，同键同时写入两份 strings.xml（中文 + 英文）。

**Tech Stack:** Kotlin + Jetpack Compose + `java.util.Locale` + `android.content.res.Configuration`。无新依赖。测试仍仅 junit4 + org.json（字符串迁移为重构，不新增单测行为）。

**Spec:** docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md（§5.4 主题与语言：`所有硬编码中文迁移到 values/strings.xml + values-en/strings.xml`；§6 需求 10 英文全量翻译）

## Global Constraints

- minSdk 26，compileSdk 35，不升级、不加依赖。
- 只迁移**用户可见文案**（`Text`/`Toast`/`label`/`title`/`placeholder`/`contentDescription`/`Snackbar`/对话框文案）；**中文注释不迁移**（开发者面向，无需英文）。
- 每个新键同时写入 `values/strings.xml`（中文）与 `values-en/strings.xml`（英文），键名必须两文件一致。
- 键命名沿用现有 snake_case 约定，按屏加前缀：`play_*`/`library_*`/`file_*`/`download_*`/`video_*`/`playlist_*`/`sleep_*`/`trigger_*`/`image_*`/`gallery_*`/`slideshow_*`/`profile_*`/`login_*`/`history_*`/`appearance_*`/`language_*`/`nav_*`/`notif_*`。
- 编译/测试命令（bash）：
  `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
- 提交信息以 `Co-Authored-By: Claude Code <noreply@anthropic.com>` 结尾；前缀 `feat(strings):` / `fix(strings):`。
- 设置 prefs 名：`asmr_settings`（`MainActivity` 与 `SettingsRepositoryImpl` 已一致）；语言键 `app_language`，0=跟随系统、1=中文、2=英文。

## Review Focus

1. **键 parity**：`values` 与 `values-en` 的 string 键集合必须完全一致；漏译（只加中文不加英文）会在 `StringResourcesTest` 爆红。
2. **运行态 override 不破坏系统跟随**：`app_language=0` 时必须返回原 context（不 wrap、不 `Locale.setDefault`），不得强制某语言。
3. **语言切换即时生效**：选择器 set 后必须 `activity.recreate()`；`attachBaseContext` 每次重建读最新 `app_language`，不得缓存旧 locale。
4. **不误迁注释/日志**：仅替换用户可见文案；`Log`/注释/`Constants` 里的非展示串、以及 `description`（通知渠道描述）是否迁移按「用户可见」判断。
5. **格式串安全**：含 `%s`/`%d` 的文案迁到 resources 后仍用 `stringResource(R.string.x, arg)` 传参，不得丢占位符或顺序错乱。

---

### Task 1: AppLocale 工具 + MainActivity 运行态 override

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/AppLocale.kt`
- Modify: `app/src/main/java/com/asmrhelper/MainActivity.kt`
- Test: `app/src/test/java/com/asmrhelper/util/AppLocaleTest.kt`
- Test: `app/src/test/java/com/asmrhelper/util/StringResourcesTest.kt`（键 parity 守卫，随迁移持续护航）

**Interfaces:**
- Produces: `object AppLocale { fun localeFor(language: Int): Locale?; fun wrap(context: Context, language: Int): Context }`。
- Produces: `StringResourcesTest` 读 `src/main/res/values/strings.xml` 与 `src/main/res/values-en/strings.xml`，断言键集合相等。

- [ ] **Step 1: 写失败测试 `AppLocaleTest`**

```kotlin
class AppLocaleTest {
    @Test fun localeForFollowsSystem() { assertNull(AppLocale.localeFor(0)); assertNull(AppLocale.localeFor(99)) }
    @Test fun localeForZhAndEn() {
        assertEquals(Locale("zh"), AppLocale.localeFor(1))
        assertEquals(Locale("en"), AppLocale.localeFor(2))
    }
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.util.AppLocaleTest"`
Expected: FAIL（`Unresolved reference: AppLocale`）。

- [ ] **Step 3: 实现 `AppLocale`**

`localeFor(language)` 按 `1→Locale("zh")、2→Locale("en")、else→null`。`wrap(context, language)`：`localeFor` 为 null 直接返回原 context；否则 `Locale.setDefault(locale)` + `Configuration(context.resources.configuration).apply{ setLocale(locale) }` + `context.createConfigurationContext(config)`。

- [ ] **Step 4: 跑测试验证通过**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: 写键 parity 守卫 `StringResourcesTest`**

用 `java.io.File("src/main/res/values/strings.xml")` / `values-en` 读文本，正则 `name="([^"]+)"` 抽取键集合，`assertEquals(valuesKeys, enKeys)`。

- [ ] **Step 6: 跑 parity 守卫**

Run: `--tests "com.asmrhelper.util.StringResourcesTest"`。Expected: PASS（当前两文件已 parity；若 RED 说明现状缺译，先补齐再继续）。

- [ ] **Step 7: `MainActivity.attachBaseContext` 接 override**

```kotlin
override fun attachBaseContext(newBase: Context) {
    val lang = newBase.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE).getInt("app_language", 0)
    super.attachBaseContext(AppLocale.wrap(newBase, lang))
}
```

- [ ] **Step 8: 编译 + 全量测试**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/asmrhelper/util/AppLocale.kt app/src/main/java/com/asmrhelper/MainActivity.kt app/src/test/java/com/asmrhelper/util/AppLocaleTest.kt app/src/test/java/com/asmrhelper/util/StringResourcesTest.kt
git commit -m "feat(strings): AppLocale 运行态语言 override + 键 parity 守卫

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 设置页语言选择器

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt`（`AppearanceSettingsScreen`）
- Modify: `app/src/main/res/values/strings.xml` / `values-en/strings.xml`（加 `language_*` 键）

**Interfaces:**
- Consumes: `SettingsRepository.getAppLanguage(): Flow<Int>` / `setAppLanguage(mode: Int)`（已存在）。
- Produces: `SettingsViewModel.appLanguage: StateFlow<Int>`、`fun setAppLanguage(mode: Int)`（内部 set 后触发 Activity recreate）。

- [ ] **Step 1: `SettingsViewModel` 暴露 `appLanguage`**

加 `val appLanguage: StateFlow<Int>`（`settingsRepository.getAppLanguage().stateIn(...)`，initial 0）+ `fun setAppLanguage(mode: Int)`（`viewModelScope.launch { settingsRepository.setAppLanguage(mode) }`）。

- [ ] **Step 2: 加 `language_*` 键**

`language_title`=「语言」/「Language」；`language_follow_system`=「跟随系统」/「Follow system」；`language_zh`=「中文」/「中文」；`language_en`=「English」/「English」。

- [ ] **Step 3: `AppearanceSettingsScreen` 加语言选择区**

在主题模式/主题色彩之后加一区，三选项（跟随系统/中文/English）用 `appLanguage` 高亮当前，点击 `viewModel.setAppLanguage(index)` 后 `(LocalContext.current as? Activity)?.recreate()`。

- [ ] **Step 4: 编译 + 全量测试 + parity**

Run: 全量。Expected: BUILD SUCCESSFUL + `StringResourcesTest` 绿。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(strings): 设置页语言选择器（跟随系统/中文/English）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 播放页迁移

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/MusicPlayScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`
- Modify: `values/strings.xml` / `values-en/strings.xml`

**方法（后续每个迁移任务同）**
- [ ] **Step 1: 读文件，枚举用户可见中文**（`Text("…")`/`Toast`/`label = { Text("…") }`/`title = "…"`/`placeholder`/`contentDescription`/`Snackbar`），跳过注释与 Log。
- [ ] **Step 2: 加键** 到两份 strings.xml（`play_*` 前缀，中文 + 英文）。
- [ ] **Step 3: 替换** 字面量为 `stringResource(R.string.<key>)`（需 `LocalContext`/`context.getString` 处用现有注入方式；含 `%s`/`%d` 用 `stringResource(R.string.x, arg)`）。
- [ ] **Step 4: 编译 + 全量测试 + parity**（`StringResourcesTest` 必须绿）。
- [ ] **Step 5: 提交**（`feat(strings): 播放页文案迁移`）。

---

### Task 4: 曲库迁移

**Files:** `ui/library/LibraryScreen.kt`、`FileBrowserScreen.kt`、`DownloadDialog.kt`、`VideoAudioTab.kt`、`FavoritesViewModel.kt`、`AudioFilePickerDialog.kt`；两份 strings.xml。
步骤同 Task 3（`library_*`/`file_*`/`download_*`/`video_*`），提交 `feat(strings): 曲库文案迁移`。

---

### Task 5: 播放列表/睡眠/触发器迁移

**Files:** `ui/playlist/PlaylistScreen.kt`、`PlaylistViewModel.kt`、`ui/sleep/SleepJournalScreen.kt`、`ui/triggerpad/TriggerPadScreen.kt`；两份 strings.xml。
步骤同 Task 3（`playlist_*`/`sleep_*`/`trigger_*`），提交 `feat(strings): 播放列表/睡眠/触发器文案迁移`。

---

### Task 6: 图片/背景迁移

**Files:** `ui/slideshow/ImageSlideshowContent.kt`、`ImageSlideshowViewModel.kt`、`ui/background/BackgroundGalleryScreen.kt`；两份 strings.xml。
步骤同 Task 3（`slideshow_*`/`image_*`/`gallery_*`），提交 `feat(strings): 图片/背景文案迁移`。

---

### Task 7: 个人主页/登录/历史/发现/音源设置迁移

**Files:** `ui/profile/ProfileScreen.kt`、`ProfileViewModel.kt`、`MusicLoginDialog.kt`、`ui/history/HistoryScreen.kt`、`ui/discover/DiscoverScreen.kt`、`DiscoverViewModel.kt`、`ui/settings/MusicSourceSettingsScreen.kt`；两份 strings.xml。
步骤同 Task 3（`profile_*`/`login_*`/`history_*`/`discover_*`/`source_*`），提交 `feat(strings): 个人主页/登录/历史/发现/音源设置文案迁移`。

---

### Task 8: 设置页剩余/导航/组件/通知迁移

**Files:** `ui/settings/SettingsScreen.kt`、`SettingsViewModel.kt`、`ui/navigation/AsmrNavHost.kt`、`Screen.kt`、`ui/components/BatteryOptimizationDialog.kt`、`ModeToggleButton.kt`、`HypnosisBackground.kt`、`MiniPlayer.kt`、`util/Constants.kt`（通知渠道名/描述等用户可见串）、`AsmrApplication.kt`（通知渠道 description）、`MainActivity.kt`（如残留）。
步骤同 Task 3（`settings_*`/`appearance_*`/`nav_*`/`notif_*`/`common_*`），提交 `feat(strings): 设置页/导航/组件/通知文案迁移`。

---

### Task 9: 终验 + push

- [ ] **Step 1: 全量 grep 确认无残留**：`Grep [\x{4e00}-\x{9fff}]` 于 `ui/`、`AsmrApplication.kt`、`MainActivity.kt`、`util/Constants.kt`，人工核对剩余命中均为注释/Log（非用户可见）。
- [ ] **Step 2: 全量编译 + 测试 + parity**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL；`StringResourcesTest` 绿（两文件键完全一致）。

- [ ] **Step 3: push**

```bash
git push origin main
```

---

## 分期说明（超出本计划验证范围，供 reviewer 与用户知悉）

- **真机验证**：locale override 的 `createConfigurationContext` 在 minSdk 26 → 35 的行为、语言切换后 Compose 重组与字符串刷新需真机/模拟器确认（`attachBaseContext` 方案是标准做法，但 ROM 定制可能有差异）。
- **字符串迁移为重构**：无行为变化，故除 `AppLocale.localeFor` 与 `StringResourcesTest` 键 parity 外不新增单测；正确性由「编译 + parity 守卫 + grep 无残留」三重校验兜底。
- **中文注释不迁移**：本计划只动用户可见文案，开发者注释保留中文（与既有代码风格一致）。
- **通知渠道 `description`**：按「用户可见」（系统设置里展示）判定，纳入迁移。
