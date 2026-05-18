package com.asmrhelper.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

enum class NoiseType(val label: String) {
    WHITE("白噪音"),
    PINK("粉红噪音"),
    BROWN("棕色噪音")
}

@Singleton
class NoiseGenerator @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var track: AudioTrack? = null
    private var generateJob: Job? = null

    @Volatile var isPlaying: Boolean = false
        private set

    @Volatile var currentType: NoiseType = NoiseType.WHITE
        private set

    @Volatile var volume: Float = 0.3f

    fun updateVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        track?.setStereoVolume(volume, volume)
    }

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    ).let { if (it <= 0) sampleRate * 2 * 2 / 10 else it } // fallback: ~100ms stereo 16-bit

    // Pink noise state: 7-pole filter approximation (Voss-McCartney)
    private val pinkState = FloatArray(7) { 0f }

    // Brown noise state
    private var brownState = 0f

    fun start(type: NoiseType = NoiseType.WHITE) {
        stop()
        currentType = type
        // resetState() is called inside fill loop for thread safety — not here

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        this.track = track
        track.play()
        track.setStereoVolume(volume, volume)
        isPlaying = true

        generateJob = scope.launch {
            resetState()
            val buffer = ShortArray(bufferSize / 2)
            while (isActive) {
                fillBuffer(buffer, type)
                track.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        generateJob?.cancel()
        generateJob = null
        track?.apply {
            pause()
            flush()
            release()
        }
        track = null
        isPlaying = false
    }

    private fun fillBuffer(buffer: ShortArray, type: NoiseType) {
        when (type) {
            NoiseType.WHITE -> fillWhiteNoise(buffer)
            NoiseType.PINK -> fillPinkNoise(buffer)
            NoiseType.BROWN -> fillBrownNoise(buffer)
        }
    }

    private fun fillWhiteNoise(buffer: ShortArray) {
        for (i in buffer.indices) {
            buffer[i] = (Random.nextFloat() * 2f - 1f).times(16384f).toInt().coerceIn(-16384, 16383).toShort()
        }
    }

    private fun fillPinkNoise(buffer: ShortArray) {
        for (i in buffer.indices) {
            // Voss-McCartney simplified pink noise
            val idx = Random.nextInt(7)
            pinkState[idx] = (Random.nextFloat() * 2f - 1f)
            var sum = 0f
            for (s in pinkState) sum += s
            val sample = sum / 3.5f // normalize
            buffer[i] = (sample * 16384f).toInt().coerceIn(-16384, 16383).toShort()
        }
    }

    private fun fillBrownNoise(buffer: ShortArray) {
        for (i in buffer.indices) {
            val white = (Random.nextFloat() * 2f - 1f) * 0.1f
            brownState = (brownState + white).coerceIn(-1f, 1f) * 0.95f
            buffer[i] = (brownState * 16384f).toInt().coerceIn(-16384, 16383).toShort()
        }
    }

    private fun resetState() {
        for (j in pinkState.indices) pinkState[j] = 0f
        brownState = 0f
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
