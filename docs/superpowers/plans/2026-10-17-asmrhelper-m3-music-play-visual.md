# ASMRHelper M3 — MUSIC 播放页视觉（Folia 概念复刻）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 MUSIC 模式播放页从「朴素列表式」`PlayScreenV2` 升级为 Folia 风格：封面双色调 + 流动背景 + 音频可视化 + 居中歌词 + 悬浮控制 + 灵动岛提示 + 模式按钮淡出，同时保留音效器/切片/工具箱/歌单等既有对话框。

**Architecture:** 不改播放引擎与音源层。新增纯逻辑工具类（可 JVM 单测）承载所有可测算法——双色调取色、可视化柱状图归一化、歌词时序定位、远程歌词映射、播放队列编解码；Compose 层只做「取数 + 绘制 + 动画」的薄封装。封面与来源元数据通过 `Audio` 贯穿到播放页，`PlayViewModel` 增接 `MusicSourceManager` 以便远程歌曲拉取在线歌词。

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 + Media3 ExoPlayer + Hilt + Room + OkHttp + Coil + org.json；新增 `androidx.palette:palette-ktx`（封面主色提取）。

**Spec:** `docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md` §6.7（MUSIC 播放页 Folia 布局分解）、§7（M3 里程碑）。

## Global Constraints

- 技术栈不变：Kotlin + Jetpack Compose + Material 3 + Media3 ExoPlayer + Hilt + Room + OkHttp。
- `minSdk = 26`。
- UI/文案/字符串中不出现任何竞品名，仅用「歌词模式」「可视化」等中性词。
- 新增用户可见字符串进 `values/strings.xml` + `values-en/strings.xml`。
- 概念复刻，不逐字移植 GPU shader：用 Compose 动画 + 取色渐变 + ExoPlayer 音频数据。
- 不嵌入 JS 引擎、不改音源层协议。
- 测试命令（Windows Git Bash）：
  `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "<FQCN>"`
  全量：`export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest`

## Review Focus

下列是 spec 隐含但普通 happy-path 测试不会覆盖的输入/失败模式；每条都落到所属任务的测试里。

1. **远程歌曲 `coverUrl` 为 null** → 双色调回退到强调色，不崩、不白屏（Task 2 纯逻辑测试 + Task 6 回退分支）。
2. **远程歌曲无歌词 / `getLyric` 返回空** → 居中歌词显示占位「暂无歌词」，不崩溃（Task 4 映射测试 + Task 5 分支）。
3. **远程歌曲 `id` 为 0（id 非纯数字）** → 不触发无意义的在线歌词请求（Task 4 `isRemoteSong` 测试）。
4. **歌词时间戳乱序/重复** → `currentLineIndex`/`toLrcLines` 仍给出稳定结果（Task 4 排序 + 定位测试）。
5. **Visualizer API 不可用（waveform/fft 为 null）** → 柱状图渲染空闲态，不除零（Task 3 空输入测试）。
6. **模式按钮淡出后** → 触摸仍可恢复、可切换，不会锁死（Task 7 触摸恢复逻辑）。

---

### Task 1: 封面/来源元数据贯穿播放链路

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/domain/model/Audio.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverViewModel.kt`（`RemoteSong.toAudio`）
- Create: `app/src/main/java/com/asmrhelper/util/PlaylistStateCodec.kt`
- Modify: `app/src/main/java/com/asmrhelper/player/PlayerManager.kt`（持久化改用 codec）
- Test: `app/src/test/java/com/asmrhelper/util/PlaylistStateCodecTest.kt`
- Test: `app/src/test/java/com/asmrhelper/ui/discover/RemoteSongMapperTest.kt`（扩展）

**Interfaces:**
- Produces: `Audio.coverUrl: String?`、`Audio.sourceId: String`（供 Task 5/6 读取）；`PlaylistStateCodec.encode(List<Audio>): String` / `decode(String): List<Audio>`（供 PlayerManager 使用）。

- [ ] **Step 1: 写失败测试 `PlaylistStateCodecTest`**

```kotlin
package com.asmrhelper.util

import com.asmrhelper.domain.model.Audio
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistStateCodecTest {
    @Test
    fun roundTripsCoverAndSource() {
        val audio = Audio(id = 123, title = "晴天", artist = "周杰伦",
            filePath = "https://x/1.mp3", durationMs = 269000L,
            coverUrl = "https://c/1.jpg", sourceId = "netease")
        val decoded = PlaylistStateCodec.decode(PlaylistStateCodec.encode(listOf(audio))).single()
        assertEquals(audio.id, decoded.id)
        assertEquals(audio.title, decoded.title)
        assertEquals(audio.artist, decoded.artist)
        assertEquals(audio.filePath, decoded.filePath)
        assertEquals(audio.durationMs, decoded.durationMs)
        assertEquals(audio.isFavorite, decoded.isFavorite)
        assertEquals(audio.coverUrl, decoded.coverUrl)
        assertEquals(audio.sourceId, decoded.sourceId)
    }

    @Test
    fun decodeEmptyAndMalformedReturnEmpty() {
        assertEquals(emptyList<Audio>(), PlaylistStateCodec.decode(""))
        assertEquals(emptyList<Audio>(), PlaylistStateCodec.decode("not json"))
        assertEquals("[]", PlaylistStateCodec.encode(emptyList()))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `... :app:testDebugUnitTest --tests "com.asmrhelper.util.PlaylistStateCodecTest"`
Expected: 编译失败（`PlaylistStateCodec` 未定义）。

- [ ] **Step 3: 实现 `PlaylistStateCodec` + 扩展 `Audio` 字段**

- `Audio.kt`：在 `addedAt` 之后追加两个带默认值字段（置于末尾避免破坏任何位置参数调用）：
  `val coverUrl: String? = null, val sourceId: String = ""`
- 新建 `util/PlaylistStateCodec.kt`：

```kotlin
package com.asmrhelper.util

import com.asmrhelper.domain.model.Audio
import org.json.JSONArray
import org.json.JSONObject

/** 播放队列的 JSON 编解码（含封面/来源元数据），供 PlayerManager 持久化与恢复。 */
object PlaylistStateCodec {
    fun encode(audios: List<Audio>): String {
        val arr = JSONArray()
        audios.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("title", a.title); put("artist", a.artist)
                put("filePath", a.filePath); put("durationMs", a.durationMs)
                put("isFavorite", a.isFavorite)
                put("coverUrl", a.coverUrl ?: "")
                put("sourceId", a.sourceId)
            })
        }
        return arr.toString()
    }

    fun decode(json: String): List<Audio> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Audio(
                id = o.optLong("id", 0L),
                title = o.optString("title", "未知"),
                artist = o.optString("artist", ""),
                filePath = o.optString("filePath", ""),
                durationMs = o.optLong("durationMs", 0L),
                isFavorite = o.optBoolean("isFavorite", false),
                coverUrl = o.optString("coverUrl", "").takeIf { it.isNotBlank() },
                sourceId = o.optString("sourceId", "")
            )
        }
    } catch (_: Exception) { emptyList() }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `... :app:testDebugUnitTest --tests "com.asmrhelper.util.PlaylistStateCodecTest"`
Expected: PASS（2/2）。

- [ ] **Step 5: 扩展 `RemoteSongMapperTest` 并失败**

在 `RemoteSongMapperTest.kt` 追加：

```kotlin
@Test
fun mapsCoverAndSourceId() {
    val audio = RemoteSong("42", "歌", "人", coverUrl = "https://c/1.jpg", sourceId = "netease")
        .toAudio("https://s/1.mp3")
    assertEquals("https://c/1.jpg", audio.coverUrl)
    assertEquals("netease", audio.sourceId)
    assertEquals(42L, audio.id)
}
```

Run：`... --tests "com.asmrhelper.ui.discover.RemoteSongMapperTest"`
Expected：`mapsCoverAndSourceId` FAIL（`coverUrl`/`sourceId` 未映射）。

- [ ] **Step 6: 更新 `RemoteSong.toAudio`**

在 `DiscoverViewModel.kt` 底部的 `toAudio` 增加 `coverUrl = coverUrl, sourceId = sourceId`。

- [ ] **Step 7: 运行确认通过**

Run：`... --tests "com.asmrhelper.ui.discover.RemoteSongMapperTest"`
Expected：PASS。

- [ ] **Step 8: 让 `PlayerManager` 使用 `PlaylistStateCodec`**

把 `savePlaylistState()` / `loadPlaylistState()` 里内联的 `org.json` 序列化替换为 `PlaylistStateCodec.encode/decode`（`saveLastPlayback`/`loadLastPlayback` 需补存/读 `coverUrl` 与 `sourceId`）。删除内联 JSON 代码，保持行为一致。

- [ ] **Step 9: 全量单测 + 提交**

Run：`... :app:testDebugUnitTest`（全量）
Expected：全绿（含既有 `RemoteSongMapperTest`、`NeteaseJsonTest` 等）。

```bash
git add -A
git commit -m "feat(m3): 封面/来源元数据贯穿 Audio + 播放队列 codec

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 双色调取色纯逻辑 + palette 依赖

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/Duotone.kt`
- Test: `app/src/test/java/com/asmrhelper/util/DuotoneTest.kt`
- Modify: `gradle/libs.versions.toml`、`app/build.gradle.kts`（palette）

**Interfaces:**
- Produces: `Duotone.DuotoneColors(primary: Long, secondary: Long)`、`Duotone.duotone(seed: Long)`、`Duotone.darken(argb: Long, percent: Int)`、`Duotone.swapRedBlue(argb: Long)`（供 Task 6 使用）。

- [ ] **Step 1: 写失败测试 `DuotoneTest`**

```kotlin
package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DuotoneTest {
    @Test fun darkenScalesEachChannelByPercent() {
        assertEquals(0xFF1C3854L, Duotone.darken(0xFF336699L, 55))
    }
    @Test fun swapRedBlueExchangesChannels() {
        assertEquals(0xFF996633L, Duotone.swapRedBlue(0xFF336699L))
    }
    @Test fun duotoneReturnsDarkPrimaryAndSwappedSecondary() {
        val c = Duotone.duotone(0xFF336699L)
        assertEquals(0xFF1C3854L, c.primary)
        assertEquals(0xFF996633L, c.secondary)
    }
}
```

- [ ] **Step 2: 运行确认失败**（`Duotone` 未定义）→ Run `... --tests "com.asmrhelper.util.DuotoneTest"`，Expected FAIL 编译。

- [ ] **Step 3: 实现 `Duotone.kt`**

```kotlin
package com.asmrhelper.util

/** 封面双色调：由主色派生出「深色主调 + 红蓝交换的次调」，形成双色渐变。 */
object Duotone {
    data class DuotoneColors(val primary: Long, val secondary: Long)

    fun duotone(seed: Long): DuotoneColors =
        DuotoneColors(darken(seed, 55), swapRedBlue(seed))

    fun darken(argb: Long, percent: Int): Long {
        val a = (argb shr 24) and 0xFF
        val r = ((argb shr 16) and 0xFF) * percent / 100
        val g = ((argb shr 8) and 0xFF) * percent / 100
        val b = (argb and 0xFF) * percent / 100
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun swapRedBlue(argb: Long): Long {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (a.toLong() shl 24) or (b.toLong() shl 16) or (g.toLong() shl 8) or r.toLong()
    }
}
```

- [ ] **Step 4: 运行确认通过** → Run `... --tests "com.asmrhelper.util.DuotoneTest"`，Expected PASS（3/3）。

- [ ] **Step 5: 加 palette 依赖**

`gradle/libs.versions.toml`：`[versions]` 加 `palette = "1.0.0"`；`[libraries]` 加
`palette-ktx = { group = "androidx.palette", name = "palette-ktx", version.ref = "palette" }`。
`app/build.gradle.kts` 依赖块加 `implementation(libs.palette.ktx)`。

- [ ] **Step 6: 全量单测 + 提交**

Run：`... :app:testDebugUnitTest`，Expected 全绿。
`git commit -m "feat(m3): 双色调取色纯逻辑 + palette 依赖"`（带 Co-Authored-By 尾行）。

---

### Task 3: 可视化柱状图纯逻辑

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/MusicVisualizerMath.kt`
- Test: `app/src/test/java/com/asmrhelper/util/MusicVisualizerMathTest.kt`

**Interfaces:**
- Produces: `MusicVisualizerMath.barsFromWaveform(bytes: ByteArray?, barCount: Int): FloatArray`、`barsFromFft(magnitudes: FloatArray?, barCount: Int): FloatArray`（供 Task 6 使用）。

- [ ] **Step 1: 写失败测试 `MusicVisualizerMathTest`**

```kotlin
package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicVisualizerMathTest {
    @Test fun nullWaveformReturnsZeros() {
        val bars = MusicVisualizerMath.barsFromWaveform(null, 4)
        assertEquals(4, bars.size)
        assertTrue(bars.all { it == 0f })
    }
    @Test fun bucketsAndAveragesWaveform() {
        val bars = MusicVisualizerMath.barsFromWaveform(byteArrayOf(128, 128, 0, 0), 2)
        assertEquals(2, bars.size)
        assertEquals(0f, bars[0], 1e-6f)
        assertEquals(1f, bars[1], 1e-6f)
    }
    @Test fun zeroBarsReturnsEmpty() {
        assertEquals(0, MusicVisualizerMath.barsFromWaveform(byteArrayOf(1, 2, 3), 0).size)
    }
    @Test fun bucketsAndAveragesFft() {
        val bars = MusicVisualizerMath.barsFromFft(floatArrayOf(0f, 1f, 0f, 1f), 2)
        assertEquals(0.5f, bars[0], 1e-6f)
        assertEquals(0.5f, bars[1], 1e-6f)
    }
}
```

- [ ] **Step 2: 运行确认失败**（`MusicVisualizerMath` 未定义）。

- [ ] **Step 3: 实现 `MusicVisualizerMath.kt`**

```kotlin
package com.asmrhelper.util

import kotlin.math.abs

/** 音频可视化柱状图归一化：把原始波形/FFT 数据降采样为 0..1 的柱高。 */
object MusicVisualizerMath {
    fun barsFromWaveform(bytes: ByteArray?, barCount: Int): FloatArray {
        val bars = FloatArray(barCount.coerceAtLeast(0))
        if (bytes == null || bytes.isEmpty() || barCount <= 0) return bars
        val perBar = bytes.size / barCount
        for (i in 0 until barCount) {
            val start = i * perBar
            val end = if (i == barCount - 1) bytes.size else start + perBar
            var sum = 0f
            for (j in start until end) {
                sum += abs((bytes[j].toInt() and 0xFF) - 128) / 128f
            }
            bars[i] = if (end > start) sum / (end - start) else 0f
        }
        return bars
    }

    fun barsFromFft(magnitudes: FloatArray?, barCount: Int): FloatArray {
        val bars = FloatArray(barCount.coerceAtLeast(0))
        if (magnitudes == null || magnitudes.isEmpty() || barCount <= 0) return bars
        val perBar = magnitudes.size / barCount
        for (i in 0 until barCount) {
            val start = i * perBar
            val end = if (i == barCount - 1) magnitudes.size else start + perBar
            var sum = 0f
            for (j in start until end) sum += magnitudes[j].coerceIn(0f, 1f)
            bars[i] = if (end > start) sum / (end - start) else 0f
        }
        return bars
    }
}
```

- [ ] **Step 4: 运行确认通过** → PASS（4/4）。

- [ ] **Step 5: 全量单测 + 提交**

`git commit -m "feat(m3): 可视化柱状图归一化纯逻辑"`（带 Co-Authored-By 尾行）。

---

### Task 4: 歌词时序纯逻辑 + 远程歌词映射

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/LyricTiming.kt`
- Create: `app/src/main/java/com/asmrhelper/data/music/RemoteLyricMapper.kt`
- Test: `app/src/test/java/com/asmrhelper/util/LyricTimingTest.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/RemoteLyricMapperTest.kt`

**Interfaces:**
- Produces: `enum LyricMode { JUMP, SCROLL }`；`List<LrcLine>.currentLineIndex(progressMs: Long): Int`；`scrollAnchorIndex(currentIndex: Int, mode: LyricMode, visibleCount: Int): Int`；`RemoteLyric.toLrcLines(): List<LrcLine>`；`Audio.isRemoteSong(): Boolean`（供 Task 5/6 使用）。

- [ ] **Step 1: 写失败测试 `LyricTimingTest`**

```kotlin
package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimingTest {
    private val lines = listOf(LrcLine(1000, "a"), LrcLine(2000, "b"), LrcLine(3000, "c"))

    @Test fun currentLineIndexFindsLastLineAtOrBeforeProgress() {
        assertEquals(1, lines.currentLineIndex(2500))
        assertEquals(2, lines.currentLineIndex(9999))
        assertEquals(0, lines.currentLineIndex(500))
    }
    @Test fun jumpSnapsToCurrent() {
        assertEquals(5, scrollAnchorIndex(5, LyricMode.JUMP, 10))
    }
    @Test fun scrollCentersCurrentAndClamps() {
        assertEquals(3, scrollAnchorIndex(5, LyricMode.SCROLL, 4))
        assertEquals(0, scrollAnchorIndex(1, LyricMode.SCROLL, 10))
    }
}
```

- [ ] **Step 2: 写失败测试 `RemoteLyricMapperTest`**

```kotlin
package com.asmrhelper.data.music

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
import com.asmrhelper.util.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLyricMapperTest {
    @Test fun mapsAndSortsLines() {
        val remote = RemoteLyric(
            lines = listOf(RemoteLyricLine(2000, "b"), RemoteLyricLine(1000, "a")),
            rawText = "[00:02.00] b\n[00:01.00] a"
        )
        val lines = remote.toLrcLines()
        assertEquals(listOf(LrcLine(1000, "a"), LrcLine(2000, "b")), lines)
    }
    @Test fun emptyRemoteYieldsEmpty() {
        assertTrue(RemoteLyric().toLrcLines().isEmpty())
    }
    @Test fun isRemoteSongRequiresSourceAndPositiveId() {
        assertTrue(Audio(id = 1, title = "x", filePath = "u", sourceId = "netease").isRemoteSong())
        assertFalse(Audio(id = 0, title = "x", filePath = "u", sourceId = "netease").isRemoteSong())
        assertFalse(Audio(id = 1, title = "x", filePath = "u", sourceId = "").isRemoteSong())
    }
}
```

- [ ] **Step 3: 运行确认两处失败**（`LyricTiming`/`RemoteLyricMapper` 未定义）。

- [ ] **Step 4: 实现 `LyricTiming.kt` 与 `RemoteLyricMapper.kt`**

```kotlin
// util/LyricTiming.kt
package com.asmrhelper.util

enum class LyricMode { JUMP, SCROLL }

/** 当前进度对应的歌词行：最后一个 timeMs <= progressMs 的下标，无匹配取 0。 */
fun List<LrcLine>.currentLineIndex(progressMs: Long): Int =
    indexOfLast { it.timeMs <= progressMs }.coerceAtLeast(0)

/** 歌词滚动锚点：JUMP 直接跳当前行；SCROLL 让当前行尽量居中（上移 visibleCount/2 行）。 */
fun scrollAnchorIndex(currentIndex: Int, mode: LyricMode, visibleCount: Int): Int =
    when (mode) {
        LyricMode.JUMP -> currentIndex
        LyricMode.SCROLL -> (currentIndex - visibleCount / 2).coerceAtLeast(0)
    }
```

```kotlin
// data/music/RemoteLyricMapper.kt
package com.asmrhelper.data.music

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.util.LrcLine

/** 在线歌词 → 显示用 LrcLine（按时间排序，与本地 LRC 路径对齐）。 */
fun RemoteLyric.toLrcLines(): List<LrcLine> =
    lines.map { LrcLine(it.timeMs, it.text) }.sortedBy { it.timeMs }

/** 是否为可拉取在线歌词的远程歌曲：有来源且 id 为正数。 */
fun Audio.isRemoteSong(): Boolean = sourceId.isNotBlank() && id > 0L
```

- [ ] **Step 5: 运行确认两处通过**（LyricTimingTest 3/3，RemoteLyricMapperTest 3/3）。

- [ ] **Step 6: 全量单测 + 提交**

`git commit -m "feat(m3): 歌词时序定位 + 远程歌词映射纯逻辑"`（带 Co-Authored-By 尾行）。

---

### Task 5: PlayViewModel 接入远程歌词 + 音源

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt`

**Interfaces:**
- Consumes: `Audio.isRemoteSong()`、`RemoteLyric.toLrcLines()`（Task 4）；`MusicSourceManager.activeSource`。
- Produces: `PlayViewModel` 保持既有接口不变；新增依赖 `MusicSourceManager`（构造注入），`loadLyricsFor` 分支加载远程歌词。

- [ ] **Step 1: 注入 `MusicSourceManager`**

在 `PlayViewModel` 构造加入 `private val musicSourceManager: MusicSourceManager`（`@HiltViewModel` 自动解析，`MusicSourceManager` 为 `@Singleton`）。

- [ ] **Step 2: 改写 `loadLyricsFor` 分支**

```kotlin
private fun loadLyricsFor(audio: Audio?) {
    viewModelScope.launch {
        if (audio == null) {
            _lyrics.value = emptyList(); _hasLyrics.value = false; _rawLyricsText.value = ""
            return@launch
        }
        if (audio.isRemoteSong()) {
            musicSourceManager.activeSource.value.getLyric(audio.id.toString()).collect { remote ->
                val lines = remote.toLrcLines()
                _lyrics.value = lines
                _hasLyrics.value = lines.isNotEmpty()
                _rawLyricsText.value = remote.rawText
            }
        } else {
            val text = withContext(Dispatchers.IO) { LyricsStore.loadLyrics(context, audio.filePath) }
            val lines = if (text.isNullOrBlank()) emptyList() else LrcParser.parse(text)
            _lyrics.value = lines
            _hasLyrics.value = lines.isNotEmpty()
            _rawLyricsText.value = text ?: ""
        }
    }
}
```

（纯决策 `isRemoteSong` 与映射 `toLrcLines` 已在 Task 4 覆盖，此处仅编排。）

- [ ] **Step 3: 编译验证**

Run：`export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin`
Expected：BUILD SUCCESSFUL。

- [ ] **Step 4: 全量单测 + 提交**

`git commit -m "feat(m3): MUSIC 远程歌曲在线歌词接入"`（带 Co-Authored-By 尾行）。

---

### Task 6: Folia 视觉组件（取色 + 背景/可视化/歌词/悬浮控制）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/play/folia/CoverPalette.kt`
- Create: `app/src/main/java/com/asmrhelper/ui/play/folia/MusicPlayVisuals.kt`

**Interfaces:**
- Consumes: `Duotone.duotone`（Task 2）、`MusicVisualizerMath.barsFromWaveform/barsFromFft`（Task 3）、`List<LrcLine>.currentLineIndex` + `scrollAnchorIndex` + `LyricMode`（Task 4）、`Audio.coverUrl`（Task 1）、`LocalAccentColor`/`DynamicIsland` 等主题组件。
- Produces（Compose，供 Task 7 组装）：
  - `@Composable fun rememberCoverDominantColor(coverUrl: String?, fallback: Long): Long`
  - `@Composable fun CoverDuotoneBackground(coverUrl: String?, modifier: Modifier = Modifier)`
  - `@Composable fun MusicVisualizer(waveform: ByteArray?, fft: FloatArray?, style: VisualizerStyle, modifier: Modifier = Modifier)`
  - `@Composable fun CenteredLyrics(lines: List<LrcLine>, progressMs: Long, mode: LyricMode, onToggleMode: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun FloatingControls(isPlaying: Boolean, progress: Float, durationMs: Long, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier)`

> 说明：本任务为 Compose/Android 绘制与动画，无 JVM 单测；纯算法已在 Task 2/3/4 覆盖。以「编译 + 真机视觉验收」为准。

- [ ] **Step 1: `CoverPalette.kt` — 封面主色提取（回退强调色）**

`rememberCoverDominantColor`：`coverUrl` 空 → 返回 `fallback`（`LocalAccentColor.current` 的 ARGB）；否则 `produceState` 内用 Coil `ImageLoader` 解码 `Bitmap`，`Palette.from(bitmap).generate()` 取 dominant swatch RGB（无 swatch 则回退 `fallback`）。异常一律回退 `fallback`（Review Focus #1）。

- [ ] **Step 2: `MusicPlayVisuals.kt` — 四个组件**

- `CoverDuotoneBackground`：全屏 Box，底层 `AsyncImage`（coverUrl，`ContentScale.Crop` + `Modifier.blur(40.dp)` + `rememberInfiniteTransition` 缓慢缩放/平移），上层 `Brush.radialGradient`/`linearGradient` 用 `Duotone.duotone(seed)` 的两色叠加半透明遮罩；`coverUrl` 为空时仅用强调色渐变。
- `MusicVisualizer`：`style` 枚举 `VisualizerStyle { BAR, WAVE }`；用 `MusicVisualizerMath` 把 `waveform`/`fft` 归一化为柱高，`Canvas` 绘制柱状频谱（BAR）或镜像波线（WAVE）；空数据画一条水平怠速线（Review Focus #5）。
- `CenteredLyrics`：`LazyColumn` 居中，`currentLineIndex` 高亮当前行（更大字号 + 强调色 + `shadow`/`blur` 发光），`LaunchedEffect` 按 `scrollAnchorIndex(..., mode, 可见行数)` `animateScrollToItem`；底部一行小按钮在 `JUMP`/`SCROLL` 间切换（`onToggleMode`）；空歌词显示「暂无歌词」占位（Review Focus #2）。
- `FloatingControls`：底部悬浮半透明胶囊条（圆角 + `DarkSurfaceVariant` 半透明 + 模糊），内含上一曲 / 播放暂停 / 下一曲 + 进度 Slider + 「更多」按钮（`onMore` 打开音效/切片/工具箱/歌单菜单）。

- [ ] **Step 3: 编译验证**

Run：`... :app:compileDebugKotlin`，Expected BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

`git commit -m "feat(m3): Folia 视觉组件（双色调背景/可视化/居中歌词/悬浮控制）"`（带 Co-Authored-By 尾行）。

---

### Task 7: MusicPlayScreen 组装 + 导航接线 + 模式按钮淡出

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/play/MusicPlayScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`
- Delete: `app/src/main/java/com/asmrhelper/ui/play/PlayScreenV2.kt`
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-en/strings.xml`（新增 2~3 条）

**Interfaces:**
- Consumes: Task 6 的全部组件；`PlayViewModel`（沿用既有 `uiState`/`lyrics`/`hasLyrics`/`waveformBytes`/`fftMagnitudes`/控制方法）；`ModeToggleButton`；`DynamicIsland`。
- Produces: `@Composable fun MusicPlayScreen(onNavigateToPlaylist/..., onToggleMode: () -> Unit, ...)`，替换 `AsmrNavHost` 中 MUSIC 分支的 `PlayScreenV2`。

- [ ] **Step 1: 新建 `MusicPlayScreen.kt`**

以 `hiltViewModel()` 取 `PlayViewModel`，收集 `uiState`、`lyrics`、`hasLyrics`、`waveformBytes`、`fftMagnitudes`。封面取 `state.playerState.currentAudio?.coverUrl`。布局（Folia）：

1. 背景：`CoverDuotoneBackground(coverUrl)`。
2. 顶部：`DynamicIsland`（居中）+ 左上角**淡出式** `ModeToggleButton`（见 Step 3）。
3. 中部：`MusicVisualizer`（BAR/WAVE 切换）→ 歌名/艺术家 → `CenteredLyrics`。
4. 底部：`FloatingControls`，`onMore` 打开复用菜单（音效器/切片/工具箱/歌单）。

**保留既有对话框**：把 `PlayScreenV2.kt` 中的音效器、切片、工具箱、歌单、定时、歌词编辑对话框代码**原样迁入**本文件（这些对话框依赖的 `PlayViewModel` 方法均已在场，无需改动）。新增文案用 `stringResource`。

- [ ] **Step 2: 接线 `AsmrNavHost.kt`**

- MUSIC 分支：`PlayScreenV2(...)` → `MusicPlayScreen(...)`（`onToggleMode` 传 `playViewModel.setPlaybackMode(next)`）。
- 移除 `PlayPage` 中叠加在整页上的 `ModeToggleButton` 对 MUSIC 的覆盖（ASMR 分支的 `PlayScreen` 保持不变，仍由 `PlayPage` 提供常显按钮）；即 `ModeToggleButton` 仅 ASMR 时叠加，MUSIC 由 `MusicPlayScreen` 自绘淡出版。
- 删除 `import ...PlayScreenV2`，删除 `PlayScreenV2.kt` 文件。

- [ ] **Step 3: 模式按钮 2 秒淡出（Review Focus #6）**

在 `MusicPlayScreen` 内：`var controlsVisible by remember { mutableStateOf(true) }`；`LaunchedEffect(交互 tick)` 每次触摸/滑动/点击时 `controlsVisible = true` 并重置 2 秒计时，超时后 `controlsVisible = false`；`ModeToggleButton` 用 `animateFloatAsState` 把 alpha 拉到 0（可见性为 `controlsVisible`），触摸屏幕任意处（根 Box 的 `pointerInput`）恢复。确保 `ModeToggleButton` 淡出后仍可通过「点一下屏幕再点按钮」完成切换，不锁死。

- [ ] **Step 4: 新增字符串**

`strings.xml` + `values-en` 各加：歌词模式「跳转 / 滚动」、可视化风格「柱状 / 波形」、更多「更多」。

- [ ] **Step 5: 编译验证**

Run：`... :app:compileDebugKotlin`，Expected BUILD SUCCESSFUL（确认 `PlayScreenV2` 已删、无引用残留）。

- [ ] **Step 6: 全量单测 + 提交**

Run：`... :app:testDebugUnitTest`，Expected 全绿。

```bash
git add -A
git commit -m "feat(m3): MUSIC 播放页 Folia 视觉落地 + 模式按钮淡出

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Self-Review 记录

- **Spec 覆盖**：§6.7 七项——封面双色调（Task 2/6）、流动背景（Task 6）、音频可视化（Task 3/6）、居中歌词 + 跳转/滚动 + 发光（Task 4/6）、悬浮控制（Task 6/7）、灵动岛提示（Task 7 复用 `DynamicIsland`）、模式按钮淡出（Task 7）。音效器/切片双模式可用（§6 item 3）通过 Task 7 保留对话框满足。
- **步骤唯一性**：所有纯函数签名 + 精确断言值已给出；Compose 组件给出签名与行为，实现者有唯一合理写法。
- **类型一致性**：`LrcLine(timeMs, text)` 贯穿 Task 4/5/6；`LyricMode`/`scrollAnchorIndex` 命名前后一致；`Audio.coverUrl/sourceId` 自 Task 1 定义后 Task 5/6 一致引用。
- **Review Focus**：6 条均落到所属任务的测试（Task 2/3/4 的纯逻辑测试 + Task 5/6/7 的分支/回退/恢复）。
- **比例**：计划以「签名 + 精确断言」为主，未转录完整 UI 代码，长度与 spec 成比例。
