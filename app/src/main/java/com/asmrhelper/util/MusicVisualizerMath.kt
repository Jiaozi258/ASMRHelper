package com.asmrhelper.util

import kotlin.math.abs

/** 音频可视化柱状图归一化：把原始波形/FFT 数据降采样为 0..1 的柱高。 */
object MusicVisualizerMath {
    fun barsFromWaveform(bytes: ByteArray?, barCount: Int): FloatArray {
        val bars = FloatArray(barCount.coerceAtLeast(0))
        if (bytes == null || bytes.isEmpty() || barCount <= 0) return bars
        val perBar = bytes.size / barCount
        for (i in 0 until barCount) {
            val start = i * perBar
            val end = if (i == barCount - 1) bytes.size else start + perBar
            var sum = 0f
            for (j in start until end) {
                sum += abs((bytes[j].toInt() and 0xFF) - 128) / 128f
            }
            bars[i] = if (end > start) sum / (end - start) else 0f
        }
        return bars
    }

    fun barsFromFft(magnitudes: FloatArray?, barCount: Int): FloatArray {
        val bars = FloatArray(barCount.coerceAtLeast(0))
        if (magnitudes == null || magnitudes.isEmpty() || barCount <= 0) return bars
        val perBar = magnitudes.size / barCount
        for (i in 0 until barCount) {
            val start = i * perBar
            val end = if (i == barCount - 1) magnitudes.size else start + perBar
            var sum = 0f
            for (j in start until end) sum += magnitudes[j].coerceIn(0f, 1f)
            bars[i] = if (end > start) sum / (end - start) else 0f
        }
        return bars
    }
}
