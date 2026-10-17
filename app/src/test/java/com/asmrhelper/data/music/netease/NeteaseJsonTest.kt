package com.asmrhelper.data.music.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseJsonTest {

    // ── 搜索 ────────────────────────────────────────────

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

    // ── 播放地址 ────────────────────────────────────────

    @Test
    fun parseSongUrlExtractsUrl() {
        val json = """{"data":[{"id":1,"url":"https://x/y.mp3"}]}"""
        assertEquals("https://x/y.mp3", NeteaseJson.parseSongUrl(json))
    }

    @Test
    fun parseSongUrlEmptyOnMissing() {
        assertEquals("", NeteaseJson.parseSongUrl("""{"data":[]}"""))
        assertEquals("", NeteaseJson.parseSongUrl("not json"))
    }

    // ── 歌词 ────────────────────────────────────────────

    @Test
    fun parseLyricParsesTimestamps() {
        val json = """{"lrc":{"lyric":"[00:00.00]第一句\n[00:10.50]第二句\n"}}"""
        val lyric = NeteaseJson.parseLyric(json)
        assertEquals(2, lyric.lines.size)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals("第一句", lyric.lines[0].text)
        assertEquals(10500L, lyric.lines[1].timeMs)
        assertEquals("第二句", lyric.lines[1].text)
    }

    @Test
    fun parseLyricNoTimestampReturnsEmptyLines() {
        val json = """{"lrc":{"lyric":"纯文本没有时间戳"}}"""
        assertTrue(NeteaseJson.parseLyric(json).lines.isEmpty())
    }

    @Test
    fun parseLyricEmptyOnMalformed() {
        assertTrue(NeteaseJson.parseLyric("not json").lines.isEmpty())
    }

    // ── 榜单 ────────────────────────────────────────────

    @Test
    fun parseChartsMapsList() {
        val json = """{"list":[{"id":19723756,"name":"飙升榜","tracks":[{"id":1,"name":"x","ar":[{"name":"A"}],"al":{"name":"al","picUrl":"http://c/1.jpg"},"dt":269000}]}]}"""
        val charts = NeteaseJson.parseCharts(json)
        assertEquals(1, charts.size)
        assertEquals("19723756", charts[0].id)
        assertEquals("飙升榜", charts[0].name)
        assertEquals(1, charts[0].songs.size)
        assertEquals("x", charts[0].songs[0].title)
    }

    @Test
    fun parseChartsEmptyOnMalformed() {
        assertTrue(NeteaseJson.parseCharts("not json").isEmpty())
    }

    // ── 推荐 ────────────────────────────────────────────

    @Test
    fun parseRecommendMapsResult() {
        val json = """{"result":[{"id":347230,"name":"新歌","song":{"id":347230,"name":"新歌","artists":[{"name":"歌手"}],"album":{"name":"专辑","picUrl":"http://c/1.jpg"},"duration":269000}}]}"""
        val songs = NeteaseJson.parseRecommend(json)
        assertEquals(1, songs.size)
        assertEquals("347230", songs[0].id)
        assertEquals("新歌", songs[0].title)
        assertEquals("歌手", songs[0].artist)
        assertEquals("专辑", songs[0].album)
        assertEquals(269000L, songs[0].durationMs)
        assertEquals("http://c/1.jpg", songs[0].coverUrl)
        assertEquals("netease", songs[0].sourceId)
    }

    @Test
    fun parseRecommendEmptyOnMalformed() {
        assertTrue(NeteaseJson.parseRecommend("not json").isEmpty())
    }
}
