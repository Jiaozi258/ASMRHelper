package com.asmrhelper.data.music.qq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqMusicJsonTest {

    @Test
    fun parseSearchMapsSong() {
        val json = """
            {"code":0,"req_0":{"code":0,"data":{"body":{"item_song":{"items":[
              {"mid":"0035GveV3i9dBM","title":"小苹果","name":"小苹果",
               "singer":[{"name":"筷子兄弟"}],
               "album":{"mid":"000owywt4caGcV","name":"老男孩之猛龙过江"},
               "interval":213}
            ]}}}}}
        """.trimIndent()
        val list = QqMusicJson.parseSearch(json)
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("0035GveV3i9dBM", s.id)
        assertEquals("小苹果", s.title)
        assertEquals("筷子兄弟", s.artist)
        assertEquals("老男孩之猛龙过江", s.album)
        assertEquals(213000L, s.durationMs)
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000000owywt4caGcV.jpg", s.coverUrl)
        assertEquals("qq", s.sourceId)
    }

    @Test
    fun parseSearchJoinsSingersAndEmptyOnMalformed() {
        val json = """{"req_0":{"data":{"body":{"item_song":{"items":[
            {"mid":"m1","title":"x","singer":[{"name":"A"},{"name":"B"}]}
        ]}}}}}"""
        assertEquals("A / B", QqMusicJson.parseSearch(json)[0].artist)
        assertTrue(QqMusicJson.parseSearch("not json").isEmpty())
        assertTrue(QqMusicJson.parseSearch("""{"req_0":{"data":{}}}""").isEmpty())
    }

    @Test
    fun parseSongUrlJoinsSipAndPurl() {
        val json = """{"req_0":{"data":{"sip":["http://aqqmusic.tc.qq.com/"],
            "midurlinfo":[{"result":0,"purl":"M500abc.mp3?guid=10000&vkey=K"}]}}}"""
        assertEquals("https://aqqmusic.tc.qq.com/M500abc.mp3?guid=10000&vkey=K", QqMusicJson.parseSongUrl(json))
    }

    @Test
    fun parseSongUrlEmptyOnVipOrMalformed() {
        val vip = """{"req_0":{"data":{"sip":["http://aqqmusic.tc.qq.com/"],"midurlinfo":[{"result":104003,"purl":""}]}}}"""
        assertEquals("", QqMusicJson.parseSongUrl(vip))
        assertEquals("", QqMusicJson.parseSongUrl("not json"))
    }

    @Test
    fun parseLyricBase64DecodesAndParsesTimes() {
        val b64 = "W3RpOuWwj+iLueaenF0KWzAwOjAwLjAwXeesrOS4gOWPpQpbMDA6MTAuNTBd56ys5LqM5Y+lCg=="
        val json = """{"req_0":{"data":{"lyric":"$b64"}}}"""
        val lyric = QqMusicJson.parseLyric(json)
        assertEquals(2, lyric.lines.size)
        assertEquals(0L, lyric.lines[0].timeMs)
        assertEquals("第一句", lyric.lines[0].text)
        assertEquals(10500L, lyric.lines[1].timeMs)
        assertEquals("第二句", lyric.lines[1].text)
    }

    @Test
    fun parseLyricEmptyOnMalformed() {
        assertTrue(QqMusicJson.parseLyric("not json").lines.isEmpty())
        assertTrue(QqMusicJson.parseLyric("""{"req_0":{"data":{"lyric":"!!!"}}}""").lines.isEmpty())
    }

    @Test
    fun parseChartsMapsToplists() {
        val json = """{"code":0,"req_0":{"code":0,"data":{"group":[
            {"groupName":"巅峰榜","toplist":[
              {"topId":26,"title":"热歌榜","songList":[
                {"mid":"0035GveV3i9dBM","name":"小苹果","singer":[{"name":"筷子兄弟"}],
                 "album":{"mid":"000owywt4caGcV","name":"老男孩"},"interval":213}
              ]}
            ]}
        ]}}}"""
        val charts = QqMusicJson.parseCharts(json)
        assertEquals(1, charts.size)
        assertEquals("26", charts[0].id)
        assertEquals("热歌榜", charts[0].name)
        assertEquals(1, charts[0].songs.size)
        assertEquals("0035GveV3i9dBM", charts[0].songs[0].id)
    }

    @Test
    fun parseChartsEmptyOnMalformed() {
        assertTrue(QqMusicJson.parseCharts("not json").isEmpty())
        assertTrue(QqMusicJson.parseCharts("""{"req_0":{"data":{}}}""").isEmpty())
    }

    @Test
    fun parseRecommendationsAndUin() {
        val json = """{"req_0":{"data":{"songlist":[
            {"mid":"m1","name":"歌","singer":[{"name":"A"}],"album":{"mid":"am","name":"专"},"interval":200}
        ]}}}"""
        assertEquals(1, QqMusicJson.parseRecommendations(json).size)
        assertEquals("", QqMusicJson.uin(""))
        assertEquals("123456", QqMusicJson.uin("uin=o123456; qm_keyst=K"))
        assertEquals("", QqMusicJson.uin("qm_keyst=K"))
    }
}
