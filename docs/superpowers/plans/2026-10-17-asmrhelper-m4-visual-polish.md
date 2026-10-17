# ASMRHelper M4 视觉打磨（可视化风格 + 动画）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 MUSIC 播放页的可视化从 2 种风格扩到 6 种，并打磨居中歌词、悬浮控制条的动画细节，延续 M3 的 Folia 概念复刻。

**Architecture:** 纯 Compose 渲染层改造，不动数据层/播放层/网络层。所有新可视化风格复用现有 `MusicVisualizerMath.barsFromFft / barsFromWaveform` 产出的归一化柱高（0..1 的 `FloatArray`），只在 `MusicVisualizer` 的 `Canvas` 里按风格绘制不同几何；动画用 Compose `animate*AsState` + `rememberInfiniteTransition`。

**Tech Stack:** Kotlin + Jetpack Compose（Canvas、`animateFloatAsState`、`rememberInfiniteTransition`、`AnimatedVisibility`）+ Material 3。无新增依赖。

**Spec:** docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md（§6.7 音频可视化「保留 2~3 个内置风格」→ 本次扩到 6 个；§7 M4「更多可视化风格、打磨动画」）。

## Global Constraints

- 技术栈不变，`minSdk = 26`，无新增依赖。
- 所有新增 UI 文案进 `values/strings.xml` + `values-en/strings.xml`，不硬编码中文。
- 可视化仅存在于 MUSIC 模式；ASMR 模式仍走原 `PlayScreen` 可视化，不回归。
- 空数据（无 FFT/波形）时**每种风格都要**降级为怠速基线，不得空白或越界（现有 BAR/WAVE 已画 `drawLine` 基线）。
- 本里程碑是纯 Compose 渲染/动画，无新单元可测纯逻辑；验证标准与 M3 Task 5/6/7 一致：`:app:compileDebugKotlin` + `:app:testDebugUnitTest` 全绿。

## Review Focus

1. 空 `fft`/`waveform` 数据时，DOTS/RADIAL/MIRROR/GLOW 不得除零或画越界图形——每种风格都要和 BAR/WAVE 一样只画怠速基线。
2. `heights.size == 0`（barCount=0 或空输入）时所有新风格不得触发 `forEachIndexed` 空指针或 `size-1` 负下标。
3. 快速连续切换风格时交叉淡入不得闪白/闪黑或复用旧状态。
4. 居中歌词当前行发光动画在 `lines` 为空时不得启动无限动画（省电）。
5. 悬浮控制条入场动画在子页（歌单/曲库）返回时不得重复触发、遮挡交互。

---

### Task 1: 扩展 `VisualizerStyle` 并新增 4 种 Canvas 风格

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt`（`VisualizerStyle` 枚举 + `MusicVisualizer`）
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: `MusicVisualizerMath.barsFromFft(FloatArray?, Int): FloatArray`、`barsFromWaveform(ByteArray?, Int): FloatArray`（已在 M3 产出）。
- Produces: `enum class VisualizerStyle { BAR, WAVE, DOTS, RADIAL, MIRROR, GLOW }`；`MusicVisualizer` 签名不变（`waveform, fft, style, modifier`）。

- [ ] **Step 1: 扩展枚举**

`VisualizerStyle` 从 `{ BAR, WAVE }` 扩为 `{ BAR, WAVE, DOTS, RADIAL, MIRROR, GLOW }`。

- [ ] **Step 2: 新增 4 条字符串资源**

`values/strings.xml` 加：`visualizer_dots`（点阵）、`visualizer_radial`（环形）、`visualizer_mirror`（对称）、`visualizer_glow`（光晕）；`values-en/strings.xml` 对应 `Dots` / `Radial` / `Mirror` / `Glow`。

- [ ] **Step 3: 实现 4 种新风格绘制**

`MusicVisualizer` 的 `Canvas` 里，对同一份 `heights`（`FloatArray`）按风格绘制：
- **DOTS**：沿水平中线等距布 N 个圆点，半径 = `base + h * maxR`，alpha 随 `h` 变化；空闲时只画基线。
- **RADIAL**：N 根径向条，`angle_i = i/N * 2π`，长度 = `inner + h * (outer-inner)`，沿径向画圆角条。
- **MIRROR**：与 BAR 相同，但每根条从中线同时向上下两个方向对称延伸。
- **GLOW**：过各点 `(x_i, mid - h_i * amp)` 的平滑三次曲线，加阴影发光描边 + 底部半透明填充。
- 保留现有 `drawLine` 怠速基线，对每种风格都生效。

- [ ] **Step 4: 编译 + 全量测试验证**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL（既有纯逻辑测试全绿）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(m4): 可视化新增点阵/环形/对称/光晕 4 种风格"
```

---

### Task 2: 风格切换（点按循环 6 种 + 交叉淡入）与「更多」菜单联动

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/MusicPlayScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt`

**Interfaces:**
- Consumes: Task 1 的 `VisualizerStyle`（6 种）。
- Produces: `MusicVisualizer` 新增可选参数 `crossfade: Boolean = false`（切换时对旧风格做 alpha 淡出再重绘）；`MusicPlayScreen` 内点按逻辑改为循环。

- [ ] **Step 1: 点按循环**

`MusicPlayScreen` 里 `visualizerStyle` 的点按从「BAR↔WAVE 二选一」改为顺序循环 6 种：`style = entries[(style.ordinal + 1) % entries.size]`（可视化本身 + 「更多」菜单的可视化风格行都改）。

- [ ] **Step 2: 风格名显示**

「更多」菜单的可视化风格行 `value` 用新增的 4 条 + 既有 2 条字符串资源显示当前风格名（不再写死二选一）。

- [ ] **Step 3: 交叉淡入**

`MusicVisualizer` 增加 `crossfade`：风格切换时用 `animateFloatAsState` 对旧帧做短暂 alpha 过渡，避免 6 风格间硬切。

- [ ] **Step 4: 编译 + 全量测试验证**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/asmrhelper/ui/play/MusicPlayScreen.kt app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt
git commit -m "feat(m4): 可视化 6 风格循环切换 + 交叉淡入"
```

---

### Task 3: 动画打磨（居中歌词当前行发光脉冲 + 悬浮控制条入场）

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt`

**Interfaces:**
- Consumes: 现有 `CenteredLyrics`、`FloatingControls` 签名（不变）。
- Produces: `CenteredLyrics` 当前行发光带轻微脉冲；`FloatingControls` 首次出现时 slide-up + fade-in。

- [ ] **Step 1: 歌词当前行发光脉冲**

`CenteredLyrics` 里当前行（`isCurrent`）的 `Shadow` 用 `rememberInfiniteTransition` 让 `blurRadius`/alpha 在范围内缓慢呼吸；`lines` 为空时不启动动画。

- [ ] **Step 2: 悬浮控制条入场动画**

`FloatingControls` 用 `AnimatedVisibility`（`slideInVertically + fadeIn`）做首次出现的上滑+淡入；已有交互时仍即时响应。

- [ ] **Step 3: 编译 + 全量测试验证**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt
git commit -m "feat(m4): 歌词发光脉冲 + 悬浮控制条入场动画"
```

---

## 验收清单（全部任务完成后）

- [ ] `:app:compileDebugKotlin` 通过
- [ ] `:app:testDebugUnitTest` 全绿
- [ ] `VisualizerStyle` 共 6 种，MUSIC 页点按可循环切换，空数据每种风格都显示怠速基线
- [ ] 新增文案已中英双语言资源化
- [ ] 全部提交已 push 到 `main`
