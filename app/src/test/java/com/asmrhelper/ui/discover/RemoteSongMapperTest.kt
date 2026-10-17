package com.asmrhelper.ui.discover

import com.asmrhelper.domain.model.music.RemoteSong
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSongMapperTest {
    @Test
    fun mapsToAudioWithUrlAsFilePath() {
        val audio = RemoteSong("1", "歌", "人", durationMs = 3000L, sourceId = "netease")
            .toAudio("https://s/1.mp3")
        assertEquals("歌", audio.title)
        assertEquals("https://s/1.mp3", audio.filePath)
        assertEquals(3000L, audio.durationMs)
    }

    @Test
    fun mapsCoverAndSourceId() {
        val audio = RemoteSong("42", "歌", "人", coverUrl = "https://c/1.jpg", sourceId = "netease")
            .toAudio("https://s/1.mp3")
        assertEquals("https://c/1.jpg", audio.coverUrl)
        assertEquals("netease", audio.sourceId)
        assertEquals(42L, audio.id)
    }

    @Test
    fun mapsStringMidToSourceSongId() {
        val audio = RemoteSong("0035GveV3i9dBM", "小苹果", "人", sourceId = "qq").toAudio("https://s/1.mp3")
        assertEquals(0L, audio.id)
        assertEquals("0035GveV3i9dBM", audio.sourceSongId)
    }
}
