package com.asmrhelper.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "ASMRHelper")
            .apply {
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
                        )
                        .build()
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

        // Listen for state changes to update notification
        playerManager.setStateListener { state ->
            updateNotification(state)
            updateMediaSessionState(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        val state = playerManager.state.value
        val notification = buildNotification(state)
        startForeground(Constants.NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        playerManager.setStateListener(null)
        // Do NOT release singleton ExoPlayer instances here —
        // PlayerManager owns Hilt-provided @Singleton players that
        // must survive service restarts within the same process.
        playerManager.stop()
        super.onDestroy()
    }

    private fun buildNotification(state: PlayerState): android.app.Notification {
        val loopLabel = when (state.loopMode) {
            com.asmrhelper.domain.model.LoopMode.NONE -> ""
            com.asmrhelper.domain.model.LoopMode.SINGLE -> " | 单曲循环"
            com.asmrhelper.domain.model.LoopMode.LIST -> " | 列表循环"
        }
        val subtitle = (state.currentAudio?.artist ?: "未在播放") + loopLabel
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(state.currentAudio?.title ?: "ASMRHelper")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "暂停" else "播放",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (state.isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
                )
            )
            .addAction(
                android.R.drawable.ic_media_next, "下一首",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(state.isPlaying)
            .build()
    }

    private fun updateNotification(state: PlayerState) {
        val notification = buildNotification(state)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun updateMediaSessionState(state: PlayerState) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    state.progressMs,
                    1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                )
                .build()
        )

        mediaSession.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, state.currentAudio?.title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, state.currentAudio?.artist)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
