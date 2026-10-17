# M6 QQ 音乐登录 + 榜单/推荐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 QQ 音乐源支持 WebView 网页登录（扫码/密码在 QQ 网页内完成），登录后可用排行榜与个性化推荐，补完原「QQ 音乐二期」被分期的登录部分。

**Architecture:** 复用网易云已交付的 WebView 登录模式（`MusicLoginDialog` 的 `QrWebLoginPanel` = WebView + `CookieManager` 抓 Cookie → `importLogin`）。把登录对话框从「写死网易云 URL/域/Cookie 键」参数化到 `MusicSource` 接口（每个源声明自己的 `webLogin` 配置 + 是否支持账号密码 API 登录）；QQ 源据此用 QQ 网页登录页抓 `qm_keyst`/`uin`。榜单/推荐在 `QqMusicJson` 加纯解析器，`QqMusicSource` 用登录 Cookie 发认证请求。

**Tech Stack:** Kotlin + OkHttp + `org.json` + JUnit4（现有栈）。WebView 用 Android 自带 `WebView`/`CookieManager`。无新依赖。

**Spec:** docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md

## Global Constraints

- minSdk 26，compileSdk 35（沿用现状，不升级）。
- 测试仅 `junit:junit:4.13.2` + `org.json:json:20240303`（无 Robolectric、无 Compose UI test、无网络单测）。
- 解析器「异常返回空/空串，不抛」，与 `NeteaseJson`/`QqMusicJson` 一致。
- 测试/编译命令（bash）：
  `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
- 提交信息以 `Co-Authored-By: Claude Code <noreply@anthropic.com>` 结尾；前缀沿用 `feat(m6):` / `fix(m6):`。

## Review Focus

1. **登录态判定**：WebView 抓到的 QQ Cookie 串必须含鉴权键（`qm_keyst` 或 `uin`）才判定已登录；空 Cookie/匿名 Cookie 不得误判为已登录。
2. **榜单/推荐解析**：登录态接口响应形状可能与匿名不同或缺键（`group`/`toplist`/`songList` 缺失），`parseCharts`/`parseRecommendations` 不得 NPE，一律返回空列表。
3. **认证请求 Cookie 头**：`getCharts`/`getRecommendations` 必须带登录 Cookie + 正确 `comm.uin`；未登录时回退到匿名（返回空）而非崩溃。
4. **对话框参数化不回归网易云**：账号密码 tab 仅在 `supportsAccountLogin` 的源显示；网易云仍走 `loginAccount` API + WebView 扫码，行为不变。
5. **uin 提取**：QQ Cookie 的 `uin=o<号码>` 带 `o` 前缀，认证请求的 `comm.uin` 必须是纯数字，去前缀逻辑不得破坏其它键。

---

### Task 1: QQ 榜单/推荐解析器 + uin 提取（纯逻辑）

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicJson.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/qq/QqMusicJsonTest.kt`

**Interfaces:**
- Consumes: `RemoteSong`/`RemoteChart`（`com.asmrhelper.domain.model.music`，已存在）。
- Produces: `object QqMusicJson { fun parseCharts(json: String): List<RemoteChart>; fun parseRecommendations(json: String): List<RemoteSong>; fun uin(cookie: String): String }` —— 供 Task 2 `QqMusicSource` 调用。全部「异常返回空/空串」。

- [ ] **Step 1: 写失败测试**

`QqMusicJsonTest.kt` 加三个用例（响应根 `{code, req_0:{code, data}}`）：

```kotlin
@Test fun parseChartsMapsToplists() {
    val json = """{"code":0,"req_0":{"code":0,"data":{"group":[
        {"groupName":"巅峰榜","toplist":[
          {"topId":26,"title":"热歌榜","songList":[
            {"mid":"0035GveV3i9dBM","name":"小苹果","singer":[{"name":"筷子兄弟"}],
             "album":{"mid":"000owywt4caGcV","name":"老男孩"},"interval":213}
          ]}
        ]}
    ]}}}"""
    val charts = QqMusicJson.parseCharts(json)
    assertEquals(1, charts.size)
    assertEquals("26", charts[0].id)
    assertEquals("热歌榜", charts[0].name)
    assertEquals(1, charts[0].songs.size)
    assertEquals("0035GveV3i9dBM", charts[0].songs[0].id)
}

@Test fun parseChartsEmptyOnMalformed() {
    assertTrue(QqMusicJson.parseCharts("not json").isEmpty())
    assertTrue(QqMusicJson.parseCharts("""{"req_0":{"data":{}}}""").isEmpty())
}

@Test fun parseRecommendationsAndUin() {
    val json = """{"req_0":{"data":{"songlist":[
        {"mid":"m1","name":"歌","singer":[{"name":"A"}],"album":{"mid":"am","name":"专"},"interval":200}
    ]}}}"""
    assertEquals(1, QqMusicJson.parseRecommendations(json).size)
    assertEquals("", QqMusicJson.uin(""))
    assertEquals("123456", QqMusicJson.uin("uin=o123456; qm_keyst=K"))
    assertEquals("", QqMusicJson.uin("qm_keyst=K"))
}
```

- [ ] **Step 2: 跑测试验证失败**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.data.music.qq.QqMusicJsonTest"`
Expected: FAIL（`Unresolved reference: parseCharts / parseRecommendations / uin`）。

- [ ] **Step 3: 实现三个函数**

- 抽私有 `songOf(obj: JSONObject): RemoteSong`（与 `parseSearch` 每首映射一致：`id=mid`、`title=title.ifBlank{name}`、`artist=singer[].name` 用 `" / "` 连接、`album=album.name`、`durationMs=interval*1000`、`coverUrl` 从 `album.mid` 派生、`sourceId="qq"`），`parseSearch` 改为复用。
- `parseCharts`：遍历 `req_0.data.group[]` 的 `toplist[]`，每项 `RemoteChart(id=optString("topId"), name=optString("title"), songs=songList[] 映射)`；缺 `songList` 时 songs 为空。整体 try/catch 返回空。
- `parseRecommendations`：遍历 `req_0.data.songlist[]`，`songOf` 映射；整体 try/catch 返回空。
- `uin(cookie)`：`Regex("""(?:^|;\s*)uin=([^;]+)""")` 取第一个 `uin=` 值，去前导 `o` 后返回纯数字；无则 `""`。

- [ ] **Step 4: 跑测试验证通过**

Run: 同 Step 2。
Expected: PASS（新增 3 用例 + 既有用例全绿）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/asmrhelper/data/music/qq/QqMusicJson.kt app/src/test/java/com/asmrhelper/data/music/qq/QqMusicJsonTest.kt
git commit -m "feat(m6): QQ 榜单/推荐解析器 + uin 提取

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: QqMusicSource 认证榜单/推荐请求

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt`

**Interfaces:**
- Consumes: `QqMusicJson.parseCharts/parseRecommendations/uin`（Task 1）、`cookie` 字段。
- Produces: `getCharts(): Flow<List<RemoteChart>>`、`getRecommendations(): Flow<List<RemoteSong>>` 由「空」改为带 Cookie 的认证请求；未登录返回空。

- [ ] **Step 1: 实现认证 POST + 两个方法**

- 把 `post` 改为认证感知：请求加 `header("Cookie", cookie)`（`cookie` 为空则不带头）；`comm.uin` 用 `QqMusicJson.uin(cookie).ifBlank { "0" }`。
- `getCharts()`：`post("musicToplist.ToplistInfo", "GetAll", JSONObject())` → `parseCharts`。
- `getRecommendations()`：`post("music.recommend.RecommendAdaptor", "getRecommendation", JSONObject().put("offset",0).put("limit",30))` → `parseRecommendations`（端点/形状为 best-effort，见分期说明）。
- 未登录（`cookie` 空）时 `post` 保持匿名 `uin=0`，接口返回 500003 → 解析器返回空，不崩溃。

- [ ] **Step 2: 编译 + 全量测试**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt
git commit -m "feat(m6): QQ 音源带登录 Cookie 的榜单/推荐

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 登录对话框参数化（QQ WebView 登录接入）

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/domain/music/MusicSource.kt`（加 `webLogin`/`supportsAccountLogin` + `WebLoginConfig`）
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/MusicLoginDialog.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/music/netease/NeteaseMusicSource.kt`
- Modify: `app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt`

**Interfaces:**
- Produces: `MusicSource` 新增 `val webLogin: WebLoginConfig?`（默认 null）、`val supportsAccountLogin: Boolean`（默认 false）；`data class WebLoginConfig(val url: String, val cookieDomain: String, val cookieKeys: Set<String>)`。Netease 覆盖 `supportsAccountLogin=true`、`webLogin=(网易云页/域/键)`；QQ 覆盖 `webLogin=(QQ 页/域/键)`、`supportsAccountLogin` 保持 false。

- [ ] **Step 1: `MusicSource` 加登录配置**

`MusicSource.kt` 内加 `WebLoginConfig` 数据类与两个带默认实现的成员（`webLogin get() = null`、`supportsAccountLogin get() = false`），不强制既有实现改动即编译。

- [ ] **Step 2: Netease / QQ 声明各自配置**

- `NeteaseMusicSource`：`override val supportsAccountLogin = true`、`override val webLogin = WebLoginConfig("https://music.163.com/#/login", "https://music.163.com", setOf("MUSIC_U", "__csrf"))`。
- `QqMusicSource`：`override val webLogin = WebLoginConfig("https://y.qq.com/", "https://y.qq.com", setOf("qm_keyst", "uin"))`。

- [ ] **Step 3: `MusicLoginDialog` 参数化**

- 删掉顶部 `LOGIN_URL`/`COOKIE_DOMAIN` 常量，改为从 `source.webLogin` 读取。
- 账号密码 tab 仅在 `source.supportsAccountLogin` 时渲染；否则对话框只显示 `QrWebLoginPanel`（标签「网页登录」）。
- `QrWebLoginPanel(source, config)` 用 `config.url` 加载、`config.cookieDomain` 轮询、判定改为 `config.cookieKeys.any { all.contains(it) }`、`importLogin(Cookies.select(all, config.cookieKeys), "")`。

- [ ] **Step 4: 编译 + 全量测试**

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL（网易云既有登录路径回归绿）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/asmrhelper/domain/music/MusicSource.kt app/src/main/java/com/asmrhelper/ui/profile/MusicLoginDialog.kt app/src/main/java/com/asmrhelper/data/music/netease/NeteaseMusicSource.kt app/src/main/java/com/asmrhelper/data/music/qq/QqMusicSource.kt
git commit -m "feat(m6): 登录对话框参数化，QQ 音乐接入 WebView 网页登录

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 分期说明（超出本计划验证范围，供 reviewer 与用户知悉）

- **真机端到端验证**：QQ 登录页 URL（`https://y.qq.com/`）与 Cookie 鉴权键（`qm_keyst`/`uin`）、榜单/推荐接口的**登录后**响应形状均需真机 + QQ 账号验证；本计划按公开 qq-music-api/LX Music 的已知形状实现，解析器遇形状不符一律返回空，不崩溃。
- **推荐端点**：`music.recommend.RecommendAdaptor/getRecommendation` 为 best-effort（M5 仅确认匿名返回 500003，未捕获登录后形状）；如真机返回不同形状，`parseRecommendations` 返回空即降级，不影响搜索/播放。
- **账号密码登录**：QQ 的 ptlogin2 密码登录需 JS/ticket，无法走 `loginAccount(username,password)` 简单 POST；QQ 源 `supportsAccountLogin=false`，账号密码由用户在 WebView 的 QQ 网页登录页内完成（网页同时提供扫码与密码）。
- **酷狗/Spotify**：仍为「可选」，不在 M6 范围（原决策「Spotify/酷狗可选」）。
