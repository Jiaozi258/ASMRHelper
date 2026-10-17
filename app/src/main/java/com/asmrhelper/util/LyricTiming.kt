package com.asmrhelper.util

enum class LyricMode { JUMP, SCROLL }

/** 当前进度对应的歌词行：最后一个 timeMs <= progressMs 的下标，无匹配取 0。 */
fun List<LrcLine>.currentLineIndex(progressMs: Long): Int =
    indexOfLast { it.timeMs <= progressMs }.coerceAtLeast(0)

/** 歌词滚动锚点：JUMP 直接跳当前行；SCROLL 让当前行尽量居中（上移 visibleCount/2 行）。 */
fun scrollAnchorIndex(currentIndex: Int, mode: LyricMode, visibleCount: Int): Int =
    when (mode) {
        LyricMode.JUMP -> currentIndex
        LyricMode.SCROLL -> (currentIndex - visibleCount / 2).coerceAtLeast(0)
    }
