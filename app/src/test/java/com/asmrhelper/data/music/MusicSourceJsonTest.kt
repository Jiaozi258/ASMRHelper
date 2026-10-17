package com.asmrhelper.data.music

import com.asmrhelper.domain.music.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSourceJsonTest {
    @Test
    fun sourcesRoundTrip() {
        val list = listOf(
            SourceConfig("netease", "网易云音乐", "https://a"),
            SourceConfig("c", "自定义", "http://b", isCustom = true)
        )
        val json = MusicSourceJson.sourcesToJson(list)
        assertEquals(list, MusicSourceJson.sourcesFromJson(json))
    }

    @Test
    fun sourcesFromJsonEmptyOnGarbage() {
        assertTrue(MusicSourceJson.sourcesFromJson("not json").isEmpty())
    }
}
