package com.asmrhelper.data.music.neteaselib

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 加密：AES-128-ECB（无 IV，PKCS7）+ `url-盐-数据-盐-MD5 签名` 载荷。
 * 常量与算法来自公开的 NeteaseCloudMusicApi 实现（eapi 方案），用于新版
 * `/eapi/song/enhance/player/url/v1` 等接口。
 */
object Eapi {

    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val SALT = "-36cd479b6b5-"

    fun md5(s: String): String = MessageDigest.getInstance("MD5")
        .digest(s.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** 返回大写 hex 的 `params`，作为 POST 表单的 `params` 字段。 */
    fun encrypt(url: String, jsonBody: String): String {
        val message = "nobody${url}use${jsonBody}md5forencrypt"
        val digest = md5(message)
        val data = "$url$SALT$jsonBody$SALT$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(EAPI_KEY.toByteArray(StandardCharsets.UTF_8), "AES"))
        val enc = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return enc.joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }
}
