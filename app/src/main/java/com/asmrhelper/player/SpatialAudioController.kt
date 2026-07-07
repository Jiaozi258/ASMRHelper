package com.asmrhelper.player

import android.media.audiofx.Virtualizer
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
import kotlin.math.sin

enum class SpatialMode(val label: String) {
    OFF("关闭"),
    SWEEP("左右扫掠"),
    CIRCLE("环绕旋转"),
    WIDE("空间扩展")
}

@Singleton
class SpatialAudioController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var virtualizer: Virtualizer? = null
    private var animJob: Job? = null

    @Volatile var currentMode: SpatialMode = SpatialMode.OFF
        private set

    @Volatile var isActive: Boolean = false
        private set

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(300.toShort())
            }
        } catch (_: Exception) {
            // Audio effects not supported on this device
        }
    }

    fun setMode(mode: SpatialMode) {
        currentMode = mode
        animJob?.cancel()

        when (mode) {
            SpatialMode.OFF -> {
                virtualizer?.enabled = false
                isActive = false
            }
            SpatialMode.WIDE -> {
                virtualizer?.apply {
                    enabled = true
                    setStrength(800.toShort())
                }
                isActive = true
            }
            SpatialMode.SWEEP, SpatialMode.CIRCLE -> {
                virtualizer?.apply {
                    enabled = true
                    setStrength(400.toShort())
                }
                isActive = true
                startPanAnimation(mode == SpatialMode.CIRCLE)
            }
        }
    }

    private fun startPanAnimation(circular: Boolean) {
        animJob = scope.launch {
            var t = 0f
            while (isActive) {
                if (circular) {
                    val depth = (sin(t * 1.7f) * 0.5f + 0.5f).toFloat()
                    virtualizer?.setStrength(
                        (400 + depth * 500).toInt().coerceIn(0, 1000).toShort()
                    )
                }
                t += 0.05f
                delay(30L)
            }
        }
    }

    fun release() {
        animJob?.cancel()
        animJob = null
        scope.cancel()
        virtualizer?.apply { enabled = false; release() }
        virtualizer = null
        isActive = false
        currentMode = SpatialMode.OFF
    }
}
