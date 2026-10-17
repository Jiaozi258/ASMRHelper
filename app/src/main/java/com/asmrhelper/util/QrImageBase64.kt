package com.asmrhelper.util

/** 从网易云返回的二维码图片串中提取 base64 内容；空串/非图片串返回 null。 */
object QrImageBase64 {

    fun payload(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val idx = trimmed.indexOf("base64,")
        val payload = if (idx >= 0) trimmed.substring(idx + "base64,".length) else trimmed
        return payload.ifBlank { null }
    }
}
