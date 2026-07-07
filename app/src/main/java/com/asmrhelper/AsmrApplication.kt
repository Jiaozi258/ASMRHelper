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
        val targetImportance = NotificationManager.IMPORTANCE_HIGH

        // Build the desired channel config
        val desired = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            targetImportance
        ).apply {
            description = "播放控制和当前播放信息"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val needsRecreate = existing == null
            || existing.importance < targetImportance   // upgrade → must recreate
            || existing.shouldVibrate() != desired.shouldVibrate()
            || existing.lockscreenVisibility != desired.lockscreenVisibility

        if (needsRecreate) {
            if (existing != null) nm.deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
            nm.createNotificationChannel(desired)
            android.util.Log.i("AsmrApp", "Notification channel (re)created: importance=$targetImportance")
        } else if (existing.importance > targetImportance) {
            // Downgrade: createNotificationChannel preserves higher importance,
            // so we must recreate.
            nm.deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
            nm.createNotificationChannel(desired)
        } else {
            // Channel exists with correct importance — just update properties
            nm.createNotificationChannel(desired)
        }
    }
}
