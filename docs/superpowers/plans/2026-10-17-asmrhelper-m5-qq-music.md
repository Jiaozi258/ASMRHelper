# QQ 音乐二期（原生匿名源） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 QQ 音乐接入为可用的原生音源（匿名）：搜索、播放地址、歌词、封面，可在音源设置里切换到 QQ，并在 MUSIC 发现页搜歌/播歌/显示歌词。

**Architecture:** 镜像已交付的 `NeteaseMusicSource` 原生音源模式。新增 `QqMusicSign`（zzc 签名，SHA1+索引切片+XOR 扰码+base64）与 `QqMusicJson`（org.json 纯解析）两个纯逻辑对象（可单测），`QqMusicSource` 作为薄网络适配器直接 POST `https://u.y.qq.com/cgi-bin/musics.fcg`（匿名 `uin=0`）。集成到 `MusicSourceManager.sourceFor`（QQ 分支）并给 `Audio` 增加字符串平台 id 字段以支持 QQ 的非数字 songmid 歌词链路。

**Tech Stack:** Kotlin + OkHttp（复用注入的 `OkHttpClient`）+ `org.json` + JUnit4（现有测试栈）。无新依赖。

**Spec:** docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md

## Global Constraints

- minSdk 26，compileSdk 35（沿用现状，不升级）。
- 测试仅用 `junit:junit:4.13.2` + `org.json:json:20240303`（无 Robolectric、无 Compose UI test、无网络单测）。
- 解析器一律「异常返回空/空串，不抛」，与 `NeteaseJson` 一致。
- 测试/编译命令（bash）：
  `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
- 提交信息以 `Co-Authored-By: Claude Code <noreply@anthropic.com>` 结尾；前缀沿用仓库惯例 `feat(m5):` / `fix(m5):`。

## Review Focus

以下输入类/失败模式是 spec 隐含但任务测试未覆盖的，最容易咬到真实用户（按可能性排序）：

1. **VIP 歌 vkey**：`result=104003` 且 `purl=""`（QQ 现在把绝大多数热门歌绿钻化）。`getSongUrl` 必须返回空串走「无法获取播放地址」提示，绝不能返回 `sip[0] + ""` 或崩溃。→ 归 Task 2 的 `parseSongUrl` 测试（`result!=0` 断言空串）。
2. **非数字 songmid**：QQ 的 `mid` 是 `"0035GveV3i9dBM"` 这类字符串，`toLongOrNull()` 得 0，若不补 `sourceSongId` 会丢 id → 歌词永远拉不到。→ 归 Task 5 的 `toAudio` 测试（字符串 mid 映射到 `sourceSongId` 且 `isRemoteSong()` 为 true）。
3. **歌词 base64 解码失败**：`data.lyric` 是 base64 的 LRC，解码异常/空内容不得抛。→ 归 Task 2 的 `parseLyric` 测试（malformed 返回空歌词）。
4. **搜索空结果**：响应缺 `item_song`/`items` 键时返回空列表，不 NPE。→ 归 Task 2 的 `parseSearch` 测试。
5. **zzc 对空串/非 ASCII 载荷**：确定性、无异常（签名作用于任意 JSON body）。→ 归 Task 1 的 `sign("")` 与 `sign("""{"a":1}""")` 向量。

---

### Task 1: QqMusicSign（zzc 签名）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSign.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/qq/QqMusicSignTest.kt`

**Interfaces:**
- Produces: `object QqMusicSign { fun sign(payload: String): String }` —— 供 Task 3 `QqMusicSource` 对请求体签名。

- [ ] **Step 1: Write the failing test**

`QqMusicSignTest.kt`（junit4 + Assert.assertEquals）：

```kotlin
package com.asmrhelper.data.music.qq

import org.junit.Assert.assertEquals
import org.junit.Test

class QqMusicSignTest {
    @Test fun signHelloVector() {
        assertEquals("zzcfa14dde89n1iwax0l5rimr0qwjexceiov4daaee8d6", QqMusicSign.sign("hello"))
    }
    @Test fun signJsonVector() {
        assertEquals("zzc7746109xq501htmv4ipz7c8owlxfpihjm1fd695c7", QqMusicSign.sign("""{"a":1}"""))
    }
    @Test fun signEmptyVector() {
        assertEquals("zzcf0e03e5gx4qeiq5cfgdyqwu7sdqfsb5fro3aa45053", QqMusicSign.sign(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.data.music.qq.QqMusicSignTest"`
Expected: FAIL（`Unresolved reference: QqMusicSign`）。

- [ ] **Step 3: Implement `sign(payload: String): String` in `QqMusicSign.kt`**

算法（已逆向验证，照抄）：
1. `s = SHA-1(payload 的 UTF-8 字节)` 的 40 位十六进制串，**大写**（`MessageDigest.getInstance("SHA-1")`，字节转 `%02X`）。
2. `p1` = 依次取 `s` 下标 `[23,14,6,36,16,7,19]` 的字符（7 个）。
3. `p2` = 依次取 `s` 下标 `[16,1,32,12,19,27,8,5]` 的字符（8 个）。
4. `p3` = 20 字节：对 `i in 0..19`，取 `s` 的 `[i*2, i*2+2]` 两位十六进制 `toInt(16)`，与 `SCRAMBLE[i]` 异或后 `toByte()`。
5. `b` = `Base64.getEncoder().encodeToString(p3)` 去掉 `/`、`+`、`=`。
6. 返回 `"zzc" + p1 + b + p2`，整体 `.lowercase()`。

`SCRAMBLE = intArrayOf(89,39,179,150,218,82,58,252,177,52,186,123,120,64,242,133,143,161,121,179)`。

- [ ] **Step 4: Run test to verify it passes**

Run: 同 Step 2。
Expected: PASS（3/3）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSign.kt app/src/test/java/com/asmrhelper/data/music/qq/QqMusicSignTest.kt
git commit -m "feat(m5): QQ 音乐 zzc 签名对象

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: QqMusicJson 解析器

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicJson.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/qq/QqMusicJsonTest.kt`

**Interfaces:**
- Consumes: `RemoteSong`/`RemoteLyric`/`RemoteLyricLine`（`com.asmrhelper.domain.model.music`，已存在）。
- Produces: `object QqMusicJson { fun parseSearch(json: String): List<RemoteSong>; fun parseSongUrl(json: String): String; fun parseLyric(json: String): RemoteLyric }` —— 供 Task 3 `QqMusicSource` 调用。全部「异常返回空/空串」。

- [ ] **Step 1: Write the failing test**

`QqMusicJsonTest.kt`，fixture 用真实响应形状（响应根为 `{code, req_0:{code, data}}`）：

```kotlin
package com.asmrhelper.data.music.qq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqMusicJsonTest {

    @Test fun parseSearchMapsSong() {
        val json = """
            {"code":0,"req_0":{"code":0,"data":{"body":{"item_song":{"items":[
              {"mid":"0035GveV3i9dBM","title":"小苹果","name":"小苹果",
               "singer":[{"name":"筷子兄弟"}],
               "album":{"mid":"000owywt4caGcV","name":"老男孩之猛龙过江"},
               "interval":213}
            ]}}}}}
        """.trimIndent()
        val list = QqMusicJson.parseSearch(json)
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("0035GveV3i9dBM", s.id)
        assertEquals("小苹果", s.title)
        assertEquals("筷子兄弟", s.artist)
        assertEquals("老男孩之猛龙过江", s.album)
        assertEquals(213000L, s.durationMs)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000000owywt4caGcV.jpg", s.coverUrl)
        assertEquals("qq", s.sourceId)
    }

    @Test fun parseSearchJoinsSingersAndEmptyOnMalformed() {
        val json = """{"req_0":{"data":{"body":{"item_song":{"items":[
            {"mid":"m1","title":"x","singer":[{"name":"A"},{"name":"B"}]}
        ]}}}}}"""
        assertEquals("A / B", QqMusicJson.parseSearch(json)[0].artist)
        assertTrue(QqMusicJson.parseSearch("not json").isEmpty())
        assertTrue(QqMusicJson.parseSearch("""{"req_0":{"data":{}}}""").isEmpty())
    }

    @Test fun parseSongUrlJoinsSipAndPurl() {
        val json = """{"req_0":{"data":{"sip":["http://aqqmusic.tc.qq.com/"],
            "midurlinfo":[{"result":0,"purl":"M500abc.mp3?guid=10000&vkey=K"}]}}}"""
        assertEquals("http://aqqmusic.tc.qq.com/M500abc.mp3?guid=10000&vkey=K", QqMusicJson.parseSongUrl(json))
    }

    @Test fun parseSongUrlEmptyOnVipOrMalformed() {
        val vip = """{"req_0":{"data":{"sip":["http://aqqmusic.tc.qq.com/"],"midurlinfo":[{"result":104003,"purl":""}]}}}"""
        assertEquals("", QqMusicJson.parseSongUrl(vip))
        assertEquals("", QqMusicJson.parseSongUrl("not json"))
    }

    @Test fun parseLyricBase64DecodesAndParsesTimes() {
        val b64 = "W3RpOuWwj+iLueaenF0KWzAwOjAwLjAwXeesrOS4gOWPpQpbMDA6MTAuNTBd56ys5LqM5Y+lCg=="
        val json = """{"req_0":{"data":{"lyric":"$b64"}}}"""
        val lyric = QqMusicJson.parseLyric(json)
        assertEquals(2, lyric.lines.size)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals("第一句", lyric.lines[0].text)
        assertEquals(10500L, lyric.lines[1].timeMs)
        assertEquals("第二句", lyric.lines[1].text)
    }

    @Test fun parseLyricEmptyOnMalformed() {
        assertTrue(QqMusicJson.parseLyric("not json").lines.isEmpty())
        assertTrue(QqMusicJson.parseLyric("""{"req_0":{"data":{"lyric":"!!!"}}}""").lines.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.data.music.qq.QqMusicJsonTest"`
Expected: FAIL（`Unresolved reference: QqMusicJson`）。

- [ ] **Step 3: Implement `QqMusicJson.kt`**

实现要点（全部 `try/catch` 兜底返回空）：
- `parseSearch`：`root.optJSONObject("req_0").optJSONObject("data").optJSONObject("body").optJSONObject("item_song").optJSONArray("items")` 遍历。每首：`id=optString("mid")`，`title=optString("title").ifBlank{optString("name")}`，`artist` = `singer[]` 的 `name` 用 `" / "` 连接，`album=album.name`，`durationMs=optLong("interval")*1000`，`coverUrl="https://y.gtimg.cn/music/photo_new/T002R300x300M000"+album.mid+".jpg"`（`album.mid` 为空则 `coverUrl=null`），`sourceId="qq"`。
- `parseSongUrl`：`data.midurlinfo[0]`；当 `result==0` 且 `purl` 非空且 `sip` 非空时返回 `sip[0]+purl`，否则 `""`。
- `parseLyric`：`data.lyric` 经 `Base64.getDecoder().decode` 得 LRC 文本，用 `Regex("""\[(\d+):(\d+(?:\.\d+)?)\]""")` 逐行解析时间戳与文本（镜像 `NeteaseJson.parseLyric`），返回 `RemoteLyric`。

- [ ] **Step 4: Run test to verify it passes**

Run: 同 Step 2。
Expected: PASS（6/6）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/music/qq/QqMusicJson.kt app/src/test/java/com/asmrhelper/data/music/qq/QqMusicJsonTest.kt
git commit -m "feat(m5): QQ 音乐响应解析器（搜索/播放地址/歌词）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: QqMusicSource 音源实现

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt`

**Interfaces:**
- Consumes: `QqMusicSign.sign`（Task 1）、`QqMusicJson.parse*`（Task 2）、`MusicSource` 接口（`com.asmrhelper.domain.music`）。
- Produces: `class QqMusicSource(client: OkHttpClient, persistedCookie: String = "", persistedNickname: String = "", onLoginChanged: (String, String) -> Unit = { _, _ -> }) : MusicSource`，`override val id = "qq"`，`override val displayName = "QQ音乐"`。

- [ ] **Step 1: Implement `QqMusicSource.kt`**（薄网络适配器，镜像 `NeteaseMusicSource`；纯逻辑已在 Task 1/2 单测，本类为 OkHttp 网络层，无单测——与已交付的 `NeteaseMusicSource` 一致）

实现要点：
- `post(module, method, paramJson): String`：构造 `payload = {"comm":{"cv":4747474,"ct":24,"format":"json","inCharset":"utf-8","outCharset":"utf-8","notice":0,"platform":"yqq.json","needNewCode":1,"uin":0},"req_0":{"module":module,"method":method,"param":<paramJson 原样内嵌>}}`；`body = payload` 紧凑序列化；URL = `https://u.y.qq.com/cgi-bin/musics.fcg?_=<epochMillis>&sign=<QqMusicSign.sign(body)>`；POST，头 `User-Agent`（Chrome UA）、`Referer: https://y.qq.com/`、`Content-Type: application/json`；异常返回 `""`。全部在 `Dispatchers.IO` 上 `withContext`。
- `search`：`post("music.adaptor.SearchAdaptor","do_search_v2", {"searchid":"12345678901234567890","query":keyword,"search_type":0,"page_num":30,"page_id":page,"highlight":1,"grp":1})` → `QqMusicJson.parseSearch`。
- `getSongUrl(songId, quality)`：忽略 quality，`post("music.vkey.GetVkey","UrlGetVkey", {"uin":"0","guid":"10000","songmid":[songId],"filename":["M500"+songId+songId+".mp3"],"songtype":[0],"loginflag":1,"platform":"20"})` → `QqMusicJson.parseSongUrl`（VIP 返回 `""`）。
- `getLyric(songId)`：`post("music.musichallSong.PlayLyricInfo","GetPlayLyricInfo", {"songMid":songId,"crypt":0,"lrc_t":0,"qrc":0,"roma":0,"trans":0})` → `QqMusicJson.parseLyric`。
- `getCoverUrl(songId)`：`flow { emit("") }`（封面已在 `parseSearch` 从 `album.mid` 派生）。
- `getSongDetail`：`flow {}`；`getPlaylist`：`flow { emit(RemotePlaylist("", "")) }`。
- `getRecommendations()` / `getCharts()`：`flow { emit(emptyList()) }`——QQ 排行榜/推荐接口匿名返回 `500003`（需登录），二期匿名源不做（见 Review Focus / 分期说明）。
- `isLoggedIn`：`MutableStateFlow(persistedCookie.contains("uin"))`；`loginAccount`：`flow { emit(Result.failure(IllegalStateException("QQ 扫码登录后续里程碑提供"))) }`；`importLogin`/`logout` 维护 cookie/nickname + `_isLoggedIn` + 回调（占位，供后续 WebView 扫码登录注入）。

- [ ] **Step 2: Compile + full suite**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全部既有测试 + Task 1/2 新测试通过。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt
git commit -m "feat(m5): QQ 音乐原生音源（搜索/播放地址/歌词/封面，匿名）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: MusicSourceManager QQ 分支

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt:56-69`（`sourceFor`）

**Interfaces:**
- Consumes: `QqMusicSource`（Task 3）、`SourcePresets.QQ`（已存在，`("qq","QQ音乐")`）。
- Produces: `sourceFor(config)` 对 `config.id == "qq"` 返回 `QqMusicSource`，否则 `NeteaseMusicSource`；登录态持久化沿用 `settingsRepository.getMusicLogin/setMusicLogin`（QQ 用同一套 `MusicLogin(cookie, nickname)`）。

- [ ] **Step 1: Implement QQ branch in `sourceFor`**

把 `sourceCache.getOrPut(config.id) { ... }` 内改为 `when (config.id)`：`"qq"` → 构造 `QqMusicSource(client, login.cookie, login.nickname, onLoginChanged 同现有 lambda)`；`else` → 现有 `NeteaseMusicSource(...)`。`import` 加 `com.asmrhelper.data.music.qq.QqMusicSource`。（Hilt 装配，无单测，与现有 `sourceFor` 一致。）

- [ ] **Step 2: Compile + full suite**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt
git commit -m "feat(m5): 音源管理器接入 QQ 分支

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Audio.sourceSongId + 歌词链路

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/domain/model/Audio.kt`（加字段）
- Modify: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverViewModel.kt:143-151`（`RemoteSong.toAudio`）
- Modify: `app/src/main/java/com/asmrhelper/data/music/RemoteLyricMapper.kt`（`isRemoteSong`）
- Modify: `app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt:443-444`（`loadLyricsFor`）
- Test: `app/src/test/java/com/asmrhelper/ui/discover/RemoteSongMapperTest.kt`（加用例）
- Test: `app/src/test/java/com/asmrhelper/data/music/RemoteLyricMapperTest.kt`（加用例）

**Interfaces:**
- Consumes: `Audio`、`RemoteSong.toAudio`、`Audio.isRemoteSong()`（Task 3/4 不依赖本任务，但 QQ 歌词依赖它）。
- Produces: `Audio.sourceSongId: String?`（平台原生歌曲 id 字符串）；`isRemoteSong()` 对 QQ（id=0 但 sourceSongId 非空）返回 true；`loadLyricsFor` 用 `sourceSongId` 拉歌词。

- [ ] **Step 1: Write the failing tests**

`RemoteSongMapperTest.kt` 加：
```kotlin
@Test fun mapsStringMidToSourceSongId() {
    val audio = RemoteSong("0035GveV3i9dBM", "小苹果", "人", sourceId = "qq").toAudio("https://s/1.mp3")
    assertEquals(0L, audio.id)
    assertEquals("0035GveV3i9dBM", audio.sourceSongId)
}
```

`RemoteLyricMapperTest.kt` 加（import `com.asmrhelper.domain.model.Audio`）：
```kotlin
@Test fun isRemoteSongTrueForQqStringMid() {
    assertTrue(Audio(id = 0L, title = "x", filePath = "u", sourceId = "qq", sourceSongId = "0035GveV3i9dBM").isRemoteSong())
}
@Test fun isRemoteSongFalseForLocal() {
    assertFalse(Audio(id = 0L, title = "x", filePath = "u").isRemoteSong())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.ui.discover.RemoteSongMapperTest" --tests "com.asmrhelper.data.music.RemoteLyricMapperTest"`
Expected: FAIL（`sourceSongId` 未定义 / 断言失败）。

- [ ] **Step 3: Implement the four edits**

1. `Audio.kt`：在 `sourceId: String = ""` 后加 `val sourceSongId: String? = null`。
2. `DiscoverViewModel.kt` 的 `RemoteSong.toAudio`：`Audio(...)` 参数加 `sourceSongId = id`（把 `RemoteSong.id` 字符串原样带上）。
3. `RemoteLyricMapper.kt`：`fun Audio.isRemoteSong() = sourceId.isNotBlank() && (id > 0L || !sourceSongId.isNullOrBlank())`。
4. `PlayViewModel.kt` `loadLyricsFor`：`getLyric(audio.sourceSongId ?: audio.id.toString())`。

- [ ] **Step 4: Run full suite to verify it passes**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全部测试通过（含原 Netease 路径回归）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/domain/model/Audio.kt app/src/main/java/com/asmrhelper/ui/discover/DiscoverViewModel.kt app/src/main/java/com/asmrhelper/data/music/RemoteLyricMapper.kt app/src/main/java/com/asmrhelper/ui/play/PlayViewModel.kt app/src/test/java/com/asmrhelper/ui/discover/RemoteSongMapperTest.kt app/src/test/java/com/asmrhelper/data/music/RemoteLyricMapperTest.kt
git commit -m "feat(m5): Audio 增加 sourceSongId 支持 QQ 非数字 songmid 歌词

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 分期说明（超出本计划的验证结论，供 reviewer 与用户知悉）

- **扫码登录 / 账号密码登录**：QQ 音乐登录走 ptlogin2，本会话实测「QR 端点 + `qrsig`/`pt_login_sig` cookie」可拿，但 `ptqrlogin` 轮询对非浏览器客户端稳定返回 **403（WAF 反爬）**；正确做法是 WebView 加载登录页 + 抓 `CookieManager`。此项为独立的 UI/生命周期 + 网络流程，另立里程碑（M6）。
- **排行榜 / 推荐**：QQ 排行榜 `musicToplist.ToplistInfo.GetAll` 匿名返回 `500003`（需登录），推荐同理。本计划 `getCharts()`/`getRecommendations()` 返回空，QQ 源以「搜索」为主发现路径；登录落地后一并补齐。
- **VIP 歌**：匿名 vkey 对 `pay_play=1` 的歌稳定返回 `104003`（绿钻），即使用户登录、未开绿钻仍不可播；`getSongUrl` 正确返回空串并走「无法获取播放地址」提示，属于预期行为而非缺陷。
