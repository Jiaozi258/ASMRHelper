# M2 音源重做（原生网易云，Folia 式）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用原生 Kotlin 实现网易云音乐真实接口（weapi 加密）+ 真实登录（手机号+密码 / 扫码），替换现有假 `HttpMusicSource` + 假登录，让发现页显示真实在线音乐、登录真正可用、在线流播放可跑。

**Architecture:** 保留 `MusicSource` 接口不动（消费方：发现页 / 登录对话框已按它写）；新增 `NeteaseMusicSource` 直接请求 `music.163.com`（weapi 加密）；删除 `HttpMusicSource`、通用 JSON 归一化 `MusicJsonAdapters`、`SourceConfig.baseUrl` 自定义源地址概念；`MusicSourceManager` 简化为「网易云 + QQ」两个原生源 + 登录态持久化。

**Tech Stack:** Kotlin + Compose + Material3 + Hilt + Room + Media3 ExoPlayer + OkHttp 4.12 + `javax.crypto`（AES/RSA）+ `java.security.MessageDigest`（MD5）。移除 zxing（登录二维码改为直接展示网易云返回的 base64 图片）。

**Spec:** `docs/superpowers/specs/2026-10-17-asmrhelper-redesign-design.md`（§5.3 音源层、§6.5 发现页、§6.6 平台登录）。本计划**修订** §5.3 的「音源地址/自定义源」方案为「原生平台实现（Folia 式）」，依据用户 2026-10-17 新拍板。

## Global Constraints

- `minSdk = 26`；JSON 一律 `org.json`（`javax.crypto`/`MessageDigest` 为 JDK 标准库，无需新依赖）。
- 移除依赖：zxing-core（登录二维码改为 base64 图片直显）。OkHttp 保留。
- 无竞品名：不在字符串/UI 出现「Folia」「LX Music」等参考 App 名（「网易云音乐」「QQ音乐」允许）。
- 新字符串全部进 `values/strings.xml` + `values-en/strings.xml`。
- 登录 cookie（`MUSIC_U`）+ 账号昵称持久化到 SharedPreferences（沿用 `SettingsRepository`），重启保留登录态。
- Gradle 命令在 Windows Git Bash 下运行：`export JAVA_HOME=/f/mc && gradle-dist/gradle-8.11.1/bin/gradle ...`。测试命令统一 `:app:testDebugUnitTest`。
- 提交末尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`；`git add` 排除 `.claude/settings.local.json`；完成后 push 到 `main`。

## 关键事实（weapi 加密，常量已核实，必须逐字使用）

```
params = Base64( AES-128-CBC( Base64( AES-128-CBC(json, key=0CoJUm6Qyw8W8jud, iv=0102030405060708) ), key=secKey, iv=0102030405060708 ) )
secKey = 随机 16 位 base62 字符串（charset 0123456789abcde...，见实现）
encSecKey = hex( RSA-1024/ECB/NoPadding( reverse(secKey) ) )，补齐 256 hex 位
RSA 公钥 exponent = 010001
RSA 公钥 modulus (hex) = 00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7
AES 模式 = AES/CBC/PKCS5Padding，密钥/IV 为 UTF-8 字节
MD5 用于密码：login 时 password 字段 = md5(原始密码)（小写 hex）
```

**端点（POST，weapi 加密 body，`Content-Type: application/x-www-form-urlencoded`，字段 `params`/`encSecKey`）：**

| 功能 | 端点 | body（加密前 JSON） |
|---|---|---|
| 手机号+密码登录 | `/weapi/login/cellphone` | `{phone, countrycode:"86", password: md5(pwd), rememberLogin:"true"}` |
| 扫码 key | `/weapi/login/qr/key` | `{type:"1"}` → `data.unikey` |
| 扫码生成 | `/weapi/login/qr/create?qrimg=true` | `{key, qrimg:true}` → `data.qrimg`（base64 PNG） |
| 扫码轮询 | `/weapi/login/qr/check` | `{key, type:"1"}` → `code` 800=过期/801=等待/802=已扫/803=成功（cookie 在 Set-Cookie） |
| 搜索 | `/weapi/cloudsearch/get/web` | `{s, type:"1", limit, offset}` |
| 播放地址 | `/weapi/song/enhance/player/url/v1` | `{ids:"[id]", level:"standard", encodeType:"aac"}` → `data[0].url` |
| 歌词 | `/weapi/song/lyric` | `{id, lv:-1, tv:-1}` → `lrc.lyric` |
| 榜单 | `/weapi/toplist` | `{}` → `list` |
| 推荐（免登录） | `/weapi/personalized/newsong` | `{limit:30}` → `result` |

> 端点路径以 `https://music.163.com` 为 base。若运行时发现网易云风控升级（如需要 `checkToken`），实现阶段按 `@neteasecloudmusicapienhanced/api` 的现行做法微调端点/参数，但 weapi 加密算法不变。

## Review Focus

> 下述输入是设计隐含、但任务测试可能不覆盖、最可能咬到真实用户的场景。

1. **未登录也能用发现页**：推荐用免登录接口 `personalized/newsong`，搜索/播放地址不需 cookie；未登录不应崩溃、不应发带空 cookie 的请求。
2. **登录失败/过期**：账号密码错误、二维码过期（code 800）→ 明确失败提示并允许重试，轮询循环可取消（离开对话框即停止）。
3. **播放地址为空/失效**：`getSongUrl` 返回空 → Toast「无法获取播放地址」，不塞空串给 ExoPlayer。
4. **歌词无时间戳**：`getLyric` 返回无 `[mm:ss]` 纯文本 → 0 行，播放页回退「无歌词」。
5. **weapi 加密字节级正确**：`params`/`encSecKey` 长度与编码正确（AES base64、RSA 256 hex），否则所有请求 400。

---

### Task 1: weapi 加密工具

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/neteaselib/Weapi.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/neteaselib/WeapiTest.kt`

**Interfaces:**
- Produces（纯 Kotlin，无网络，后续所有请求复用）：
```kotlin
object Weapi {
    fun md5(s: String): String                       // 小写 hex
    fun aesEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray  // AES/CBC/PKCS5
    fun rsaEncrypt(plain: ByteArray): ByteArray      // 用内置公钥 RSA/ECB/NoPadding，输入为 reverse(secKey)
    fun encrypt(json: String): WeapiForm             // WeapiForm(params: String, encSecKey: String)
    fun randomSecKey(): String                       // 16 位 base62
}
data class WeapiForm(val params: String, val encSecKey: String)
```

- [ ] **Step 1: 写失败测试**（用标准 AES-128-CBC 测试向量 + MD5 已知向量 + RSA 输出格式断言）
```kotlin
class WeapiTest {
    @Test fun md5LowerHex() { Assert.assertEquals("5d41402abc4b2a76b9719d911017c592", Weapi.md5("hello")) }
    @Test fun aesCbcPkcs5MatchesNistVector() { /* key/iv/plaintext/ciphertext 用 NIST SP800-38A 向量 */ }
    @Test fun encryptProducesBase64ParamsAnd256HexKey() {
        val form = Weapi.encrypt("""{"a":1}""")
        // params 非空且 base64 可解；encSecKey 长度 == 256 且全 hex
    }
    @Test fun randomSecKeyIs16Base62() { Assert.assertEquals(16, Weapi.randomSecKey().length) }
}
```
（NIST 向量：key=`2b7e151628aed2a6abf7158809cf4f3c`，iv=`000102030405060708090a0b0c0d0e0f`，明文块 `6bc1bee22e409f96e93d7e117393172a` → 密文 `7649abac8119b246cee98e9b12e9197d`，PKCS5 填充后需对应完整块。实现阶段以 `AES/CBC/NoPadding` 对单块验证，`PKCS5Padding` 另验往返。）

- [ ] **Step 2: 跑测试确认失败**（`Weapi` 未定义）
- [ ] **Step 3: 实现 `Weapi`**：`MessageDigest.getInstance("MD5")`、`Cipher.getInstance("AES/CBC/PKCS5Padding")`、`Cipher.getInstance("RSA/ECB/NoPadding")`（用 `RSAPublicKeySpec(modulus, exponent)` 构造 `KeyFactory.getInstance("RSA")`）。`encrypt`：AES key1 → base64 → AES secKey → base64；`encSecKey` 对 `reverse(secKey)` 的字节做 RSA → hex 左补零到 256。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: Commit**（`feat(m2): 网易云 weapi 加密工具`）

---

### Task 2: NeteaseMusicSource 请求封装 + 搜索

**Files:**
- Create: `app/src/main/java/com/asmrhelper/data/music/netease/NeteaseMusicSource.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/netease/NeteaseMusicSourceTest.kt`

**Interfaces:**
- Consumes: `MusicSource`、`Weapi`（Task 1）、`RemoteSong`/`QrLoginState`（现有）、OkHttp。
- Produces: `NeteaseMusicSource(client: OkHttpClient)` 实现 `MusicSource`（含 `search` 解析）。纯解析逻辑抽为 `object NeteaseJson { fun parseSearch(json): List<RemoteSong>; fun parseSongUrl(json): String; fun parseLyric(json): RemoteLyric; fun parseCharts(json): List<RemoteChart>; fun parseRecommend(json): List<RemoteSong> }` 便于单测。

- [ ] **Step 1: 写失败测试**（纯解析，无网络：`NeteaseJson.parseSearch` 用真实网易云搜索响应片段）
```kotlin
class NeteaseJsonTest {
    @Test fun parseSearchSongs() { /* 给定 {result:{songs:[{id,name,ar:[{name}],al:{name,picUrl},dt}]}}，断言 RemoteSong 字段映射 */ }
    @Test fun parseSearchEmptyOnMalformed() { Assert.assertTrue(NeteaseJson.parseSearch("not json").isEmpty()) }
}
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现 `NeteaseJson.parseSearch` + `NeteaseMusicSource.search`**：`search` = `flow { weapiPost("/weapi/cloudsearch/get/web", {...}) → parseSearch }`。`weapiPost` 内部用 `Weapi.encrypt(json)` 构造 form body，`OkHttp` 同步 `execute()`（`Dispatchers.IO`），返回响应字符串。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: Commit**（`feat(m2): NeteaseMusicSource 请求封装 + 搜索`）

---

### Task 3: 播放地址 / 歌词 / 榜单 / 推荐

**Files:**
- Modify: `NeteaseMusicSource.kt`、`NeteaseJson.kt`
- Test: `app/src/test/java/com/asmrhelper/data/music/netease/NeteaseJsonTest.kt`（扩展）

**Interfaces:**
- Consumes: Task 2 的 `NeteaseMusicSource`/`NeteaseJson`。

- [ ] **Step 1: 写失败测试**（`parseSongUrl`/`parseLyric`/`parseCharts`/`parseRecommend` 各一 happy-path + 一 malformed）
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`getSongUrl`（`data[0].url`，空→`""`）、`getLyric`（`lrc.lyric` 无 `[mm:ss]`→0 行，Review Focus #4）、`getCharts`（`list` 映射 `RemoteChart`）、`getRecommendations`（`result` 映射 `RemoteSong`）。
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: Commit**（`feat(m2): 网易云 播放地址/歌词/榜单/推荐`）

---

### Task 4: 登录（手机号+密码 / 扫码）+ cookie 持久化

**Files:**
- Modify: `NeteaseMusicSource.kt`（`loginAccount`/`loginQr`/`logout`/`isLoggedIn`）
- Modify: `app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt`（登录态持久化到 `SettingsRepository`）
- Modify: `app/src/main/java/com/asmrhelper/domain/repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt`（加 `musicLogin` 存取：cookie + 昵称，键 `music_login_<id>`）
- Test: `app/src/test/java/com/asmrhelper/data/music/netease/NeteaseQrReducerTest.kt`

**Interfaces:**
- Consumes: Task 2/3 的 `NeteaseMusicSource`、`SettingsRepository`。
- Produces: 真实登录流。`loginAccount(phone, password)` 发 `/login/cellphone`（`password=md5`），成功存 cookie + 设 `isLoggedIn=true`；`loginQr()`：key→create(qrimg base64)→`Waiting(key, qrImageBase64)`→每 2s `check`，映射 code 到 `QrLoginState`（800→Expired、801→Waiting 持续、802→Scanned、803→Confirmed 并读 cookie）；`logout()` 清 cookie。

- [ ] **Step 1: 写失败测试**（纯状态机：QR code → `QrLoginState` 映射）
```kotlin
class NeteaseQrReducerTest {
    // object NeteaseQr { fun stateFor(code: Int): QrLoginState }  —— code 800/801/802/803/其他 各一断言
}
```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`NeteaseQr.stateFor`；`NeteaseMusicSource` 登录方法（含 cookie 存取回调 `onLoginChanged`）；`MusicSourceManager` 启动时恢复 cookie 登录态。
- [ ] **Step 4: 跑测试确认通过** + `:app:compileDebugKotlin`
- [ ] **Step 5: Commit**（`feat(m2): 网易云真实登录（手机号+密码/扫码）+ cookie 持久化`）

---

### Task 5: MusicSourceManager 重构 + 删除假适配器

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/data/music/MusicSourceManager.kt`（产出原生源）
- Modify: `app/src/main/java/com/asmrhelper/domain/music/SourceConfig.kt`（去掉 `baseUrl`，`SourcePresets` = 网易云 + QQ）
- Delete: `app/src/main/java/com/asmrhelper/data/music/HttpMusicSource.kt`
- Delete: `app/src/main/java/com/asmrhelper/data/music/MusicJsonAdapters.kt`、`MusicSourceJson.kt` 及对应测试
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/MusicSourceSettingsScreen.kt`（删自定义源增删改/URL 编辑，只留活动源切换）
- Modify: `app/src/main/java/com/asmrhelper/ui/settings/SettingsViewModel.kt`（同步简化）

**Interfaces:**
- Consumes: Task 4 的 `NeteaseMusicSource`、`SourceConfig`。
- Produces: `MusicSourceManager`（`sources`/`activeSourceId`/`activeSource`/`setActive`），`SourceConfig(id, displayName)`，`SourcePresets.NETEASE`/`QQ`。

- [ ] **Step 1: 写失败测试**（`SourcePresets` 仅含网易云+QQ、id 唯一）
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：删 `HttpMusicSource`/`MusicJsonAdapters`/`MusicSourceJson`；`MusicSourceManager.sourceFor(config)` 返回 `NeteaseMusicSource`（按 id 缓存）；`activeSource` 不再判空（原生源恒可用）。
- [ ] **Step 4: 跑测试确认通过** + `:app:compileDebugKotlin`
- [ ] **Step 5: Commit**（`feat(m2): MusicSourceManager 产出原生源 + 移除假适配器`）

---

### Task 6: 登录对话框真实化 + 发现页空态调整

**Files:**
- Modify: `app/src/main/java/com/asmrhelper/ui/profile/MusicLoginDialog.kt`（扫码显示 base64 图片；手机号字段 label 改「手机号/邮箱」）
- Modify: `app/src/main/java/com/asmrhelper/domain/model/music/RemoteSong.kt`（`QrLoginState.Waiting` 改带 `qrImageBase64: String?`）
- Modify: `app/src/main/java/com/asmrhelper/util/QrCodeRenderer.kt`（若仅登录用，则删除；连同 zxing 依赖）
- Modify: `app/src/main/java/com/asmrhelper/ui/discover/DiscoverScreen.kt` + `DiscoverViewModel.kt`（去掉 `activeSource == null` 空态分支，原生源恒可用；保留「播放失败」提示）
- Modify: `app/src/main/res/values/strings.xml` + `values-en/strings.xml`

**Interfaces:**
- Consumes: Task 4 的登录流、`QrLoginState`。
- Produces: 真实登录 UI。

- [ ] **Step 1: 写失败测试**（`QrLoginState.Waiting` 结构变化导致的既有 `QrLoginReducerTest` 调整/重建，以及 base64 判空辅助）
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 实现**：`MusicLoginDialog` 扫码 Tab 解码 base64 图片直显（`android.util.Base64.decode` → Bitmap）；账号 Tab label 改「手机号/邮箱」；删除 zxing 依赖与 `QrCodeRenderer`；`DiscoverViewModel`/`DiscoverScreen` 去掉空源空态。
- [ ] **Step 4: 跑测试确认通过** + `:app:compileDebugKotlin`
- [ ] **Step 5: Commit**（`feat(m2): 登录对话框真实化 + 发现页去掉空源空态`）

---

## 收尾

- [ ] `:app:testDebugUnitTest` 全绿、`:app:compileDebugKotlin` 无错误。
- [ ] `:app:assembleDebug` 产出 APK。
- [ ] push 到 `main`。
- [ ] 更新记忆 `asmr-helper-redesign.md`：M2 音源已改为原生网易云（Folia 式），QQ 二期待做。

## 已知占位（非阻塞）

- **QQ 音乐（二期）**：签名算法（zzb/zzc）+ 登录（MQTT 扫码）复杂度高，本计划不做，另起计划。
- **真机验证**：网易云接口的端到端行为（weapi 是否被风控、登录/播放）需 OPPO 设备重连后验收；weapi 字节级正确性由 Task 1 的标准向量测试覆盖。
