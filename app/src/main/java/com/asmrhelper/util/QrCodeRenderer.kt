package com.asmrhelper.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

/** 用 zxing-core 把内容渲染为二维码 Bitmap；失败返回 null。 */
object QrCodeRenderer {

    fun render(content: String, sizePx: Int): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.MARGIN] = 1
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                for (x in 0 until sizePx) {
                    pixels[y * sizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
        } catch (_: Exception) {
            null
        }
    }
}
