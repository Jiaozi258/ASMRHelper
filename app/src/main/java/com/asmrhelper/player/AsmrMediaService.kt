package com.asmrhelper.player

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.asmrhelper.MainActivity
import com.asmrhelper.R
import com.asmrhelper.domain.model.PlayerState
import com.asmrhelper.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AsmrMediaService : Service() {

    @Inject lateinit var playerManager: PlayerManager
    private lateinit var mediaSession: MediaSessionCompat
    private var channelReady = false

    override fun onCreate() {
        super.onCreate()
        // Channel is created in AsmrApplication. Verify it exists.
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        channelReady = ch != null
        android.util.Log.i("AsmrMedia", "onCreate channelReady=$channelReady importance=${ch?.importance}, areEnabled=${nm.areNotificationsEnabled()}")

        setupMediaSession()
        playerManager.setStateListener { state ->
            updateNotification(state)
            updateMediaSessionState(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("asmr_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("show_notification", true)) {
            stopSelf(); return START_NOT_STICKY
        }

        // Diagnostic: check if system notifications are enabled for this app
        if (!channelReady) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val areEnabled = nm.areNotificationsEnabled()
            android.util.Log.w("AsmrMedia", "Notifications enabled by system: $areEnabled")
        }

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        val state = playerManager.state.value
        val notification = buildNotification(state)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startForeground(
                    Constants.NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification)
            }
            android.util.Log.i("AsmrMedia", "startForeground OK, title=${state.currentAudio?.title}, sdk=${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            android.util.Log.e("AsmrMedia", "startForeground failed: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        playerManager.setStateListener(null)
        super.onDestroy()
    }

    // ── Notification ──────────────────────────────────────

    private fun buildNotification(state: PlayerState): android.app.Notification {
        val prefs = getSharedPreferences("asmr_settings", MODE_PRIVATE)
        val showOnLockScreen = prefs.getBoolean("show_on_lockscreen", false)
        val loopLabel = when (state.loopMode) {
            com.asmrhelper.domain.model.LoopMode.NONE -> ""
            com.asmrhelper.domain.model.LoopMode.SINGLE -> " | 单曲循环"
            com.asmrhelper.domain.model.LoopMode.LIST -> " | 列表循环"
            else -> ""
        }
        val title = state.currentAudio?.title ?: "ASMRHelper"
        val subtitle = (state.currentAudio?.artist ?: "未在播放") + loopLabel

        // FOREGROUND_SERVICE_IMMEDIATE (API 31+) makes the system show the
        // notification immediately instead of deferring it — critical on
        // Chinese ROMs (MIUI, ColorOS) where deferred FG notifications are
        // often hidden in the drawer.
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
                if (state.isPlaying) "暂停" else "播放",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (state.isPlaying) PlaybackStateCompat.ACTION_PAUSE
                    else PlaybackStateCompat.ACTION_PLAY
                )
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一首",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(true)
            .setVisibility(
                if (showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_SECRET
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun updateNotification(state: PlayerState) {
        val prefs = getSharedPreferences("asmr_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("show_notification", true)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(Constants.NOTIFICATION_ID, buildNotification(state))
        } catch (_: Exception) { }
    }

    // ── MediaSession ──────────────────────────────────────

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "ASMRHelper").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                    ).build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = playerManager.handleEvent(PlayerEvent.Resume)
                override fun onPause() = playerManager.handleEvent(PlayerEvent.Pause)
                override fun onSkipToNext() = playerManager.handleEvent(PlayerEvent.Next)
                override fun onSkipToPrevious() = playerManager.handleEvent(PlayerEvent.Previous)
                override fun onStop() = stopSelf()
            })
        }
        mediaSession.isActive = true
    }

    private fun updateMediaSessionState(state: PlayerState) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    state.progressMs, 1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                ).build()
        )
        mediaSession.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, state.currentAudio?.title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, state.currentAudio?.artist)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
    }
}
