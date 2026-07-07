package com.asmrhelper.player

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.di.MainPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class EqualizerController @Inject constructor(
    @MainPlayer private val mainPlayer: ExoPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var equalizer: Equalizer? = null

    private val _bandLevels = MutableStateFlow(listOf(0f, 0f, 0f))
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    /** Human-readable band names. */
    val bandLabels = listOf("低音", "中音", "高音")

    /** The actual Equalizer band indices we're using. */
    private val actualBandIndices = mutableListOf<Short>()

    init {
        scope.launch {
            var attempts = 0
            while (attempts < 20) {
                val sessionId = mainPlayer.audioSessionId
                if (sessionId > 0) {
                    try {
                        val eq = Equalizer(0, sessionId).apply { enabled = true }
                        val numBands = eq.numberOfBands.toInt()

                        // Select 3 bands spread across the spectrum
                        if (numBands >= 3) {
                            // Use bands near 1/4, 1/2, and 3/4 of the range
                            val targets = listOf(
                                (numBands * 0.2).toInt(),
                                (numBands * 0.5).toInt(),
                                (numBands * 0.8).toInt()
                            )
                            for (t in targets) {
                                val band = t.toShort().coerceIn(0, (numBands - 1).toShort())
                                if (band !in actualBandIndices) {
                                    actualBandIndices.add(band)
                                    eq.setBandLevel(band, 0)
                                }
                            }
                        } else {
                            // Just use whatever bands are available
                            for (b in 0 until numBands) {
                                actualBandIndices.add(b.toShort())
                                eq.setBandLevel(b.toShort(), 0)
                            }
                        }
                        equalizer = eq
                        _isEnabled.value = true
                        break
                    } catch (_: Exception) {
                        _isEnabled.value = false
                    }
                }
                attempts++
                delay(500L)
            }
        }
    }

    fun setBandLevel(band: Int, levelDb: Float) {
        val clamped = levelDb.coerceIn(-10f, 10f)
        val newLevels = _bandLevels.value.toMutableList()
        if (band in newLevels.indices) {
            newLevels[band] = clamped
            _bandLevels.value = newLevels
        }
        try {
            val eq = equalizer ?: return
            if (band < actualBandIndices.size) {
                eq.setBandLevel(actualBandIndices[band], (clamped * 100).toInt().toShort())
            }
        } catch (_: Exception) { }
    }

    fun reset() {
        val eq = equalizer ?: return
        for (i in actualBandIndices.indices) {
            try { eq.setBandLevel(actualBandIndices[i], 0) } catch (_: Exception) { }
        }
        _bandLevels.value = listOf(0f, 0f, 0f)
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) { }
        equalizer = null
        _isEnabled.value = false
    }
}
