package com.asmrhelper.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 单一音频焦点管理器：整个应用只在这里申请/释放音频焦点。
 *
 * 两个 ExoPlayer 都不再各自处理焦点（PlayerModule 中 handleAudioFocus=false），
 * 由本类统一管理。这样来电或其它应用抢占焦点时，主播放器与环境音都能正确
 * 暂停/恢复，且避免"多个焦点持有者互相冲突"导致点播放自动切回暂停的回归。
 */
@Singleton
class AudioFocusManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 永久/暂时失去焦点（来电、其它应用开始播放）→ 应暂停。 */
    var onFocusLost: (() -> Unit)? = null

    /** 暂时失去但可降低音量 → 应 duck（降音）而非暂停。 */
    var onFocusDuck: (() -> Unit)? = null

    /** 重新获得焦点 → 应恢复播放。 */
    var onFocusGained: (() -> Unit)? = null

    private var focusRequest: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLost?.invoke()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusDuck?.invoke()
            AudioManager.AUDIOFOCUS_GAIN -> onFocusGained?.invoke()
        }
    }

    fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        focusRequest = null
    }
}
