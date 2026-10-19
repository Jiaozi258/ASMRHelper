package com.asmrhelper.domain.model

/** 在线播放音质档位（全局一个档位，切换后只影响下一首）。 */
enum class PlaybackQuality(val key: String) {
    STANDARD("standard"),    // 标准
    HIGHER("higher"),        // 较高
    EXCELLENT("excellent"),  // 极高
    LOSSLESS("lossless")     // 无损
}
