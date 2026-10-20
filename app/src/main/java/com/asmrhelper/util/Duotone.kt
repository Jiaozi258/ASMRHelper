package com.asmrhelper.util

/** 封面双色调：由主色派生出「深色主调 + 红蓝交换的次调」，形成双色渐变。 */
object Duotone {
    data class DuotoneColors(val primary: Long, val secondary: Long)

    fun duotone(seed: Long): DuotoneColors =
        DuotoneColors(darken(seed, 55), swapRedBlue(seed))

    fun darken(argb: Long, percent: Int): Long {
        val a = (argb shr 24) and 0xFF
        val r = ((argb shr 16) and 0xFF) * percent / 100
        val g = ((argb shr 8) and 0xFF) * percent / 100
        val b = (argb and 0xFF) * percent / 100
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun swapRedBlue(argb: Long): Long {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (a.toLong() shl 24) or (b.toLong() shl 16) or (g.toLong() shl 8) or r.toLong()
    }

    /** 反色：RGB 逐通道取反（白↔黑、红↔青/绿蓝），保留 alpha。用于反差模式。 */
    fun inverse(argb: Long): Long {
        val a = (argb shr 24) and 0xFF
        val r = ((argb shr 16) and 0xFF)
        val g = ((argb shr 8) and 0xFF)
        val b = (argb and 0xFF)
        return (a.toLong() shl 24) or ((255 - r).toLong() shl 16) or ((255 - g).toLong() shl 8) or (255 - b).toLong()
    }
}

/** 把 24 位 RGB 补全为不透明的 32 位 ARGB（Long）。 */
fun opaqueArgb(rgb: Int): Long = 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
