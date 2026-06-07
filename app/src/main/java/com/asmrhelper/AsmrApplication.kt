package com.asmrhelper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import com.asmrhelper.util.Constants
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AsmrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.setup(this)
        ensureNotificationChannel()
    }

    /**
     * Create the playback notification channel at app startup.
     * Doing this here (not in the Service) avoids a race condition on
     * Chinese ROMs (MIUI, ColorOS, etc.) where the channel isn't fully
     * registered by the time startForeground() is called.
     *
     * IMPORTANCE_HIGH is needed on some Chinese ROMs — DEFAULT-level
     * foreground-service notifications are silently collapsed/hidden
     * in the notification drawer.
     */
    private fun ensureNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        val target = NotificationManager.IMPORTANCE_HIGH

        if (existing != null && existing.importance == target) {
            return // Already correctly configured
        }

        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            target
        ).apply {
            description = "播放控制和当前播放信息"
            setShowBadge(false)
            // Silent sound — keeps the channel "active" without being annoying
            setSound(null, null)
            enableVibration(false)
            // Don't show heads-up popup — just show in the drawer
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        if (existing != null) {
            nm.deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        }
        nm.createNotificationChannel(channel)
        android.util.Log.i("AsmrApp", "Notification channel ready: importance=$target")
    }
}
