package com.asmrhelper.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicJsonAdaptersTest {
    @Test
    fun parsesSearchList() {
        val list = MusicJsonAdapters.parseSearch(
            """{"code":0,"data":[{"id":"1","title":"歌","artist":"人","duration":123000,"cover":"http://c"}]}"""
        )
        assertEquals(1, list.size)
        assertEquals("1", list[0].id)
        assertEquals(123000L, list[0].durationMs)
    }

    @Test
    fun searchNonZeroCodeReturnsEmpty() {
        assertTrue(MusicJsonAdapters.parseSearch("""{"code":502,"data":null}""").isEmpty())
    }

    @Test
    fun searchMalformedReturnsEmpty() {
        assertTrue(MusicJsonAdapters.parseSearch("not json").isEmpty())
    }

    @Test
    fun parsesSongUrl() {
        assertEquals(
            "https://s/1.mp3",
            MusicJsonAdapters.parseSongUrl("""{"code":0,"data":{"url":"https://s/1.mp3"}}""")
        )
    }

    @Test
    fun songUrlEmptyReturnsEmpty() {
        assertEquals("", MusicJsonAdapters.parseSongUrl("""{"code":0,"data":{"url":""}}"""))
    }
}
