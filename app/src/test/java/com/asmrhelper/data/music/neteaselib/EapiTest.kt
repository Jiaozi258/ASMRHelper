package com.asmrhelper.data.music.neteaselib

import org.junit.Assert.assertEquals
import org.junit.Test

class EapiTest {

    /** 与公开 NeteaseCloudMusicApi 的 eapi 实现对齐（用 Node 参考实现生成的向量）。 */
    @Test
    fun encryptMatchesReferenceVector() {
        val url = "/api/song/enhance/player/url/v1"
        val body = """{"ids":"[33894312]","level":"exhigh","encodeType":"mp3"}"""
        val expected = "FA90B329E9614F79E79598F37DC2EDB487F00D1BC4C9B24CD57E6C318B907356" +
            "9338432CD7D98D1A3626E997A2C531219F7DD8FF31B2E259DBCA22F31A11628DD8BAA052F1854D97" +
            "3C4F33312C905A8FDF5733349487145214CE210535112FD1BE0D767371A33090741A624AA969FC4" +
            "0CCDAB89F36265630A15A143F6D2BDD80DB1E447315B31C334E89550FCFE17AD1EF2217DB1EEDB6B" +
            "6FAFC7FD09A358F66"
        assertEquals(expected, Eapi.encrypt(url, body))
    }

    @Test
    fun md5IsLowercaseHex() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", Eapi.md5("hello"))
    }
}
