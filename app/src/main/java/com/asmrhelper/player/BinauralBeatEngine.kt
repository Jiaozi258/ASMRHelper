package com.asmrhelper.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.annotation.StringRes
import com.asmrhelper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class BinauralPreset(
    val id: String,
    @StringRes val nameRes: Int,
    val category: String,
    val baseFrequency: Float,
    val beatFrequency: Float,
    @StringRes val descriptionRes: Int
) {
    companion object {
        val PRESETS = listOf(
            BinauralPreset("delta_deep", R.string.binaural_name_delta_deep, "Delta (1-4 Hz)", 100f, 3f, R.string.binaural_desc_delta_deep),
            BinauralPreset("delta_sleep", R.string.binaural_name_delta_sleep, "Delta (1-4 Hz)", 120f, 2f, R.string.binaural_desc_delta_sleep),
            BinauralPreset("theta_meditation", R.string.binaural_name_theta_meditation, "Theta (4-8 Hz)", 150f, 6f, R.string.binaural_desc_theta_meditation),
            BinauralPreset("theta_relax", R.string.binaural_name_theta_relax, "Theta (4-8 Hz)", 160f, 5f, R.string.binaural_desc_theta_relax),
            BinauralPreset("alpha_calm", R.string.binaural_name_alpha_calm, "Alpha (8-14 Hz)", 200f, 10f, R.string.binaural_desc_alpha_calm),
            BinauralPreset("alpha_study", R.string.binaural_name_alpha_study, "Alpha (8-14 Hz)", 210f, 12f, R.string.binaural_desc_alpha_study),
            BinauralPreset("beta_focus", R.string.binaural_name_beta_focus, "Beta (14-30 Hz)", 250f, 20f, R.string.binaural_desc_beta_focus),
            BinauralPreset("beta_active", R.string.binaural_name_beta_active, "Beta (14-30 Hz)", 260f, 18f, R.string.binaural_desc_beta_active),
            BinauralPreset("gamma_cognition", R.string.binaural_name_gamma_cognition, "Gamma (30-50 Hz)", 300f, 40f, R.string.binaural_desc_gamma_cognition),
            BinauralPreset("smr_focus", R.string.binaural_name_smr_focus, "SMR (12-15 Hz)", 220f, 13f, R.string.binaural_desc_smr_focus)
        )
    }
}

class BinauralBeatEngine {

    @Volatile private var audioTrack: AudioTrack? = null
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
        // Unblock a blocked write(); the generation coroutine releases the
        // track in its finally block after write() returns.
        try { audioTrack?.pause() } catch (_: Exception) { }
        try { audioTrack?.flush() } catch (_: Exception) { }
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

        val track = AudioTrack(
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
        audioTrack = track

        // stop() may have been called before the track was created — bail out
        // to avoid a brief audible blip.
        if (!_isPlaying) {
            try { track.release() } catch (_: Exception) { }
            audioTrack = null
            return
        }

        val samples = ShortArray(bufferSize / 2)
        var phaseLeft = 0.0
        var phaseRight = 0.0

        try {
            track.play()
            track.setStereoVolume(volume, volume)

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

                track.write(samples, 0, samples.size)
            }
        } catch (_: Exception) {
            // write() may fail after the track is externally stopped.
        } finally {
            // Release on the generation thread after write() returns — never
            // during a blocked native write().
            try { track.release() } catch (_: Exception) { }
            if (audioTrack === track) {
                audioTrack = null
            }
        }
    }
}
