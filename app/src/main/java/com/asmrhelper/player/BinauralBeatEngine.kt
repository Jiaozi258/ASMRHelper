package com.asmrhelper.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class BinauralPreset(
    val name: String,
    val category: String,
    val baseFrequency: Float,
    val beatFrequency: Float,
    val description: String
) {
    companion object {
        val PRESETS = listOf(
            BinauralPreset("Delta 深睡", "Delta (1-4 Hz)", 100f, 3f, "深度睡眠与修复"),
            BinauralPreset("Delta 入眠", "Delta (1-4 Hz)", 120f, 2f, "引导入睡"),
            BinauralPreset("Theta 冥想", "Theta (4-8 Hz)", 150f, 6f, "深度冥想与内省"),
            BinauralPreset("Theta 放松", "Theta (4-8 Hz)", 160f, 5f, "身心放松"),
            BinauralPreset("Alpha 平静", "Alpha (8-14 Hz)", 200f, 10f, "平静专注"),
            BinauralPreset("Alpha 学习", "Alpha (8-14 Hz)", 210f, 12f, "轻松学习"),
            BinauralPreset("Beta 专注", "Beta (14-30 Hz)", 250f, 20f, "高度集中"),
            BinauralPreset("Beta 活跃", "Beta (14-30 Hz)", 260f, 18f, "思维活跃"),
            BinauralPreset("Gamma 认知", "Gamma (30-50 Hz)", 300f, 40f, "高级认知功能"),
            BinauralPreset("SMR 专注", "SMR (12-15 Hz)", 220f, 13f, "感官运动节律专注")
        )
    }
}

class BinauralBeatEngine {

    private var audioTrack: AudioTrack? = null
    private var generationJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var _isPlaying = false
    val isPlaying: Boolean get() = _isPlaying
    @Volatile var volume: Float = 0.4f

    fun updateVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        audioTrack?.setStereoVolume(volume, volume)
    }

    fun start(baseFreq: Float, beatFreq: Float) {
        stop()
        _isPlaying = true
        generationJob = scope.launch {
            generateTone(baseFreq, beatFreq)
        }
    }

    fun stop() {
        _isPlaying = false
        generationJob?.cancel()
        generationJob = null
        audioTrack?.let {
            it.stop()
            it.release()
        }
        audioTrack = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun generateTone(baseFreq: Float, beatFreq: Float) {
        val sampleRate = 44100
        val leftFreq = baseFreq
        val rightFreq = baseFreq + beatFreq

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 10 * 4) // ~100ms buffer

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        val samples = ShortArray(bufferSize / 2)
        var phaseLeft = 0.0
        var phaseRight = 0.0

        audioTrack?.play()
        audioTrack?.setStereoVolume(volume, volume)

        while (_isPlaying) {
            val incrementLeft = 2.0 * PI * leftFreq / sampleRate
            val incrementRight = 2.0 * PI * rightFreq / sampleRate

            val amp = (Short.MAX_VALUE * volume).toInt()
            for (i in 0 until (samples.size - 1) step 2) {
                samples[i] = (sin(phaseLeft) * amp).toInt().toShort()
                samples[i + 1] = (sin(phaseRight) * amp).toInt().toShort()
                phaseLeft += incrementLeft
                phaseRight += incrementRight
            }
            phaseLeft %= 2.0 * PI
            phaseRight %= 2.0 * PI

            audioTrack?.write(samples, 0, samples.size)
        }
    }
}
