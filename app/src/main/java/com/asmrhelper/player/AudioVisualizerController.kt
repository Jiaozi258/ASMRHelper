package com.asmrhelper.player

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioVisualizerController @Inject constructor() {

    private var visualizer: Visualizer? = null

    private val _fftMagnitudes = MutableStateFlow<FloatArray?>(null)
    val fftMagnitudes: StateFlow<FloatArray?> = _fftMagnitudes.asStateFlow()

    private val _waveformBytes = MutableStateFlow<ByteArray?>(null)
    val waveformBytes: StateFlow<ByteArray?> = _waveformBytes.asStateFlow()

    @Volatile var isActive: Boolean = false
        private set

    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        release()
        try {
            val sizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = sizeRange[1].coerceAtMost(1024) // max FFT size, keep reasonable

            visualizer = Visualizer(sessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (isActive) {
                            _waveformBytes.value = waveform
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (isActive && fft != null) {
                            // Convert FFT byte data to normalized magnitudes
                            val magnitudes = FloatArray(fft.size / 2)
                            for (i in magnitudes.indices) {
                                val real = fft[i * 2].toInt() and 0xFF
                                val imag = fft[i * 2 + 1].toInt() and 0xFF
                                magnitudes[i] = (kotlin.math.sqrt((real * real + imag * imag).toDouble()) / 255.0).toFloat()
                            }
                            _fftMagnitudes.value = magnitudes
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 3, true, true)

                this@apply.enabled = true
            }
            isActive = true
        } catch (e: Exception) {
            android.util.Log.e("AudioVisualizer", "Failed to attach: ${e.message}", e)
            visualizer?.release()
            visualizer = null
            isActive = false
        }
    }

    fun release() {
        isActive = false
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        _fftMagnitudes.value = null
        _waveformBytes.value = null
    }
}
