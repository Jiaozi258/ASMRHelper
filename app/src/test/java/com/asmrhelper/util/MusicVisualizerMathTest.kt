package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicVisualizerMathTest {
    @Test
    fun nullWaveformReturnsZeros() {
        val bars = MusicVisualizerMath.barsFromWaveform(null, 4)
        assertEquals(4, bars.size)
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun bucketsAndAveragesWaveform() {
        val bars = MusicVisualizerMath.barsFromWaveform(byteArrayOf(-128, -128, 0, 0), 2)
        assertEquals(2, bars.size)
        assertEquals(0f, bars[0], 1e-6f)
        assertEquals(1f, bars[1], 1e-6f)
    }

    @Test
    fun zeroBarsReturnsEmpty() {
        assertEquals(0, MusicVisualizerMath.barsFromWaveform(byteArrayOf(1, 2, 3), 0).size)
    }

    @Test
    fun bucketsAndAveragesFft() {
        val bars = MusicVisualizerMath.barsFromFft(floatArrayOf(0f, 1f, 0f, 1f), 2)
        assertEquals(0.5f, bars[0], 1e-6f)
        assertEquals(0.5f, bars[1], 1e-6f)
    }
}
