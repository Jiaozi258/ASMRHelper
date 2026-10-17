package com.asmrhelper.data.music.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonTest {

    @Test
    fun parseSearchSongs() {
        val json = """{"code":200,"result":{"songs":[{"id":123456,"name":"晴天","ar":[{"name":"周杰伦"}],"al":{"name":"叶惠美","picUrl":"http://c/1.jpg"},"dt":269000}]}}"""
        val list = NeteaseJson.parseSearch(json)
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("123456", s.id)
        assertEquals("晴天", s.title)
        assertEquals("周杰伦", s.artist)
        assertEquals("叶惠美", s.album)
        assertEquals(269000L, s.durationMs)
        assertEquals("http://c/1.jpg", s.coverUrl)
        assertEquals("netease", s.sourceId)
    }

    @Test
    fun parseSearchJoinsMultipleArtists() {
        val json = """{"result":{"songs":[{"id":1,"name":"x","ar":[{"name":"A"},{"name":"B"}]}]}}"""
        assertEquals("A / B", NeteaseJson.parseSearch(json)[0].artist)
    }

    @Test
    fun parseSearchEmptyOnMalformed() {
        assertTrue(NeteaseJson.parseSearch("not json").isEmpty())
    }
}
