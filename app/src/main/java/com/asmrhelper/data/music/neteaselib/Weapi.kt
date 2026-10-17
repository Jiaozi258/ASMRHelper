package com.asmrhelper.data.music.neteaselib

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 网易云 weapi 加密：`params` 双 AES-128-CBC + `encSecKey` 的 RSA-1024 模幂。
 * 常量与算法来自公开的 NeteaseCloudMusicApi 实现（weapi 方案）。
 */
object Weapi {

    private const val AES_KEY_1 = "0CoJUm6Qyw8W8jud"
    private const val AES_IV = "0102030405060708"
    private const val RSA_EXPONENT_HEX = "010001"
    private const val RSA_MODULUS_HEX =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val SEC_KEY_CHARS =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun md5(s: String): String = MessageDigest.getInstance("MD5")
        .digest(s.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun aesEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain)
    }

    /** `plain^e mod n`（教科书 RSA 模幂），返回 256 位小写 hex。 */
    fun rsaEncrypt(plain: ByteArray): String {
        val m = BigInteger(1, plain)
        val e = BigInteger(RSA_EXPONENT_HEX, 16)
        val n = BigInteger(RSA_MODULUS_HEX, 16)
        return m.modPow(e, n).toString(16).padStart(256, '0')
    }

    fun encrypt(json: String): WeapiForm {
        val secKey = randomSecKey()
        val utf8: (String) -> ByteArray = { it.toByteArray(StandardCharsets.UTF_8) }
        val first = aesEncrypt(utf8(json), utf8(AES_KEY_1), utf8(AES_IV))
        val firstB64 = Base64.getEncoder().encodeToString(first)
        val second = aesEncrypt(utf8(firstB64), utf8(secKey), utf8(AES_IV))
        val params = Base64.getEncoder().encodeToString(second)
        val encSecKey = rsaEncrypt(utf8(secKey.reversed()))
        return WeapiForm(params, encSecKey)
    }

    fun randomSecKey(): String {
        val sb = StringBuilder(16)
        repeat(16) { sb.append(SEC_KEY_CHARS[Random.nextInt(SEC_KEY_CHARS.length)]) }
        return sb.toString()
    }
}

/** weapi 请求表单：`params`（base64）与 `encSecKey`（256 hex）。 */
data class WeapiForm(val params: String, val encSecKey: String)
