package com.asmrhelper.data.music.neteaselib

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class XeapiTest {

    private val staticKey = hex("ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84")

    @Test
    fun aesEcbEncryptMatchesVector() {
        // 32 字节 STATIC 密钥 → AES-256-ECB
        val out = Xeapi.aesEcbEncrypt(staticKey, "hello world 123".toByteArray(StandardCharsets.UTF_8))
        assertEquals("eeef3d6b8b11da0a39ef541921776722", Xeapi.toHex(out))
    }

    @Test
    fun aesEcbEncryptDynamicKeySize() {
        // 16 字节密钥 → AES-128-ECB（匿名流程 dynamicKey 为 16 字节随机数）
        val out = Xeapi.aesEcbEncrypt(ByteArray(16) { 0x11 }, "hi".toByteArray(StandardCharsets.UTF_8))
        assertEquals(16, out.size)
    }

    @Test
    fun deriveAesKeyMatchesVector() {
        val ss = ByteArray(32) { 0x11 }
        val epk = ByteArray(32) { 0x22 }
        assertEquals("a9c5e3543077b063464f5343fc1afb06", Xeapi.toHex(Xeapi.deriveAesKey(ss, epk)))
    }

    @Test
    fun xeapiSignMatchesVector() {
        assertEquals(
            "Yk+ZaKEkr9QNjDl3xo63LgsyliVw3/z8t11tRdMV/FM=",
            Xeapi.xeapiSign("1730000000000", "0123456789012345"),
        )
    }

    @Test
    fun midTransformMatchesVector() {
        val ct = hex("00112233445566778899aabbccddeeff")
        val rnd = hex("0f0e0d0c0b0a09080706050403020100")
        assertEquals(
            "0f0e0d0c0b0a09080706050403020100ff0f1f2f3f4f5f6f7f8f9fafbfcfdfef",
            Xeapi.toHex(Xeapi.midTransform(ct, rnd)),
        )
    }

    @Test
    fun aesGcmEncryptMatchesVector() {
        val key = hex("a9c5e3543077b063464f5343fc1afb06")
        val iv = hex("000102030405060708090a0b")
        val sealed = Xeapi.aesGcmEncrypt(key, iv, "hello world 123".toByteArray(StandardCharsets.UTF_8))
        // ct || tag
        assertEquals("a98db91a9b4957d7b594ce4b2acbd3dee1cee5f863ea4a2dd1d475de041d63", Xeapi.toHex(sealed))
    }

    @Test
    fun decryptPublicKeyRoundtrip() {
        val json = """{"version":"1000000000000","publicKey":"AAECAwQ=","sk":"c2s="}"""
        val encrypted = Base64.getEncoder().encodeToString(
            Xeapi.aesEcbEncrypt(staticKey, json.toByteArray(StandardCharsets.UTF_8)),
        )
        val state = Xeapi.decryptPublicKey(encrypted)
        assertEquals("1000000000000", state.version)
        assertEquals("AAECAwQ=", state.publicKey)
        assertEquals("c2s=", state.sk)
    }

    @Test
    fun decryptResponseUnzipsGzipBody() {
        val json = """{"code":200,"data":[{"url":"https://m801.music.126.net/x.mp3"}]}"""
        val gz = gzip(json.toByteArray(StandardCharsets.UTF_8))
        val encrypted = Xeapi.aesEcbEncrypt("e82ckenh8dichen8".toByteArray(StandardCharsets.UTF_8), gz)
        val out = Xeapi.decryptResponse(encrypted)
        assertEquals(json, out)
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
