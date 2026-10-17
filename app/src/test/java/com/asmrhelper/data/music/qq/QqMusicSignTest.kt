package com.asmrhelper.data.music.qq

import org.junit.Assert.assertEquals
import org.junit.Test

class QqMusicSignTest {
    @Test
    fun signHelloVector() {
        assertEquals("zzcfa14dde89n1iwax0l5rimr0qwjexceiov4daaee8d6", QqMusicSign.sign("hello"))
    }

    @Test
    fun signJsonVector() {
        assertEquals("zzc7746109xq501htmv4ipz7c8owlxfpihjm1fd695c7", QqMusicSign.sign("""{"a":1}"""))
    }

    @Test
    fun signEmptyVector() {
        assertEquals("zzcf0e03e5gx4qeiq5cfgdyqwu7sdqfsb5fro3aa45053", QqMusicSign.sign(""))
    }
}
