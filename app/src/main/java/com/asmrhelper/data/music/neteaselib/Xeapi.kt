package com.asmrhelper.data.music.neteaselib

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import org.json.JSONObject

/**
 * 网易云 xeapi 加密（Folia 后端迁移后的新方案）。
 *
 * 结构：X25519 ECDH 派生 AES-128-GCM 密钥封装动态密钥 S，动态密钥 AES-ECB 加密
 * 载荷 C，HMAC-SHA256 生成公钥请求签名。常量与算法来自
 * `@neteasecloudmusicapienhanced/api` v4.41.1 的 `util/crypto.js`。
 */
object Xeapi {

    private val STATIC_KEY = hexToBytes(
        "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84",
    )
    private val EAPI_KEY = "e82ckenh8dichen8".toByteArray(StandardCharsets.UTF_8)

    /** 注意：这是字符串常量按 UTF-8 编码，不做 base64 解码（与 Folia 一致）。 */
    private val SIGN_KEY =
        ("mUHCwVNWJbunMqAHf5MImuirT6plvs6VSFW62MGHstFQxhBGdEoIhLItH3djc4+FB/OKty3" +
            "+lL2rGeoFBpVe5g==").toByteArray(StandardCharsets.UTF_8)

    fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** 密钥长度自适应（16/24/32 字节 → AES-128/192/256），对应 Folia 的 `aes-${len*8}-ecb`。 */
    fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plaintext)
    }

    fun aesEcbDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(ciphertext)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** `base64(HMAC-SHA256(UTF8(signKey), timestamp + nonce))`。 */
    fun xeapiSign(timestamp: String, nonce: String): String =
        Base64.getEncoder().encodeToString(
            hmacSha256(SIGN_KEY, (timestamp + nonce).toByteArray(StandardCharsets.UTF_8)),
        )

    /** RFC 5869 风格的两步派生：prk = HMAC(0^32, sharedSecret)，key = HMAC(prk, epk||0x01)[:16]。 */
    fun deriveAesKey(sharedSecret: ByteArray, ephemeralPublicKey: ByteArray): ByteArray {
        val prk = hmacSha256(ByteArray(32), if (sharedSecret.isNotEmpty()) sharedSecret else ByteArray(32))
        return hmacSha256(prk, ephemeralPublicKey + byteArrayOf(1)).copyOfRange(0, 16)
    }

    /** 异或 + 循环左移 + 前置随机数（Folia 的 xeapiMidTransform）。 */
    fun midTransform(ciphertext: ByteArray, random: ByteArray): ByteArray {
        val xored = ByteArray(ciphertext.size)
        for (i in ciphertext.indices) {
            xored[i] = (ciphertext[i].toInt() xor (random[i and 0x0f].toInt() and 0xff)).toByte()
        }
        val rot = if (xored.isEmpty()) 0 else (random[0].toInt() and 0x0f) % xored.size
        return random + xored.copyOfRange(rot, xored.size) + xored.copyOfRange(0, rot)
    }

    /** AES-128-GCM 加密，返回 `密文 || 认证标签`（12 字节 IV 由调用方提供）。 */
    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    /**
     * 封装动态密钥 S：临时 X25519 密钥对与对端公钥 ECDH，派生 GCM 密钥，
     * 加密 `${base64(dynamicKey)}|${os}|${sk}`。返回 `ephemeralPub(32) || iv(12) || ct || tag(16)`。
     */
    fun encryptS(dynamicKey: ByteArray, publicKeyState: PublicKeyState, os: String): ByteArray {
        val (priv, pub) = X25519.generateKeyPair()
        val peerRaw = Base64.getDecoder().decode(publicKeyState.publicKey)
        val shared = X25519.scalarMult(priv, peerRaw)
        val aesKey = deriveAesKey(shared, pub)
        val iv = Random.nextBytes(12)
        val plain = (Base64.getEncoder().encodeToString(dynamicKey) + "|" + os + "|" +
            (publicKeyState.sk ?: "")).toByteArray(StandardCharsets.UTF_8)
        return pub + iv + aesGcmEncrypt(aesKey, iv, plain)
    }

    /** 构造 xeapi 明文字段：content 为表单编码体，queryString 固定 `e_r=true`。 */
    private fun buildPlaintext(data: Map<String, String>): ByteArray {
        val body = data.entries.joinToString("&") { (k, v) -> formEncode(k) + "=" + formEncode(v) }
        return JSONObject()
            .put("content", body)
            .put("queryString", "e_r=true")
            .toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** 生成 C/S/R 三字段。sessionKey 为空时动态密钥取 16 字节随机数（匿名流程）。 */
    fun xeapi(
        data: Map<String, String>,
        publicKeyState: PublicKeyState,
        os: String,
        sessionKey: String,
        sessionId: String,
    ): XeapiForm {
        val dynamicKey =
            if (sessionKey.isNotBlank()) sessionKey.toByteArray(StandardCharsets.UTF_8) else Random.nextBytes(16)
        val plaintext = buildPlaintext(data)
        val c = aesEcbEncrypt(
            dynamicKey,
            midTransform(aesEcbEncrypt(STATIC_KEY, plaintext), Random.nextBytes(16)),
        )
        val s = encryptS(dynamicKey, publicKeyState, os)
        val r = aesEcbEncrypt(
            STATIC_KEY,
            (publicKeyState.version + "|" + if (sessionKey.isNotBlank()) sessionId else "")
                .toByteArray(StandardCharsets.UTF_8),
        )
        return XeapiForm(
            c = base64UrlEncode(c),
            s = Base64.getEncoder().encodeToString(s),
            r = Base64.getEncoder().encodeToString(r),
        )
    }

    /** 解密响应：AES-ECB(EAPI_KEY) 后，若为 gzip（1f 8b）则解压。 */
    fun decryptResponse(body: ByteArray): String {
        val decrypted = aesEcbDecrypt(EAPI_KEY, body)
        val plain = if (decrypted.size >= 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()) {
            gunzip(decrypted)
        } else {
            decrypted
        }
        return String(plain, StandardCharsets.UTF_8)
    }

    /** 公钥响应解密：AES-ECB(STATIC_KEY, base64(encryptedData)) → {version, publicKey, sk}。 */
    fun decryptPublicKey(encryptedData: String): PublicKeyState {
        val json = aesEcbDecrypt(STATIC_KEY, Base64.getDecoder().decode(encryptedData))
        val obj = JSONObject(String(json, StandardCharsets.UTF_8))
        return PublicKeyState(
            version = obj.optString("version"),
            publicKey = obj.optString("publicKey"),
            sk = obj.optString("sk").ifBlank { null },
        )
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gz -> gz.copyTo(out) }
        return out.toByteArray()
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** application/x-www-form-urlencoded 编码，与 URLSearchParams 一致（空格→`+`）。 */
    private fun formEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

/** 公钥状态：version/publicKey/sk 来自 `/api/bsr/sk/get`。 */
data class PublicKeyState(val version: String, val publicKey: String, val sk: String?)

/** xeapi 请求表单三字段。 */
data class XeapiForm(val c: String, val s: String, val r: String)
