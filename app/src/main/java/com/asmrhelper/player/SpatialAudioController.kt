package com.asmrhelper.player

import android.media.audiofx.Virtualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

enum class SpatialMode(val label: String) {
    OFF("关闭"),
    D3("3D立体"),
    SWEEP("左右扫掠"),
    CIRCLE("环绕旋转"),
    WIDE("空间扩展")
}

@Singleton
class SpatialAudioController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var virtualizer: Virtualizer? = null
    private var animJob: Job? = null

    /** 环绕动画速度倍率（0.5~3.0） */
    @Volatile private var surroundSpeed: Float = 1f

    /** 左右平衡 (-1=全左, 0=居中, 1=全右) —— 通过 Virtualizer 强度近似 */
    private val _balance = MutableStateFlow(0f)
    val balanceValue: StateFlow<Float> = _balance.asStateFlow()

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
            SpatialMode.D3 -> {
                virtualizer?.apply {
                    enabled = true
                    setStrength(300.toShort())
                }
                isActive = true
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

    /** 设置左右平衡 (-1.0 = 全左, 0 = 居中, 1.0 = 全右)。
     *  通过 Virtualizer 强度近似（Android 无独立 Balance 类）。 */
    fun setBalance(value: Float) {
        _balance.value = value.coerceIn(-1f, 1f)
        // 用 Virtualizer 强度变化近似声场偏移
        try {
            val strength = (400 + _balance.value * 300).toInt().coerceIn(0, 1000)
            virtualizer?.setStrength(strength.toShort())
        } catch (_: Exception) { }
    }

    /** 设置声源距离（Virtualizer 强度 0~1000） */
    fun setDistance(strength: Int) {
        try { virtualizer?.setStrength(strength.coerceIn(0, 1000).toShort()) } catch (_: Exception) { }
    }

    /** 设置环绕速度倍率 */
    fun setSurroundSpeed(speed: Float) {
        surroundSpeed = speed.coerceIn(0.5f, 3f)
        // 若正在环绕动画，重启以应用新速度
        if (currentMode == SpatialMode.CIRCLE && isActive) {
            animJob?.cancel()
            startPanAnimation(true)
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
                t += 0.05f * surroundSpeed
                delay((30L / surroundSpeed).toLong().coerceAtLeast(10L))
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
