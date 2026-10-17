package com.asmrhelper.data.music.neteaselib

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeapiTest {

    @Test
    fun md5LowerHex() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", Weapi.md5("hello"))
    }

    @Test
    fun aesCbcPkcs5MatchesNistVector() {
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hex("000102030405060708090a0b0c0d0e0f")
        val plain = hex("6bc1bee22e409f96e93d7e117393172a")
        val out = Weapi.aesEncrypt(plain, key, iv)
        // NIST SP800-38A F.2.1：首块密文；16 字节明文 PKCS5 补成 32 字节（追加一整块填充）
        assertEquals("7649abac8119b246cee98e9b12e9197d", toHex(out.copyOfRange(0, 16)))
        assertEquals(32, out.size)
    }

    @Test
    fun encryptProducesBase64ParamsAnd256HexKey() {
        val form = Weapi.encrypt("""{"a":1}""")
        val decoded = Base64.getDecoder().decode(form.params)
        assertTrue(decoded.isNotEmpty())
        assertEquals(256, form.encSecKey.length)
        assertTrue(form.encSecKey.all { it in "0123456789abcdef" })
    }

    @Test
    fun randomSecKeyIs16Base62() {
        val k = Weapi.randomSecKey()
        assertEquals(16, k.length)
        assertTrue(k.all { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" })
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
