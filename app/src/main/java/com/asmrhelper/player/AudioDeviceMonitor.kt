package com.asmrhelper.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听音频输出设备的接入 / 移除（蓝牙、有线耳机、USB 耳机），
 * 配合「断开设备停止播放 / 连接耳机自动续播」两项播放设置。
 * 回调由 PlayerManager 注册；仅当对应设置开启时才触发回调。
 */
@Singleton
class AudioDeviceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("asmr_settings", Context.MODE_PRIVATE)

    /** 输出设备被移除且已无同类设备在输出 → 应停止播放。 */
    var onDeviceDisconnected: (() -> Unit)? = null

    /** 有耳机/蓝牙设备接入 → 应续播。 */
    var onDeviceConnected: (() -> Unit)? = null

    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!prefs.getBoolean("bluetooth_stop", true)) return
            if (removedDevices.none { it.isHeadphoneOrBluetooth() }) return
            // 若仍有其它耳机/蓝牙设备在输出，则不视为「断开设备」
            val stillConnected = audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.isHeadphoneOrBluetooth() }
            if (!stillConnected) onDeviceDisconnected?.invoke()
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!prefs.getBoolean("bluetooth_resume", false)) return
            if (addedDevices.any { it.isHeadphoneOrBluetooth() }) {
                onDeviceConnected?.invoke()
            }
        }
    }

    fun start() {
        if (registered) return
        audioManager.registerAudioDeviceCallback(callback, null)
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { audioManager.unregisterAudioDeviceCallback(callback) } catch (_: Exception) { }
        registered = false
    }

    private fun AudioDeviceInfo.isHeadphoneOrBluetooth(): Boolean =
        when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> false
        }
}
