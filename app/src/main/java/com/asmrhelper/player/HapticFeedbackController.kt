package com.asmrhelper.player

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin

@Singleton
class HapticFeedbackController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) { null }
    }

    val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

    private var syncJob: Job? = null

    @Volatile var enabled: Boolean = false
    @Volatile var intensity: Float = 0.5f // 0.0 to 1.0

    /**
     * Start syncing vibration to audio playback.
     * [progressProvider] should return (progressMs, durationMs) or null if not playing.
     */
    fun startSync(progressProvider: suspend () -> Pair<Long, Long>?) {
        stopSync()
        syncJob = scope.launch {
            while (isActive) {
                val state = progressProvider()
                if (state == null) {
                    delay(500L)
                    return@launch
                }
                val (progressMs, durationMs) = state
                if (durationMs <= 0) {
                    delay(500L)
                    continue
                }
                val fraction = progressMs.toFloat() / durationMs
                // Generate rhythmic vibration based on audio position
                val beatPhase = (fraction * 4) % 1f // 4 beats per cycle
                val strength = when {
                    beatPhase < 0.1f -> 0.8f // strong beat
                    beatPhase < 0.3f -> 0.3f // weak echo
                    beatPhase < 0.6f -> 0.15f // subtle
                    else -> 0.05f // near silent
                }
                val actualStrength = (strength * intensity).coerceIn(0f, 1f)

                if (actualStrength > 0.1f && enabled) {
                    vibrate(actualStrength)
                }
                delay(250L)
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun pulseOnce(strength: Float = 0.5f) {
        if (!enabled) return
        vibrate(strength.coerceIn(0f, 1f))
    }

    private fun vibrate(strength: Float) {
        val v = vibrator ?: return
        try {
            val duration = (20 + strength * 80).toLong()
            val amplitude = (strength * 255).toInt().coerceIn(1, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(duration)
            }
        } catch (_: Exception) { }
    }

    fun release() {
        stopSync()
        scope.cancel()
    }
}
