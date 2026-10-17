package com.asmrhelper.data.music.qq

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * QQ 音乐 `zzc` 请求签名：对 `u.y.qq.com/cgi-bin/musics.fcg` 的 POST body 签名。
 * 算法来自公开的 QQ 音乐接口实现（SHA-1 + 索引切片 + XOR 扰码 + base64 去填充）。
 */
object QqMusicSign {

    private val PART_1 = intArrayOf(23, 14, 6, 36, 16, 7, 19)
    private val PART_2 = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE = intArrayOf(
        89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179
    )

    fun sign(payload: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }

        val p1 = PART_1.joinToString("") { sha1[it].toString() }
        val p2 = PART_2.joinToString("") { sha1[it].toString() }

        val scrambled = ByteArray(SCRAMBLE.size) { i ->
            val byte = sha1.substring(i * 2, i * 2 + 2).toInt(16)
            (byte xor SCRAMBLE[i]).toByte()
        }
        val b = Base64.getEncoder().encodeToString(scrambled)
            .replace("/", "").replace("+", "").replace("=", "")

        return ("zzc" + p1 + b + p2).lowercase()
    }
}
