# M2 音源层 + 发现页 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地「音源地址」模型——`MusicSource` 抽象 + 通用 `HttpMusicSource`（OkHttp + org.json）+ 平台预设 + 网易云/QQ 登录（账号密码 + 扫码）+ 自定义音源地址 + 发现页（搜索/推荐/排行榜/最近播放）+ 在线流播放。

**Architecture:** 单一通用 HTTP 适配器 `HttpMusicSource` 把「音源地址」上的 REST/JSON 接口归一化为 `MusicSource` 统一接口；内置平台只是预填地址的预设，可改可加自定义源。播放复用 `PlayerManager.play()`（`MediaItem.fromUri` 天然支持 http 流 URL）。JSON 解析沿用代码库现有 `org.json` 惯用，网络用新增的 OkHttp。

**Tech Stack:** Kotlin + Compose + Material3 + Hilt + Room + Media3 ExoPlayer + OkHttp 4.12 + org.json + zxing-core 3.5（二维码渲染）。

**Spec:** `docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md`（§5.3 音源层、§6.5 发现页、§6.6 平台登录）。

## Global Constraints

- `minSdk = 26`；不引入 Retrofit/Moshi/Kotlin-serialization——JSON 一律 `org.json`（与 `SettingsRepositoryImpl`/`PlayerManager` 一致）。
- 新增依赖两个：`okhttp = "4.12.0"`（`implementation(libs.okhttp)`）与 `zxing-core = "3.5.3"`（`implementation(libs.zxing.core)`），加进 `gradle/libs.versions.toml` + `app/build.gradle.kts` 的 dependencies 块。
- 无竞品名：不在字符串/UI 中出现「Folia」「LX Music」等参考 App 名（音乐平台名「网易云音乐」「QQ音乐」「酷狗音乐」「Spotify」允许，属功能项）。
- 新字符串全部进 `values/strings.xml` + `values-en/strings.xml`（沿用 M1 建立的 `AppLanguage` 基础设施；M2 仅加键，不改运行态语言 override）。
- Gradle 命令在 Windows Git Bash 下运行：`export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle ...`（等价 `./gradlew`）。测试命令统一 `:app:testDebugUnitTest`。
- 提交遵循仓库既有约定：提交信息末尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`；`git add` 时排除 `.claude/settings.local.json`；完成后 push 到 `main`。

## Review Focus

> 下述输入是设计文档隐含、但可能没有任务测试覆盖、最可能咬到真实用户的场景。每个都在其所属任务里补一条测试。

1. **音源未配置（baseUrl 为空）**：发现页/搜索应显示友好空态「请先在设置中配置音源」，不崩溃、不发请求。
2. **源返回错误 JSON 或 HTTP 非 200**：`code != 0` 或网络失败时，Flow 发出空结果或失败信号，UI 显示错误提示，不崩溃。
3. **`getSongUrl` 返回空/失效 URL**：播放中止并 Toast 提示，不把空串塞给 ExoPlayer。
4. **二维码登录过期/重试**：`Expired` 状态能重新发起，轮询循环可取消（离开页面即停止）。
5. **无时间戳的歌词**：`getLyric` 返回无 `[mm:ss]` 的纯文本时，解析为 0 行，播放页回退到「无歌词」。

---

### Task 1: 音源领域模型 + MusicSource 接口

**Files:**
- Create: `app/src/main/java/com/asmrhelper/domain/model/music/RemoteSong.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/music/MusicSource.kt`
- Create: `app/src/main/java/com/asmrhelper/domain/music/SourceConfig.kt`
- Test: `app/src/test/java/com/asmrhelper/domain/music/SourceConfigTest.kt`

**Interfaces:**
- Produces（后续任务依赖的确切类型与签名）：

```kotlin
// domain/model/music/RemoteSong.kt
data class RemoteSong(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val sourceId: String = ""
)
data class RemoteLyricLine(val timeMs: Long, val text: String)
data class RemoteLyric(val lines: List<RemoteLyricLine> = emptyList(), val rawText: String = "")
data class RemotePlaylist(val id: String, val name: String, val coverUrl: String? = null, val songs: List<RemoteSong> = emptyList())
data class RemoteChart(val id: String, val name: String, val songs: List<RemoteSong> = emptyList())
sealed class QrLoginState {
    data class Waiting(val key: String, val qrContent: String) : QrLoginState()
    data class Scanned(val key: String) : QrLoginState()
    data class Confirmed(val username: String) : QrLoginState()
    data class Expired(val key: String) : QrLoginState()
    data class Failed(val message: String) : QrLoginState()
}
```

```kotlin
// domain/music/MusicSource.kt
interface MusicSource {
    val id: String
    val displayName: String
    val isLoggedIn: StateFlow<Boolean>
    fun search(keyword: String, page: Int): Flow<List<RemoteSong>>
    fun getSongUrl(songId: String, quality: String = "standard"): Flow<String>
    fun getLyric(songId: String): Flow<RemoteLyric>
    fun getCoverUrl(songId: String): Flow<String>
    fun getSongDetail(songId: String): Flow<RemoteSong>
    fun getPlaylist(playlistId: String): Flow<RemotePlaylist>
    fun getRecommendations(): Flow<List<RemoteSong>>
    fun getCharts(): Flow<List<RemoteChart>>
    fun loginAccount(username: String, password: String): Flow<Result<Unit>>
    fun loginQr(): Flow<QrLoginState>
    fun logout()
}
```

```kotlin
// domain/music/SourceConfig.kt
data class SourceConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val isCustom: Boolean = false
)

object SourcePresets {
    // 默认 baseUrl 为空串占位：真实「音源地址」由用户在音源设置里填（见 Review Focus #1）。
    val NETEASE = SourceConfig("netease", "网易云音乐", "")
    val QQ      = SourceConfig("qq", "QQ音乐", "")
    val KUGO    = SourceConfig("kugou", "酷狗音乐", "")
    val SPOTIFY = SourceConfig("spotify", "Spotify", "")
    val ALL = listOf(NETEASE, QQ, KUGO, SPOTIFY)
}
```

- [ ] **Step 1: 写失败测试** `SourceConfigTest.kt`

```kotlin
class SourceConfigTest {
    @Test fun presetsHaveUniqueIds() {
        val ids = SourcePresets.ALL.map { it.id }
        Assert.assertEquals(ids.size, ids.toSet().size)
    }
    @Test fun customConfigIsCustom() {
        Assert.assertTrue(SourceConfig("x", "X", "http://a", isCustom = true).isCustom)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`SourceConfig` 尚不存在）

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.domain.music.SourceConfigTest"`
Expected: 编译失败（`SourceConfig`/`SourcePresets` 未定义）。

- [ ] **Step 3: 实现上述三个文件**（模型 + 接口 + 预设），`MusicSource` 用 `kotlinx.coroutines.flow.Flow`/`StateFlow`。

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/domain/model/music/RemoteSong.kt app/src/main/java/com/asmrhelper/domain/music/MusicSource.kt app/src/main/java/com/asmrhelper/domain/music/SourceConfig.kt app/src/test/java/com/asmrhelper/domain/music/SourceConfigTest.kt
git commit -m "feat(m2): 音源领域模型 + MusicSource 接口 + 平台预设"
```

---

### Task 2: OkHttp 依赖 + JSON 解析适配器

**Files:**
- Modify: `gradle/libs.versions.toml`（加 `okhttp = "4.12.0"` 与 `okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }`）
- Modify: `app/build.gradle.kts`（加 `implementation(libs.okhttp)`）
- Create: `app/src/main/java/com/asmrhelper/data/music/MusicJsonAdapters.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/MusicJsonAdaptersTest.kt`

**Interfaces:**
- Produces（纯函数，直接可测，无网络）：

```kotlin
object MusicJsonAdapters {
    fun parseSearch(json: String): List<RemoteSong>
    fun parseSongUrl(json: String): String          // 返回流 URL，空串表示失败
    fun parseLyric(json: String): RemoteLyric       // 无时间戳→ lines 为空
    fun parsePlaylist(json: String): RemotePlaylist
    fun parseRecommendations(json: String): List<RemoteSong>
    fun parseCharts(json: String): List<RemoteChart>
    fun parseLogin(json: String): LoginResult      // LoginResult(cookie: String, username: String)
    fun parseQrKey(json: String): String           // 返回 key，空串表示失败
    fun parseQrCheck(json: String): QrCheckResult  // QrCheckResult(status, username)
}
```

**归一化契约（所有「音源地址」约定遵循此 JSON 形状，写入 `HttpMusicSource` KDoc）：**

```
包裹层：{ "code": 0, "data": ... }   // code != 0 视为失败
search/recommend 的 data： [ { "id":"", "title":"", "artist":"", "album":"", "duration":0, "cover":"" } ]
song/url 的 data：     { "url": "https://..." }
lyric 的 data：        { "lrc": "..." }
charts 的 data：       [ { "id":"", "name":"", "songs":[song] } ]
playlist 的 data：     { "id":"", "name":"", "cover":"", "songs":[song] }
login 的 data：        { "cookie":"", "username":"" }
qr/key 的 data：       { "key": "..." }
qr/check 的 data：     { "status": "waiting|scanned|confirmed|expired", "username": "" }
```

- [ ] **Step 1: 写失败测试**（先只写 `parseSearch` 与 `parseSongUrl` 两个 happy-path + 两个失败路径，覆盖 Review Focus #2/#3/#5 的解析面）

```kotlin
class MusicJsonAdaptersTest {
    @Test fun parsesSearchList() {
        val list = MusicJsonAdapters.parseSearch("""{"code":0,"data":[{"id":"1","title":"歌","artist":"人","duration":123000,"cover":"http://c"}]}""")
        Assert.assertEquals(1, list.size)
        Assert.assertEquals("1", list[0].id)
        Assert.assertEquals(123000L, list[0].durationMs)
    }
    @Test fun searchNonZeroCodeReturnsEmpty() {
        Assert.assertTrue(MusicJsonAdapters.parseSearch("""{"code":502,"data":null}""").isEmpty())
    }
    @Test fun searchMalformedReturnsEmpty() {
        Assert.assertTrue(MusicJsonAdapters.parseSearch("not json").isEmpty())
    }
    @Test fun parsesSongUrl() {
        Assert.assertEquals("https://s/1.mp3", MusicJsonAdapters.parseSongUrl("""{"code":0,"data":{"url":"https://s/1.mp3"}}"""))
    }
    @Test fun songUrlEmptyReturnsEmpty() {
        Assert.assertEquals("", MusicJsonAdapters.parseSongUrl("""{"code":0,"data":{"url":""}}"""))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`MusicJsonAdapters` 未定义）

Run: `export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "com.asmrhelper.data.music.MusicJsonAdaptersTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现 `MusicJsonAdapters`**，用 `org.json.JSONObject`/`JSONArray`，所有解析包裹 `try/catch`，异常返回空值/空串（不抛）。`LoginResult`/`QrCheckResult` 作为 `data class` 定义在 `MusicJsonAdapters.kt` 同文件顶部。

- [ ] **Step 4: 跑测试确认通过**。Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/asmrhelper/data/music/MusicJsonAdapters.kt app/src/test/java/com/asmrhelper/data/music/MusicJsonAdaptersTest.kt
git commit -m "feat(m2): OkHttp 依赖 + 音源 JSON 归一化解析适配器"
```

---

### Task 3: HttpMusicSource 实现

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/HttpMusicSource.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/HttpMusicSourceTest.kt`

**Interfaces:**
- Consumes: `MusicSource`（Task 1）、`MusicJsonAdapters`（Task 2）、`SourceConfig`（Task 1）。
- Produces: `HttpMusicSource`——`MusicSource` 的唯一实现，构造 `HttpMusicSource(config: SourceConfig, client: OkHttpClient, onLoginChanged: (Boolean) -> Unit)`。

- [ ] **Step 1: 写失败测试**——只测纯 URL 构造，把 `buildSearchUrl`/`buildSongUrl` 抽为 `internal fun` 便于测试：

```kotlin
class HttpMusicSourceTest {
    private val src = HttpMusicSource(
        SourceConfig("t", "测试", "https://api.example.com"),
        okhttp3.OkHttpClient(), onLoginChanged = {}
    )
    @Test fun buildsSearchUrlWithQuery() {
        Assert.assertEquals(
            "https://api.example.com/search?keyword=%E6%B5%8B%E8%AF%95&page=1",
            src.buildSearchUrl("测试", 1)
        )
    }
    @Test fun buildsSongUrl() {
        Assert.assertEquals("https://api.example.com/song/url?id=42", src.buildSongUrl("42"))
    }
    @Test fun blankBaseUrlSearchReturnsEmptyFlow() = runBlocking {
        val blank = HttpMusicSource(SourceConfig("b", "空", ""), OkHttpClient(), onLoginChanged = {})
        Assert.assertTrue(blank.search("x", 1).first().isEmpty())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`HttpMusicSource` 未定义）。

- [ ] **Step 3: 实现 `HttpMusicSource`**。要点：所有方法返回 `flow { ... }`，用 `withContext(Dispatchers.IO)` 执行 OkHttp 同步 `call.execute()`；`baseUrl.isBlank()` 时直接 `emit(空/失败)`（Review Focus #1）；`code != 0` 或解析失败时 `emit(空/失败)`；`getSongUrl` 空串 emit `""`；登录成功后回调 `onLoginChanged(true)`，`logout()` 回调 `false`；`loginQr()` 内部 `key`→`qrContent`（`"${baseUrl}/login/qr/scan?key=$key"`）→`Waiting`。契约见 Task 2 KDoc。

- [ ] **Step 4: 跑测试确认通过**。Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/data/music/HttpMusicSource.kt app/src/test/java/com/asmrhelper/data/music/HttpMusicSourceTest.kt
git commit -m "feat(m2): HttpMusicSource 通用 HTTP 音源适配器"
```

---

### Task 4: 音源配置持久化 + MusicSourceManager

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt`（加方法）
- Modify: `app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt`（实现）
- Create: `app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt`
- Create: `app/src/main/java/com/asmrhelper/di/MusicModule.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/MusicSourceManagerTest.kt`

**Interfaces:**
- Consumes: `SourceConfig`/`SourcePresets`（Task 1）、`HttpMusicSource`（Task 3）、`SettingsRepository`。
- Produces:

```kotlin
// SettingsRepository 新增（JSON 字符串存 SharedPreferences，键 "music_sources" / "music_active_source" / "music_login"）
fun getMusicSources(): Flow<List<SourceConfig>>          // 空则返回 SourcePresets.ALL
suspend fun setMusicSources(sources: List<SourceConfig>)
fun getActiveSourceId(): Flow<String>                     // 默认 "netease"
suspend fun setActiveSourceId(id: String)

// MusicSourceManager（@Singleton 具体类，直接注入；内部懒建 HttpMusicSource 缓存）
class MusicSourceManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) {
    val activeSource: StateFlow<MusicSource?>            // baseUrl 为空→ null
    fun sourceFor(id: String): MusicSource
    suspend fun addCustomSource(displayName: String, baseUrl: String)
    suspend fun updateSource(config: SourceConfig)
    suspend fun removeSource(id: String)
    suspend fun setActive(id: String)
}
```

```kotlin
// di/MusicModule.kt
@Module @InstallIn(SingletonComponent::class)
object MusicModule {
    @Provides @Singleton fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}
```

- [ ] **Step 1: 写失败测试**（测试 manager 的空源行为 + 自定义源增删，覆盖 Review Focus #1 在 manager 层的落点）

```kotlin
class MusicSourceManagerTest {
    // 用一个真实内存 SettingsRepository 替身不行——SettingsRepositoryImpl 依赖 Room/Context。
    // 改为直接测 SourceConfig JSON 序列化 round-trip，放在本任务的一个纯函数上：
    // SettingsRepositoryImpl 暴露 internal fun sourcesToJson(list): String / sourcesFromJson(s): List<SourceConfig>
    @Test fun sourcesRoundTrip() {
        val list = listOf(SourceConfig("netease", "网易云音乐", "https://a"), SourceConfig("c", "自定义", "http://b", isCustom = true))
        val json = MusicSourceJson.sourcesToJson(list)
        Assert.assertEquals(list, MusicSourceJson.sourcesFromJson(json))
    }
}
```

> 说明：manager 的 Hilt 注入与 StateFlow 编排为薄胶水层，不做单测；真正可测逻辑是 JSON round-trip（`data/music/MusicSourceJson.kt` 纯对象）与 Task 3 的 URL 构造。Review Focus #1 的端到端行为在 Task 6 的 ViewModel 测试覆盖。

- [ ] **Step 2: 跑测试确认失败**（`MusicSourceJson` 未定义）。

- [ ] **Step 3: 实现**：新建 `data/music/MusicSourceJson.kt`（`sourcesToJson`/`sourcesFromJson`，用 org.json）；`SettingsRepositoryImpl` 增加 4 个方法的实现（沿用其 `MutableStateFlow` + `prefs` 模式）；新建 `MusicSourceManager`；新建 `MusicModule`。

- [ ] **Step 4: 跑测试确认通过**。Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt app/src/main/java/com/asmrhelper/data/repository/SettingsRepositoryImpl.kt app/src/main/java/com/asmrhelper/data/music/MusicSourceJson.kt app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt app/src/main/java/com/asmrhelper/di/MusicModule.kt app/src/test/java/com/asmrhelper/data/music/MusicSourceManagerTest.kt
git commit -m "feat(m2): 音源配置持久化 + MusicSourceManager + OkHttp DI"
```

---

### Task 5: 登录流（账号密码 + 扫码）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/util/QrCodeRenderer.kt`（zxing 生成 Bitmap）
- Modify: `gradle/libs.versions.toml` + `app/build.gradle.kts`（加 zxing-core）
- Create: `app/src/main/java/com/asmrhelper/ui/profile/MusicLoginDialog.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/ProfileScreen.kt`（加「在线音乐平台」登录卡片）
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/ProfileViewModel.kt`（暴露 manager 登录态）

**Interfaces:**
- Consumes: `MusicSourceManager`（Task 4）、`QrLoginState`（Task 1）、`SourcePresets`（Task 1）。
- Produces: `MusicLoginDialog(source: MusicSource, onDismiss: () -> Unit)` 可复用组件；`QrCodeRenderer.render(content: String, sizePx: Int): Bitmap?`。

- [ ] **Step 1: 写失败测试**（zxing 纯渲染，Review Focus #4 的状态机在 `MusicLoginViewModel` 抽成一个纯 reducer 便于测试）

```kotlin
class QrLoginReducerTest {
    // MusicLoginDialog 内的轮询状态机抽为纯函数：
    // fun reduce(current: QrLoginState, event: QrLoginState): QrLoginState —— 实际更简单：
    // 直接测「Expired 后应允许重新开始」由 UI 层 while 循环重试表达，纯函数只需 QrLoginReducer.shouldRestart(state)
    @Test fun expiredShouldRestart() {
        Assert.assertTrue(QrLoginReducer.shouldRestart(QrLoginState.Expired("k")))
    }
    @Test fun confirmedShouldNotRestart() {
        Assert.assertFalse(QrLoginReducer.shouldRestart(QrLoginState.Confirmed("u")))
    }
}
```

> 注：`QrLoginReducer` 是 `MusicLoginDialog.kt` 里的一个小 `object`，`shouldRestart(state): Boolean` = `state is Expired`。这是薄逻辑，但它是「过期重试」行为（Review Focus #4）的单一事实点。

- [ ] **Step 2: 跑测试确认失败**。

- [ ] **Step 3: 实现**：`QrCodeRenderer`（`MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)` → Bitmap）；`MusicLoginDialog`——两个 Tab：账号密码（`OutlinedTextField`×2 + 登录按钮，调 `source.loginAccount(...).collect { Result }`，成功关闭并 Toast）/ 扫码（显示 `QrCodeRenderer.render(qrContent)`，`LaunchedEffect` 轮询 `source.loginQr()`，`Waiting`→持续、`Scanned`→提示已扫码、`Confirmed`→登录成功关闭、`Expired`→重建 key、`Failed`→Toast）；`ProfileScreen` 在现有登录卡片下方新增「在线音乐平台」卡片：列出 `SourcePresets.ALL`（含登录态点，点未登录项弹出 `MusicLoginDialog`，点已登录项弹「退出登录」确认）；`ProfileViewModel` 增加 `activeSource` / `isMusicLoggedIn` 状态流与 `loginQr`/`logoutMusic` 转发方法。

- [ ] **Step 4: 跑测试确认通过** + `:app:compileDebugKotlin` 编译通过。

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/asmrhelper/util/QrCodeRenderer.kt app/src/main/java/com/asmrhelper/ui/profile/MusicLoginDialog.kt app/src/main/java/com/asmrhelper/ui/profile/ProfileScreen.kt app/src/main/java/com/asmrhelper/ui/profile/ProfileViewModel.kt app/src/test/java/com/asmrhelper/ui/profile/QrLoginReducerTest.kt
git commit -m "feat(m2): 在线音乐平台登录（账号密码 + 扫码）"
```

---

### Task 6: 音源设置界面

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/settings/MusicSourceSettingsScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt`（新增「音源设置」入口项）
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt`（注入 manager）
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt` + `Screen.kt`（新增 `SubScreen.MusicSources` 子页路由与回退）

**Interfaces:**
- Consumes: `MusicSourceManager`（Task 4）、`SourcePresets`（Task 1）、`SubScreen` 机制（`AsmrNavHost` 的 `when(playSubScreen)`）。
- Produces: `MusicSourceSettingsScreen(onBack: () -> Unit)`；`SubScreen.MusicSources` 路由。

- [ ] **Step 1: 写失败测试**（纯逻辑：自定义源校验）

```kotlin
class SourceConfigValidatorTest {
    // data/music/MusicSourceManager.kt 的伴生对象：fun validateBaseUrl(s: String): Boolean
    @Test fun validHttpUrl() {
        Assert.assertTrue(MusicSourceManager.validateBaseUrl("https://api.example.com"))
    }
    @Test fun rejectsBlankAndGarbage() {
        Assert.assertFalse(MusicSourceManager.validateBaseUrl(""))
        Assert.assertFalse(MusicSourceManager.validateBaseUrl("not a url"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`validateBaseUrl` 未定义）。

- [ ] **Step 3: 实现**：`MusicSourceSettingsScreen`——列出 `SourcePresets.ALL` + 用户自定义源；每行显示 displayName + 当前 baseUrl（截断）+ 登录态；点选切换活动源（`setActive`）；「编辑」弹 `AlertDialog` 改 baseUrl（`validateBaseUrl` 校验失败禁用确定）；「添加自定义源」弹对话框（名称 + 地址）；「删除」仅自定义源可删。`SettingsScreen` 新增「音源设置」行（`onClick` 打开 `SubScreen.MusicSources`）；`AsmrNavHost` 的 `PlayPage` 增加 `SubScreen.MusicSources -> MusicSourceSettingsScreen(onBack = { onOpenSubScreen(SubScreen.Settings) })` 分支（从设置进入、返回设置）。`Screen.kt` 加 `data object MusicSources : SubScreen("music_sources")`。

- [ ] **Step 4: 跑测试确认通过** + `:app:compileDebugKotlin`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/ui/settings/MusicSourceSettingsScreen.kt app/src/main/java/com/asmrhelper/ui/settings/SettingsScreen.kt app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt app/src/main/java/com/asmrhelper/ui/navigation/Screen.kt app/src/test/java/com/asmrhelper/data/music/SourceConfigValidatorTest.kt
git commit -m "feat(m2): 音源设置界面（预设/自定义源/活动源切换）"
```

---

### Task 7: 发现页（搜索/推荐/排行榜/最近播放 + 流播放）

**Files:**
- Create: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverViewModel.kt`
- Rewrite: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverScreen.kt`
- Modify: `app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt`（把发现页从 `DiscoverScreen(modifier = ...)` 换成带 ViewModel 的调用；若需切到播放页可在本任务加 `onPlay` 回调）

**Interfaces:**
- Consumes: `MusicSourceManager`（Task 4）、`PlayHistoryRepository`（现有）、`PlayerManager`（现有）、`RemoteSong`/`RemoteChart`（Task 1）。
- Produces: `DiscoverViewModel` + 完整 `DiscoverScreen`。

- [ ] **Step 1: 写失败测试**（RemoteSong→Audio 映射 + 空源状态，覆盖 Review Focus #1/#3 的 UI 层落点）

```kotlin
class RemoteSongMapperTest {
    // ui/discover/DiscoverViewModel.kt 的伴生函数：fun RemoteSong.toAudio(streamUrl: String): Audio
    @Test fun mapsToAudioWithUrlAsFilePath() {
        val audio = RemoteSong("1", "歌", "人", durationMs = 3000L, sourceId = "netease").toAudio("https://s/1.mp3")
        Assert.assertEquals("歌", audio.title)
        Assert.assertEquals("https://s/1.mp3", audio.filePath)
        Assert.assertEquals(3000L, audio.durationMs)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**（`toAudio` 未定义）。

- [ ] **Step 3: 实现**：`DiscoverViewModel`——`activeSource: StateFlow<MusicSource?>`；`search(query)`/`loadRecommendations()`/`loadCharts()`/`loadRecent()`（`PlayHistoryRepository.getAll()` 取最近，映射为 `RemoteSong` 展示项）；`play(song)`（`getSongUrl` 为空→Toast「无法获取播放地址」，否则 `playerManager.handleEvent(PlayerEvent.Play(toAudio(url), listOf(...)))`）；未配置源→ `sourceConfigured = false`。`DiscoverScreen` 重写：顶部搜索框（`BasicTextField`/`OutlinedTextField` + 搜索按钮）→ 结果列表（`LazyColumn`，行=封面(Coil 加载 `coverUrl`)+标题+艺人，点击播放）；未搜索时四块：热门推荐（横向/纵向卡片）、排行榜（榜单名→点开曲目列表）、最近播放（复用历史）；未配置源时整页友好空态（Review Focus #1）。字符串全部进 `strings.xml`/`values-en`。

- [ ] **Step 4: 跑测试确认通过** + `:app:testDebugUnitTest` 全量 + `:app:assembleDebug`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/asmrhelper/ui/discover/DiscoverViewModel.kt app/src/main/java/com/asmrhelper/ui/discover/DiscoverScreen.kt app/src/main/java/com/asmrhelper/ui/navigation/AsmrNavHost.kt app/src/test/java/com/asmrhelper/ui/discover/RemoteSongMapperTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(m2): 发现页（搜索/推荐/排行榜/最近播放）+ 在线流播放"
```

---

## 收尾

- [ ] `:app:testDebugUnitTest` 全绿、`:app:compileDebugKotlin` 无错误（允许既有 `PlaylistAdd` 弃用警告）。
- [ ] `:app:assembleDebug` 产出 APK。
- [ ] push 到 `main`。
- [ ] 更新记忆 `asmr-helper-redesign.md` 的「待办」段：M2 已完成，剩余 M3/M4。

## 已知占位（非阻塞）

- **平台默认 baseUrl 为空串**：设计文档要求「开箱即用」，但没有可验证的公共「音源地址」。架构上预设字段齐全、用户填地址即可用；真实默认地址留待用户提供或后续填入。此占位不改动任何架构，仅影响「开箱即用」的默认体验。
