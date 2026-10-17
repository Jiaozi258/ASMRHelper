package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimingTest {
    private val lines = listOf(LrcLine(1000L, "a"), LrcLine(2000L, "b"), LrcLine(3000L, "c"))

    @Test
    fun currentLineIndexFindsLastLineAtOrBeforeProgress() {
        assertEquals(1, lines.currentLineIndex(2500L))
        assertEquals(2, lines.currentLineIndex(9999L))
        assertEquals(0, lines.currentLineIndex(500L))
    }

    @Test
    fun jumpSnapsToCurrent() {
        assertEquals(5, scrollAnchorIndex(5, LyricMode.JUMP, 10))
    }

    @Test
    fun scrollCentersCurrentAndClamps() {
        assertEquals(3, scrollAnchorIndex(5, LyricMode.SCROLL, 4))
        assertEquals(0, scrollAnchorIndex(1, LyricMode.SCROLL, 10))
    }
}
