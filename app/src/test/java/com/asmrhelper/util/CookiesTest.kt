package com.asmrhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CookiesTest {

    @Test
    fun selectNullIsEmpty() {
        assertEquals("", Cookies.select(null, setOf("MUSIC_U")))
    }

    @Test
    fun selectBlankIsEmpty() {
        assertEquals("", Cookies.select("  ", setOf("MUSIC_U")))
    }

    @Test
    fun selectKeepsOnlyWantedPairs() {
        assertEquals(
            "MUSIC_U=a; __csrf=b",
            Cookies.select("MUSIC_U=a; __csrf=b; NMTID=c; MUSIC_A=d", setOf("MUSIC_U", "__csrf"))
        )
    }

    @Test
    fun selectStripsCookieAttributes() {
        assertEquals(
            "MUSIC_A=x",
            Cookies.select("MUSIC_A=x; Max-Age=2147483647; Path=/", setOf("MUSIC_A"))
        )
    }
}
