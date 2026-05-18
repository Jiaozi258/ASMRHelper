package com.asmrhelper.domain.model

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentAudio: Audio? = null,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val loopMode: LoopMode = LoopMode.NONE,
    val isBackgroundPlaying: Boolean = false,
    val isPrivacyMode: Boolean = false,
    val crossfadeDurationMs: Int = 0  // 0=off, 3000, 5000, 10000
)
