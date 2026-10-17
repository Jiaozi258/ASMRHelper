package com.asmrhelper.data.music

import com.asmrhelper.domain.music.SourceConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpMusicSourceTest {

    private val src = HttpMusicSource(
        SourceConfig("t", "测试", "https://api.example.com"),
        OkHttpClient(),
        onLoginChanged = {}
    )

    @Test
    fun buildsSearchUrlWithQuery() {
        assertEquals(
            "https://api.example.com/search?keyword=%E6%B5%8B%E8%AF%95&page=1",
            src.buildSearchUrl("测试", 1)
        )
    }

    @Test
    fun buildsSongUrl() {
        assertEquals("https://api.example.com/song/url?id=42", src.buildSongUrl("42"))
    }

    @Test
    fun blankBaseUrlSearchReturnsEmptyFlow() = runBlocking {
        val blank = HttpMusicSource(SourceConfig("b", "空", ""), OkHttpClient(), onLoginChanged = {})
        assertTrue(blank.search("x", 1).first().isEmpty())
    }
}
