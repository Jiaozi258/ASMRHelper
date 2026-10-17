package com.asmrhelper.player

import android.content.Context
import android.media.MediaMetadataRetriever
import com.asmrhelper.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class VideoInfo(
    val title: String,
    val platform: String,
    val durationMs: Long
)

data class ExtractionResult(
    val title: String,
    val platform: String,
    val sourceUrl: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long
)

@Singleton
class VideoAudioExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastError: String? = null

    /** 当前进行中的下载连接，用于取消时主动断开以中断阻塞读取。 */
    @Volatile private var activeConnection: HttpURLConnection? = null

    /** B站风控需要的 buvid3/buvid4 Cookie（持久化复用，避免每次都换新指纹被风控标记）。 */
    private val biliPrefs by lazy { context.getSharedPreferences("bili_anti_risk", Context.MODE_PRIVATE) }
    @Volatile private var cachedBuvid3: String? = biliPrefs.getString("buvid3", null)
    @Volatile private var cachedBuvid4: String? = biliPrefs.getString("buvid4", null)

    /** 取消进行中的下载：断开底层 socket 使阻塞的 read() 抛出异常。 */
    fun cancelActiveDownload() {
        try { activeConnection?.disconnect() } catch (_: Exception) { }
        activeConnection = null
    }

    /** Set before [extractAudio] to force a specific platform extractor.
     *  "auto" (or null) → detect from URL; "bilibili" | "youtube" | "douyin" → force. */
    var selectedPlatform: String? = null

    enum class InitResult { OK }

    fun getLastError(): String = lastError ?: context.getString(R.string.video_extract_unknown_error)

    companion object {
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 300_000
        private val CN_UA =
            "Mozilla/5.0 (Linux; Android 14; zh-CN) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        // B站 Web API（x/web-interface/view 等）按 PC 网页校验 UA，移动 UA 更容易被风控拦截
        private val PC_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Proxy instances for YouTube — Piped + Invidious.
        // In China, most are blocked by GFW. A VPN is needed.
        // Check https://docs.invidious.io/instances/ for current status.
        private val YOUTUBE_PROXIES = listOf(
            "https://pipedapi.kavin.rocks",       // Piped (often better uptime)
            "https://pipedapi.adminforge.de",
            "https://yewtu.be",                    // Invidious
            "https://inv.nadeko.net",
            "https://inv.bp.projectsegfau.lt",
            "https://invidious.privacyredirect.com",
            "https://vid.puffyan.us",
            "https://invidious.tiekoetter.com",
            "https://inv.zzls.xyz",
        )
    }

    suspend fun init(): InitResult = InitResult.OK
    fun resetInit() { lastError = null }

    // ── Public API ────────────────────────────────────────

    suspend fun extractInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val platform = selectedPlatform ?: detectPlatform(url)
            when (platform) {
                "bilibili" -> extractBilibiliInfo(url, platform)
                "youtube" -> extractYoutubeInfo(url, platform)
                else -> extractBilibiliInfo(url, platform) // fallback
            }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    suspend fun extractAudio(
        url: String,
        onProgress: (Float) -> Unit = {}
    ): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val platform = selectedPlatform ?: detectPlatform(url)
            when (platform) {
                "bilibili" -> extractBilibiliAudio(url, platform, onProgress)
                "youtube" -> extractYoutubeAudio(url, platform, onProgress)
                "douyin" -> extractDouyinAudio(url, platform, onProgress)
                else -> {
                    lastError = context.getString(R.string.video_extract_unsupported_platform)
                    null
                }
            }
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Bilibili (api.bilibili.com)
    // ═══════════════════════════════════════════════════════

    private fun extractBilibiliInfo(url: String, platform: String): VideoInfo? {
        val resolvedUrl = if ("b23.tv" in url) resolveRedirect(url) ?: return null else url
        val bvid = extractBvId(resolvedUrl)
            ?: run { lastError = context.getString(R.string.video_extract_bvid_failed); return null }
        val resp = bilibiliApi("x/web-interface/view", "bvid=$bvid") ?: return null
        val data = resp.optJSONObject("data")
            ?: run { lastError = context.getString(R.string.video_extract_bili_api_abnormal); return null }
        val title = data.optString("title", context.getString(R.string.video_extract_unknown_video))
        val dur = data.optLong("duration", 0L)
        lastError = null
        return VideoInfo(title = title, platform = platform, durationMs = dur * 1000)
    }

    private fun extractBilibiliAudio(
        url: String,
        platform: String,
        onProgress: (Float) -> Unit
    ): ExtractionResult? {
        android.util.Log.d("Extractor", "B站提取开始: url=$url platform=$platform")
        val resolvedUrl = if ("b23.tv" in url) resolveRedirect(url) ?: return null else url
        val bvid = extractBvId(resolvedUrl)
            ?: run { lastError = context.getString(R.string.video_extract_bvid_failed); return null }

        android.util.Log.d("Extractor", "B站BV号: $bvid")
        onProgress(0f)
        val infoResp = bilibiliApi("x/web-interface/view", "bvid=$bvid") ?: return null
        val data = infoResp.optJSONObject("data")
            ?: run { lastError = context.getString(R.string.video_extract_bili_no_data); return null }
        val title = data.optString("title", context.getString(R.string.video_extract_unknown_video))
        val cid = data.optLong("cid", -1L)
        val durSec = data.optLong("duration", 0L)
        android.util.Log.d("Extractor", "B站视频: title=$title cid=$cid dur=${durSec}s")
        if (cid < 0) { lastError = context.getString(R.string.video_extract_bili_no_part); return null }

        val playResp = bilibiliApi("x/player/playurl", "bvid=$bvid&cid=$cid&fnval=16&qn=0&fnver=0&fourk=1")
            ?: return null
        val dash = playResp.optJSONObject("data")?.optJSONObject("dash")
        val audioArray: JSONArray? = dash?.optJSONArray("audio")
        if (audioArray == null || audioArray.length() == 0) {
            lastError = context.getString(R.string.video_extract_no_audio_stream)
            android.util.Log.e("Extractor", "B站无音频流: dash=$dash")
            return null
        }
        val audioItem = audioArray.getJSONObject(0)
        var audioUrl = audioItem.optString("baseUrl", "")
        if (audioUrl.isEmpty()) {
            val backups = audioItem.optJSONArray("backupUrl")
            if (backups != null && backups.length() > 0) audioUrl = backups.getString(0)
        }
        if (audioUrl.isEmpty()) { lastError = context.getString(R.string.video_extract_audio_url_empty); return null }

        android.util.Log.d("Extractor", "B站音频URL: ${audioUrl.take(80)}...")
        val dir = outputDir(platform)
        val outputFile = File(dir, safeFileName(title) + ".m4a")

        onProgress(0.05f)
        downloadWithHeaders(audioUrl, outputFile, "https://www.bilibili.com", onProgress, startPct = 0.05f, endPct = 0.95f)

        android.util.Log.d("Extractor", "B站下载完成: exists=${outputFile.exists()} size=${outputFile.length()}")
        return finalizeResult(outputFile, title, platform, url, durSec)
    }

    /** 组装 B站 buvid Cookie 字符串，首次调用会先走指纹接口拿 buvid3/buvid4。 */
    private fun buvidCookie(): String {
        var b3 = cachedBuvid3
        var b4 = cachedBuvid4
        if (b3.isNullOrEmpty()) {
            val (nb3, nb4) = obtainBuvid()
            b3 = nb3; b4 = nb4
            cachedBuvid3 = b3; cachedBuvid4 = b4
            biliPrefs.edit().putString("buvid3", b3).putString("buvid4", b4).apply()
        }
        if (b3.isNullOrEmpty()) return ""
        // b_nut：会话时间戳。B站校验 Cookie 时，若只带 buvid3 而无 b_nut，
        // 会把 b_nut 记为可疑值 100，更容易触发 412。这里用当前秒级时间戳（与官网 Set-Cookie 行为一致）。
        val nut = System.currentTimeMillis() / 1000
        return buildString {
            append("buvid3=").append(b3)
            if (!b4.isNullOrEmpty()) append("; buvid4=").append(b4)
            append("; b_nut=").append(nut)
        }
    }

    /** 调 B站免费指纹接口获取 buvid3/buvid4，失败则返回 null 降级为无 Cookie。 */
    private fun obtainBuvid(): Pair<String?, String?> {
        return try {
            val conn = (URL("https://api.bilibili.com/x/frontend/finger/spi")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = 10_000
                setRequestProperty("Referer", "https://www.bilibili.com")
                setRequestProperty("User-Agent", PC_UA)
                setRequestProperty("X-Bili-Api-Source", "pc")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
            }
            val code = conn.responseCode
            val body = if (code in 200..299)
                conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            else
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            val data = JSONObject(body).optJSONObject("data")
            val b3 = data?.optString("b_3", "")?.takeIf { it.isNotEmpty() }
            val b4 = data?.optString("b_4", "")?.takeIf { it.isNotEmpty() }
            b3 to b4
        } catch (_: Exception) {
            null to null
        }
    }

    private fun bilibiliApi(path: String, query: String): JSONObject? {
        return try {
            val apiUrl = "https://api.bilibili.com/$path?$query"
            android.util.Log.d("Extractor", "B站API请求: $apiUrl")
            // Referer 需指向对应视频页，否则会被风控拦成 412
            val bvid = Regex("bvid=([a-zA-Z0-9]+)").find(query)?.groupValues?.get(1)
            val referer = if (bvid != null) "https://www.bilibili.com/video/$bvid/" else "https://www.bilibili.com"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = 30_000
                setRequestProperty("Referer", referer)
                setRequestProperty("User-Agent", PC_UA)
                setRequestProperty("X-Bili-Api-Source", "pc")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                // 缺 buvid3 Cookie 会被 B站风控直接拦成 HTTP 412
                val cookie = buvidCookie()
                if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
            }
            val code = conn.responseCode
            android.util.Log.d("Extractor", "B站API响应: HTTP $code")
            val body = if (code in 200..299) conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299 || body.isBlank()) {
                android.util.Log.e("Extractor", "B站API HTTP错误: $code, body=${body.take(200)}")
                lastError = context.getString(R.string.video_extract_bili_api_http, code); return null
            }
            android.util.Log.d("Extractor", "B站API body前200字符: ${body.take(200)}")
            val json = JSONObject(body)
            val apiCode = json.optInt("code", -1)
            if (apiCode != 0) {
                val msg = json.optString("message", "")
                lastError = when (apiCode) {
                    -404 -> context.getString(R.string.video_extract_not_found)
                    -403 -> context.getString(R.string.video_extract_no_access)
                    62002 -> context.getString(R.string.video_extract_not_visible)
                    62004 -> context.getString(R.string.video_extract_not_exist)
                    else -> context.getString(R.string.video_extract_bili_api_code, apiCode, msg).take(150)
                }
                android.util.Log.e("Extractor", "B站API业务错误: code=$apiCode, $lastError")
                return null
            }
            json
        } catch (e: UnknownHostException) {
            android.util.Log.e("Extractor", "B站DNS不可达: ${e.message}")
            lastError = context.getString(R.string.video_extract_bili_unreachable, e.message); null
        }
        catch (e: SocketTimeoutException) {
            android.util.Log.e("Extractor", "B站超时: ${e.message}")
            lastError = context.getString(R.string.video_extract_bili_timeout); null
        }
        catch (e: IOException) {
            android.util.Log.e("Extractor", "B站网络错误: ${e.message}")
            lastError = context.getString(R.string.video_extract_network_error, e.message?.take(120)); null
        }
    }

    // ═══════════════════════════════════════════════════════
    //  YouTube (Invidious public API)
    // ═══════════════════════════════════════════════════════

    private fun extractYoutubeInfo(url: String, platform: String): VideoInfo? {
        val videoId = extractYoutubeId(url)
            ?: run { lastError = context.getString(R.string.video_extract_youtube_id_failed); return null }
        // YouTube in China: try Invidious/Piped proxies. If all fail, tell user.
        val resp = invidiousApi("videos/$videoId?fields=title,lengthSeconds")
        if (resp != null) {
            val title = resp.optString("title", context.getString(R.string.video_extract_unknown_video))
            val dur = resp.optLong("lengthSeconds", 0L)
            lastError = null
            return VideoInfo(title = title, platform = platform, durationMs = dur * 1000)
        }
        // All proxies failed — the error from invidiousApi already says why
        return null
    }

    private fun extractYoutubeAudio(
        url: String,
        platform: String,
        onProgress: (Float) -> Unit
    ): ExtractionResult? {
        val videoId = extractYoutubeId(url)
            ?: run { lastError = context.getString(R.string.video_extract_youtube_id_failed); return null }

        onProgress(0f)

        // YouTube is blocked in China. Try Invidious/Piped proxy instances.
        // The download itself also needs to go through — googlevideo.com may
        // also be blocked, in which case a VPN is required.
        val resp = invidiousApi("videos/$videoId?fields=title,lengthSeconds,adaptiveFormats")
        if (resp != null) {
            return extractFromInvidiousResponse(resp, videoId, url, platform, onProgress)
        }

        // All proxies blocked. lastError already set by invidiousApi.
        return null
    }

    /** Parse proxy API response and download audio.
     *  Handles both Invidious format (itag as string) and Piped format (itag as int). */
    private fun extractFromInvidiousResponse(
        resp: JSONObject, videoId: String, url: String, platform: String,
        onProgress: (Float) -> Unit
    ): ExtractionResult? {
        val title = resp.optString("title", context.getString(R.string.video_extract_unknown_video))
        val durSec = resp.optLong("lengthSeconds", 0L)
        val formats = resp.optJSONArray("adaptiveFormats")
            ?: run { lastError = context.getString(R.string.video_extract_proxy_no_stream); return null }

        // Best audio itags: 140 (m4a 128k), 251 (opus 160k), 139 (m4a 48k)
        val preferredItags = setOf("140", "251", "139", "140", "251", "139")
        var audioUrl: String? = null
        for (target in listOf("251", "140", "139")) {
            for (i in 0 until formats.length()) {
                val fmt = formats.getJSONObject(i)
                // Invidious: itag is string, Piped: itag may be int
                val itagStr = fmt.optString("itag", "")
                val itagInt = fmt.optInt("itag", -1).toString()
                if (itagStr == target || itagInt == target) {
                    audioUrl = fmt.optString("url", "")
                    if (!audioUrl.isNullOrEmpty()) break
                }
            }
            if (!audioUrl.isNullOrEmpty()) break
        }
        if (audioUrl.isNullOrEmpty()) { lastError = context.getString(R.string.video_extract_no_audio_itag); return null }

        val dir = outputDir(platform)
        val outputFile = File(dir, safeFileName(title) + ".m4a")
        onProgress(0.05f)
        downloadFileGeneric(audioUrl, outputFile, onProgress, startPct = 0.05f, endPct = 0.95f)
        return finalizeResult(outputFile, title, platform, url, durSec)
    }

    /** Try each YouTube proxy instance until one responds.
     *  Handles both Piped API (path without /api/v1 prefix for some calls)
     *  and Invidious API (/api/v1/ prefix). */
    private fun invidiousApi(path: String): JSONObject? {
        val errors = mutableListOf<String>()
        for (baseUrl in YOUTUBE_PROXIES) {
            try {
                // Piped uses /<path>, Invidious uses /api/v1/<path>
                val apiUrl = if (baseUrl.contains("pipedapi")) {
                    "$baseUrl/$path"  // Piped: no /api/v1 prefix
                } else {
                    "$baseUrl/api/v1/$path"  // Invidious
                }
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 20_000
                    setRequestProperty("User-Agent", CN_UA)
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                val body = if (code in 200..299)
                    conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                else
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code in 200..299 && body.isNotBlank()) {
                    val json = JSONObject(body)
                    // Piped returns "title" directly; video details are in different format
                    if (json.has("title")) {
                        // If we need adaptiveFormats but don't have them, try Invidious format
                        val needsFormats = path.contains("adaptiveFormats")
                        if (!needsFormats || json.has("adaptiveFormats")) {
                            return json
                        }
                        // Piped doesn't include adaptiveFormats in basic video response.
                        // We need to convert Piped format to look like Invidious format.
                        errors.add(context.getString(R.string.video_extract_piped_needs_second, baseUrl))
                    }
                    errors.add(context.getString(R.string.video_extract_missing_field, baseUrl))
                } else {
                    errors.add(context.getString(R.string.video_extract_http, baseUrl, code))
                }
            } catch (e: UnknownHostException) { errors.add(context.getString(R.string.video_extract_dns_unreachable, baseUrl)) }
            catch (e: SocketTimeoutException) { errors.add(context.getString(R.string.video_extract_timeout, baseUrl)) }
            catch (e: Exception) { errors.add("$baseUrl → ${e.javaClass.simpleName}") }
        }
        val shownNodes = errors.take(4).joinToString("\n") { "• $it" }
        val nodeList = if (errors.size > 4) {
            shownNodes + "\n• " + context.getString(R.string.video_extract_more_nodes, errors.size - 4)
        } else {
            shownNodes
        }
        lastError = context.getString(R.string.video_extract_youtube_failed, errors.size, nodeList)
        return null
    }

    /** Extract YouTube video ID from URL. */
    private fun extractYoutubeId(url: String): String? {
        // youtu.be/VIDEOID
        Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        // youtube.com/watch?v=VIDEOID
        Regex("v=([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        // youtube.com/embed/VIDEOID
        Regex("embed/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        // youtube.com/shorts/VIDEOID
        Regex("shorts/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    // ═══════════════════════════════════════════════════════
    //  Douyin
    // ═══════════════════════════════════════════════════════

    private fun extractDouyinAudio(
        url: String,
        platform: String,
        onProgress: (Float) -> Unit
    ): ExtractionResult? {
        // Douyin requires signed API requests. Try cobalt as a bridge.
        // If cobalt is unreachable, tell the user.
        return tryCobaltExtract(url, platform, onProgress)
            ?: run {
                // lastError is set by tryCobaltExtract
                null
            }
    }

    /** One-shot cobalt API call as a fallback. */
    private fun tryCobaltExtract(
        videoUrl: String,
        platform: String,
        onProgress: (Float) -> Unit
    ): ExtractionResult? {
        // Single cobalt instance — quick fail if DNS blocked
        try {
            val requestBody = JSONObject().apply {
                put("url", videoUrl); put("isAudioOnly", true)
                put("aFormat", "m4a"); put("filenameStyle", "basic")
            }
            val conn = (URL("https://co.wuk.sh/api/json").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(requestBody.toString().toByteArray()) }
            val code = conn.responseCode
            val body = if (code in 200..299) conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            if (code !in 200..299) {
                lastError = context.getString(R.string.video_extract_douyin_http, code)
                return null
            }
            val json = JSONObject(body)
            val status = json.optString("status", "")
            if (status == "error") {
                val errCode = json.optJSONObject("error")?.optString("code", "unknown") ?: "unknown"
                lastError = when (errCode) {
                    "error.api.link" -> context.getString(R.string.video_extract_douyin_invalid)
                    "error.api.rate" -> context.getString(R.string.video_extract_douyin_rate)
                    else -> context.getString(R.string.video_extract_douyin_failed, errCode)
                }
                return null
            }
            val dlUrl = json.optString("url", "")
            if (dlUrl.isEmpty()) { lastError = context.getString(R.string.video_extract_no_download_link); return null }

            val fname = json.optString("filename", "audio.m4a")
            val title = parseTitle(fname)
            val dir = outputDir(platform)
            val outputFile = File(dir, safeFileName(title) + ".m4a")

            onProgress(0.1f)
            downloadFileGeneric(dlUrl, outputFile, onProgress, startPct = 0.1f, endPct = 0.95f)
            return finalizeResult(outputFile, title, platform, videoUrl, 0L)
        } catch (e: UnknownHostException) {
            lastError = context.getString(R.string.video_extract_douyin_offshore)
            return null
        } catch (e: SocketTimeoutException) {
            lastError = context.getString(R.string.video_extract_service_timeout)
            return null
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message?.take(120)}"
            return null
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Shared helpers
    // ═══════════════════════════════════════════════════════

    /** Output directory for extracted audio, per-platform. */
    private fun outputDir(platform: String): File {
        val dir = File(context.filesDir, "video_audio/$platform")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeFileName(title: String): String =
        "${System.currentTimeMillis()}" // avoid name collisions
//        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private fun finalizeResult(
        file: File, title: String, platform: String,
        url: String, durSec: Long
    ): ExtractionResult? {
        if (!file.exists() || file.length() == 0L) {
            lastError = context.getString(R.string.video_extract_file_empty)
            return null
        }
        if (file.length() < 1024) {
            file.delete()
            lastError = context.getString(R.string.video_extract_file_too_small, file.length())
            return null
        }
        val durMs = if (durSec > 0) durSec * 1000 else getAudioDuration(file)
        lastError = null
        return ExtractionResult(
            title = title, platform = platform, sourceUrl = url,
            filePath = file.absolutePath, durationMs = durMs, fileSizeBytes = file.length()
        )
    }

    /** Download with custom Referer. Progress scaled between startPct and endPct. */
    private fun downloadWithHeaders(
        urlString: String, dest: File, referer: String,
        onProgress: (Float) -> Unit, startPct: Float, endPct: Float
    ) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("Referer", referer)
            setRequestProperty("User-Agent", CN_UA)
        }
        conn.connect()
        // Check HTTP status BEFORE reading the stream — HttpURLConnection
        // throws a raw FileNotFoundException for 403/404, which surfaced as
        // an unhelpful "FileNotFoundException" to the user.
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            lastError = httpErrorMessage(code)
            android.util.Log.e("Extractor", "下载HTTP错误: $code, $lastError")
            throw IOException(lastError)
        }
        val total = conn.contentLengthLong
        val input = conn.inputStream ?: throw IOException(context.getString(R.string.video_extract_download_stream_empty))
        activeConnection = conn
        try {
            input.use { s -> dest.outputStream().use { o ->
                    val buf = ByteArray(65536); var dl = 0L; var n: Int
                    while (s.read(buf).also { n = it } != -1) {
                        o.write(buf, 0, n); dl += n
                        if (total > 0L) {
                            val pct = startPct + (endPct - startPct) * (dl.toFloat() / total)
                            onProgress(pct.coerceAtMost(endPct))
                        }
                    }
            } }
        } finally {
            activeConnection = null
            conn.disconnect()
        }
    }

    private fun downloadFileGeneric(
        urlString: String, dest: File,
        onProgress: (Float) -> Unit, startPct: Float = 0f, endPct: Float = 1f
    ) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", CN_UA)
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            lastError = httpErrorMessage(code)
            android.util.Log.e("Extractor", "下载HTTP错误: $code, $lastError")
            throw IOException(lastError)
        }
        val total = conn.contentLengthLong
        val input = conn.inputStream ?: throw IOException(context.getString(R.string.video_extract_download_stream_empty))
        activeConnection = conn
        try {
            input.use { s -> dest.outputStream().use { o ->
                    val buf = ByteArray(16384); var dl = 0L; var n: Int
                    while (s.read(buf).also { n = it } != -1) {
                        o.write(buf, 0, n); dl += n
                        if (total > 0L) {
                            val pct = startPct + (endPct - startPct) * (dl.toFloat() / total)
                            onProgress(pct.coerceAtMost(endPct))
                        }
                    }
            } }
        } finally {
            activeConnection = null
            conn.disconnect()
        }
    }

    /** Translate an HTTP status code into a human-readable Chinese message. */
    private fun httpErrorMessage(code: Int): String = when (code) {
        403 -> context.getString(R.string.http_error_403)
        404 -> context.getString(R.string.http_error_404)
        412 -> context.getString(R.string.http_error_412)
        429 -> context.getString(R.string.http_error_429)
        in 500..599 -> context.getString(R.string.http_error_5xx, code)
        else -> context.getString(R.string.http_error_other, code)
    }

    // ── General helpers ──────────────────────────────────

    private fun resolveRedirect(urlString: String): String? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"; instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT; readTimeout = 10_000
                // 短链解析也需要 UA/Referer，否则默认 Java UA 容易被风控拦成 403/412
                setRequestProperty("User-Agent", CN_UA)
                setRequestProperty("Referer", "https://www.bilibili.com")
            }
            val loc = conn.getHeaderField("Location"); conn.disconnect()
            if (conn.responseCode in 300..399 && loc != null) loc else {
                val c2 = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT; readTimeout = 10_000
                    setRequestProperty("User-Agent", CN_UA)
                    setRequestProperty("Referer", "https://www.bilibili.com")
                }
                val u = c2.url.toString(); c2.disconnect(); u
            }
        } catch (e: Exception) { lastError = context.getString(R.string.video_extract_redirect_failed, e.message); null }
    }

    private fun extractBvId(url: String): String? {
        val m = Regex("BV[a-zA-Z0-9]+").find(url) ?: return null
        return if (m.value.length in 10..14) m.value else m.value.take(12)
    }

    fun detectPlatform(url: String): String = when {
        "bilibili.com" in url || "b23.tv" in url -> "bilibili"
        "youtube.com" in url || "youtu.be" in url -> "youtube"
        "douyin.com" in url || "iesdouyin.com" in url -> "douyin"
        "tiktok.com" in url -> "douyin"
        else -> "other"
    }

    private fun parseTitle(filename: String): String {
        val noExt = filename.substringBeforeLast(".")
        return noExt.replace(Regex("""\s*-\s*(YouTube|Bilibili|TikTok|Douyin|Reddit|Twitter|Instagram|SoundCloud)\s*$""", RegexOption.IGNORE_CASE), "").trim().ifEmpty { noExt }
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }
}
