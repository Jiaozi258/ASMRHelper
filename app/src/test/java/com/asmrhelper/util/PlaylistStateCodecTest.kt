package com.asmrhelper.util

import com.asmrhelper.domain.model.Audio
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistStateCodecTest {
    @Test
    fun roundTripsCoverAndSource() {
        val audio = Audio(id = 123, title = "晴天", artist = "周杰伦",
            filePath = "https://x/1.mp3", durationMs = 269000L,
            coverUrl = "https://c/1.jpg", sourceId = "netease")
        val decoded = PlaylistStateCodec.decode(PlaylistStateCodec.encode(listOf(audio))).single()
        assertEquals(audio.id, decoded.id)
        assertEquals(audio.title, decoded.title)
        assertEquals(audio.artist, decoded.artist)
        assertEquals(audio.filePath, decoded.filePath)
        assertEquals(audio.durationMs, decoded.durationMs)
        assertEquals(audio.isFavorite, decoded.isFavorite)
        assertEquals(audio.coverUrl, decoded.coverUrl)
        assertEquals(audio.sourceId, decoded.sourceId)
    }

    @Test
    fun decodeEmptyAndMalformedReturnEmpty() {
        assertEquals(emptyList<Audio>(), PlaylistStateCodec.decode(""))
        assertEquals(emptyList<Audio>(), PlaylistStateCodec.decode("not json"))
        assertEquals("[]", PlaylistStateCodec.encode(emptyList()))
    }
}
