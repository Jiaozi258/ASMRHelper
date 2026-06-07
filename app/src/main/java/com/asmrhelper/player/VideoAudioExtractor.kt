package com.asmrhelper.player

import android.content.Context
import android.media.MediaMetadataRetriever
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

    /** Set before [extractAudio] to force a specific platform extractor.
     *  "auto" (or null) → detect from URL; "bilibili" | "youtube" | "douyin" → force. */
    var selectedPlatform: String? = null

    enum class InitResult { OK }

    fun getLastError(): String = lastError ?: "未知错误"

    companion object {
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 300_000
        private val CN_UA =
            "Mozilla/5.0 (Linux; Android 14; zh-CN) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

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
                    lastError = "不支持的平台，请手动选择 (B站/YouTube/抖音)"
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
            ?: run { lastError = "无法识别B站视频ID (BV号)"; return null }
        val resp = bilibiliApi("x/web-interface/view", "bvid=$bvid") ?: return null
        val data = resp.optJSONObject("data")
            ?: run { lastError = "B站API返回数据异常"; return null }
        val title = data.optString("title", "未知视频")
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
            ?: run { lastError = "无法识别B站视频ID (BV号)"; return null }

        android.util.Log.d("Extractor", "B站BV号: $bvid")
        onProgress(0f)
        val infoResp = bilibiliApi("x/web-interface/view", "bvid=$bvid") ?: return null
        val data = infoResp.optJSONObject("data")
            ?: run { lastError = "B站API未返回视频数据（视频可能已删除或私密）"; return null }
        val title = data.optString("title", "未知视频")
        val cid = data.optLong("cid", -1L)
        val durSec = data.optLong("duration", 0L)
        android.util.Log.d("Extractor", "B站视频: title=$title cid=$cid dur=${durSec}s")
        if (cid < 0) { lastError = "B站API未返回分P信息"; return null }

        val playResp = bilibiliApi("x/player/playurl", "bvid=$bvid&cid=$cid&fnval=16&qn=0&fnver=0&fourk=1")
            ?: return null
        val dash = playResp.optJSONObject("data")?.optJSONObject("dash")
        val audioArray: JSONArray? = dash?.optJSONArray("audio")
        if (audioArray == null || audioArray.length() == 0) {
            lastError = "该视频没有可提取的音频流（可能需要大会员）"
            android.util.Log.e("Extractor", "B站无音频流: dash=$dash")
            return null
        }
        val audioItem = audioArray.getJSONObject(0)
        var audioUrl = audioItem.optString("baseUrl", "")
        if (audioUrl.isEmpty()) {
            val backups = audioItem.optJSONArray("backupUrl")
            if (backups != null && backups.length() > 0) audioUrl = backups.getString(0)
        }
        if (audioUrl.isEmpty()) { lastError = "音频流地址为空"; return null }

        android.util.Log.d("Extractor", "B站音频URL: ${audioUrl.take(80)}...")
        val dir = outputDir(platform)
        val outputFile = File(dir, safeFileName(title) + ".m4a")

        onProgress(0.05f)
        downloadWithHeaders(audioUrl, outputFile, "https://www.bilibili.com", onProgress, startPct = 0.05f, endPct = 0.95f)

        android.util.Log.d("Extractor", "B站下载完成: exists=${outputFile.exists()} size=${outputFile.length()}")
        return finalizeResult(outputFile, title, platform, url, durSec)
    }

    private fun bilibiliApi(path: String, query: String): JSONObject? {
        return try {
            val apiUrl = "https://api.bilibili.com/$path?$query"
            android.util.Log.d("Extractor", "B站API请求: $apiUrl")
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = 30_000
                setRequestProperty("Referer", "https://www.bilibili.com")
                setRequestProperty("User-Agent", CN_UA)
            }
            val code = conn.responseCode
            android.util.Log.d("Extractor", "B站API响应: HTTP $code")
            val body = if (code in 200..299) conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            else conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299 || body.isBlank()) {
                android.util.Log.e("Extractor", "B站API HTTP错误: $code, body=${body.take(200)}")
                lastError = "B站API HTTP $code"; return null
            }
            android.util.Log.d("Extractor", "B站API body前200字符: ${body.take(200)}")
            val json = JSONObject(body)
            val apiCode = json.optInt("code", -1)
            if (apiCode != 0) {
                val msg = json.optString("message", "")
                lastError = when (apiCode) {
                    -404 -> "视频不存在或已删除"; -403 -> "无权访问该视频"
                    62002 -> "视频不可见"; 62004 -> "视频不存在"
                    else -> "B站API ($apiCode): $msg".take(150)
                }
                android.util.Log.e("Extractor", "B站API业务错误: code=$apiCode, $lastError")
                return null
            }
            json
        } catch (e: UnknownHostException) {
            android.util.Log.e("Extractor", "B站DNS不可达: ${e.message}")
            lastError = "无法连接B站服务器: ${e.message}"; null
        }
        catch (e: SocketTimeoutException) {
            android.util.Log.e("Extractor", "B站超时: ${e.message}")
            lastError = "连接B站超时"; null
        }
        catch (e: IOException) {
            android.util.Log.e("Extractor", "B站网络错误: ${e.message}")
            lastError = "网络错误: ${e.message?.take(120)}"; null
        }
    }

    // ═══════════════════════════════════════════════════════
    //  YouTube (Invidious public API)
    // ═══════════════════════════════════════════════════════

    private fun extractYoutubeInfo(url: String, platform: String): VideoInfo? {
        val videoId = extractYoutubeId(url)
            ?: run { lastError = "无法识别YouTube视频ID"; return null }
        // YouTube in China: try Invidious/Piped proxies. If all fail, tell user.
        val resp = invidiousApi("videos/$videoId?fields=title,lengthSeconds")
        if (resp != null) {
            val title = resp.optString("title", "未知视频")
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
            ?: run { lastError = "无法识别YouTube视频ID"; return null }

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
        val title = resp.optString("title", "未知视频")
        val durSec = resp.optLong("lengthSeconds", 0L)
        val formats = resp.optJSONArray("adaptiveFormats")
            ?: run { lastError = "代理API未返回媒体流"; return null }

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
        if (audioUrl.isNullOrEmpty()) { lastError = "未找到可用的音频流 (itag 140/251/139)"; return null }

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
                        errors.add("$baseUrl → Piped格式需二次请求")
                    }
                    errors.add("$baseUrl → 响应缺少必要字段")
                } else {
                    errors.add("$baseUrl → HTTP $code")
                }
            } catch (e: UnknownHostException) { errors.add("$baseUrl → DNS不可达") }
            catch (e: SocketTimeoutException) { errors.add("$baseUrl → 超时") }
            catch (e: Exception) { errors.add("$baseUrl → ${e.javaClass.simpleName}") }
        }
        lastError = buildString {
            append("YouTube 提取失败 — 所有代理节点在境内均不可达。\nYouTube 需要 VPN 才能使用。\n\n")
            append("已尝试 ${errors.size} 个节点:")
            errors.take(4).forEach { append("\n• $it") }
            if (errors.size > 4) append("\n• ... 还有 ${errors.size - 4} 个")
        }
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
                lastError = "抖音提取服务 HTTP $code"
                return null
            }
            val json = JSONObject(body)
            val status = json.optString("status", "")
            if (status == "error") {
                val errCode = json.optJSONObject("error")?.optString("code", "unknown") ?: "unknown"
                lastError = when (errCode) {
                    "error.api.link" -> "抖音链接无效"
                    "error.api.rate" -> "请求太频繁，稍后重试"
                    else -> "抖音提取失败: $errCode"
                }
                return null
            }
            val dlUrl = json.optString("url", "")
            if (dlUrl.isEmpty()) { lastError = "未获取到下载链接"; return null }

            val fname = json.optString("filename", "audio.m4a")
            val title = parseTitle(fname)
            val dir = outputDir(platform)
            val outputFile = File(dir, safeFileName(title) + ".m4a")

            onProgress(0.1f)
            downloadFileGeneric(dlUrl, outputFile, onProgress, startPct = 0.1f, endPct = 0.95f)
            return finalizeResult(outputFile, title, platform, videoUrl, 0L)
        } catch (e: UnknownHostException) {
            lastError = "抖音提取需要联网到海外服务，当前网络无法访问。\n请尝试使用VPN后重试。"
            return null
        } catch (e: SocketTimeoutException) {
            lastError = "连接提取服务超时"
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
            lastError = "下载完成但文件为空"
            return null
        }
        if (file.length() < 1024) {
            file.delete()
            lastError = "文件太小 (${file.length()}B)，下载可能失败"
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
            setRequestProperty("Range", "bytes=0-")
        }
        conn.connect()
        val total = conn.contentLengthLong
        val input = conn.inputStream ?: throw IOException("下载流为空")
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
        conn.disconnect()
    }

    private fun downloadFileGeneric(
        urlString: String, dest: File,
        onProgress: (Float) -> Unit, startPct: Float = 0f, endPct: Float = 1f
    ) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = CONNECT_TIMEOUT; readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
        }
        conn.connect()
        val total = conn.contentLengthLong
        val input = conn.inputStream ?: throw IOException("下载流为空")
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
        conn.disconnect()
    }

    // ── General helpers ──────────────────────────────────

    private fun resolveRedirect(urlString: String): String? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"; instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT; readTimeout = 10_000
            }
            val loc = conn.getHeaderField("Location"); conn.disconnect()
            if (conn.responseCode in 300..399 && loc != null) loc else {
                val c2 = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT; readTimeout = 10_000
                }
                val u = c2.url.toString(); c2.disconnect(); u
            }
        } catch (e: Exception) { lastError = "短链接解析失败: ${e.message}"; null }
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
        return try {
            val r = MediaMetadataRetriever(); r.setDataSource(file.absolutePath)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            r.release(); d?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }
}
