package com.asmrhelper.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
    private var initialized = false
    private val binaryPath: String
        get() = File(context.filesDir, "yt-dlp").absolutePath

    companion object {
        // yt-dlp standalone binary for Android aarch64
        private const val YTDLP_DOWNLOAD_URL =
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
    }

    suspend fun init() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            val binary = File(binaryPath)
            if (!binary.exists() || !binary.canExecute()) {
                try {
                    downloadBinary(binary)
                    binary.setExecutable(true)
                    initialized = true
                } catch (_: Exception) {
                    // Download failed; will retry on next call
                }
            } else {
                initialized = true
            }
        }
    }

    private fun downloadBinary(dest: File) {
        val url = URL(YTDLP_DOWNLOAD_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.connect()

        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output, 8192)
            }
        }
        conn.disconnect()
    }

    suspend fun extractInfo(url: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(binaryPath, "--dump-json", "--no-playlist", url)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            process.waitFor()

            if (output.isBlank()) return@withContext null

            // Simple JSON extraction without a JSON library (to keep it lightweight)
            val title = extractJsonString(output, "title") ?: "未知视频"
            val durationSec = extractJsonNumber(output, "duration")
            val platform = detectPlatform(url)

            VideoInfo(
                title = title,
                platform = platform,
                durationMs = (durationSec * 1000)
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractAudio(
        url: String,
        onProgress: (Float) -> Unit = {}
    ): ExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "video_audio")
            if (!dir.exists()) dir.mkdirs()

            val info = extractInfo(url) ?: return@withContext null
            val safeTitle = info.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
            val outputTemplate = File(dir, "${System.currentTimeMillis()}_$safeTitle").absolutePath

            val process = ProcessBuilder(
                binaryPath,
                "-x",
                "--audio-format", "m4a",
                "--audio-quality", "0",
                "-o", "$outputTemplate.%(ext)s",
                "--no-playlist",
                "--no-continue",
                "--newline",
                url
            )
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line ?: continue
                // Parse progress from yt-dlp output: "[download]  45.0% of ..."
                val percentMatch = Regex("""\[download\]\s+(\d+\.?\d*)%""").find(text)
                if (percentMatch != null) {
                    val pct = percentMatch.groupValues[1].toFloatOrNull()
                    if (pct != null) onProgress(pct / 100f)
                }
            }
            process.waitFor()
            reader.close()

            val outputFile = File("$outputTemplate.m4a")
            if (outputFile.exists() && outputFile.length() > 0) {
                ExtractionResult(
                    title = info.title,
                    platform = info.platform,
                    sourceUrl = url,
                    filePath = outputFile.absolutePath,
                    durationMs = info.durationMs,
                    fileSizeBytes = outputFile.length()
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun extractJsonNumber(json: String, key: String): Long {
        val pattern = Regex("\"$key\"\\s*:\\s*(-?\\d+(\\.\\d+)?)")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun detectPlatform(url: String): String = when {
        "bilibili.com" in url || "b23.tv" in url -> "bilibili"
        "youtube.com" in url || "youtu.be" in url -> "youtube"
        "douyin.com" in url || "iesdouyin.com" in url -> "douyin"
        else -> "other"
    }
}
