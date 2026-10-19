package com.asmrhelper.data.music.neteaselib

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class X25519Test {

    // RFC 7748 §5.2 官方测试向量。
    private val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
    private val alicePub = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
    private val bobPub = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
    private val shared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")

    @Test
    fun scalarMultBaseMatchesRfcVector() {
        assertArrayEquals(alicePub, X25519.scalarMultBase(alicePriv))
    }

    @Test
    fun scalarMultMatchesRfcSharedSecret() {
        assertArrayEquals(shared, X25519.scalarMult(alicePriv, bobPub))
    }

    @Test
    fun clampMatchesRfcDecode() {
        val clamped = X25519.clamp(alicePriv)
        // 低位 3 位置 0，最高位置 0，第 254 位置 1
        assertEquals(0, clamped[0].toInt() and 0x07)
        assertEquals(0, clamped[31].toInt() and 0x80)
        assertEquals(0x40, clamped[31].toInt() and 0x40)
    }

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected, actual)

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
