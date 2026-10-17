package com.asmrhelper.data.music

import com.asmrhelper.domain.model.Audio
import com.asmrhelper.domain.model.music.RemoteLyric
import com.asmrhelper.domain.model.music.RemoteLyricLine
import com.asmrhelper.util.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLyricMapperTest {
    @Test
    fun mapsAndSortsLines() {
        val remote = RemoteLyric(
            lines = listOf(RemoteLyricLine(2000L, "b"), RemoteLyricLine(1000L, "a")),
            rawText = "[00:02.00] b\n[00:01.00] a"
        )
        val lines = remote.toLrcLines()
        assertEquals(listOf(LrcLine(1000L, "a"), LrcLine(2000L, "b")), lines)
    }

    @Test
    fun emptyRemoteYieldsEmpty() {
        assertTrue(RemoteLyric().toLrcLines().isEmpty())
    }

    @Test
    fun isRemoteSongRequiresSourceAndPositiveId() {
        assertTrue(Audio(id = 1L, title = "x", filePath = "u", sourceId = "netease").isRemoteSong())
        assertFalse(Audio(id = 0L, title = "x", filePath = "u", sourceId = "netease").isRemoteSong())
        assertFalse(Audio(id = 1L, title = "x", filePath = "u", sourceId = "").isRemoteSong())
    }

    @Test
    fun isRemoteSongTrueForQqStringMid() {
        assertTrue(Audio(id = 0L, title = "x", filePath = "u", sourceId = "qq", sourceSongId = "0035GveV3i9dBM").isRemoteSong())
    }

    @Test
    fun isRemoteSongFalseForLocal() {
        assertFalse(Audio(id = 0L, title = "x", filePath = "u").isRemoteSong())
    }
}
