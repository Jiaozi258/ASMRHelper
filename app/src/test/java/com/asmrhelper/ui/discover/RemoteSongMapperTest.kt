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
}
