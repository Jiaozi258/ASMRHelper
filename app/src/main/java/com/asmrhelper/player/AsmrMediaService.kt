package com.asmrhelper.player

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.asmrhelper.MainActivity
import com.asmrhelper.R
import com.asmrhelper.domain.model.LoopMode
import com.asmrhelper.domain.model.PlayerState
import com.asmrhelper.util.Constants
import com.asmrhelper.widget.AsmrWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AsmrMediaService : Service() {

    @Inject lateinit var playerManager: PlayerManager
    private lateinit var mediaSession: MediaSessionCompat
    private var channelReady = false

    // ── WakeLock: prevent CPU sleep during playback ──────
    private var wakeLock: PowerManager.WakeLock? = null

    // ── AudioFocus: react to calls, alarms, other apps ────
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null  // API 26+
    private var hasAudioFocus: Boolean = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        android.util.Log.d("AsmrMedia", "AudioFocus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (e.g. phone call) — pause fully
                hasAudioFocus = false
                playerManager.handleEvent(PlayerEvent.Pause)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (e.g. notification ping) — pause
                hasAudioFocus = false
                playerManager.handleEvent(PlayerEvent.Pause)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Short interruption — lower volume instead of pausing
                // ExoPlayer handles ducking internally when handleAudioFocus=true,
                // so we don't need to do anything extra here.
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained — resume if we were playing before
                hasAudioFocus = true
                val state = playerManager.state.value
                if (!state.isPlaying && state.currentAudio != null) {
                    playerManager.handleEvent(PlayerEvent.Resume)
                }
            }
        }
    }

    // ── Notification throttle: avoid rebuild spam ────────
    private var lastNotifTitle: String? = null
    private var lastNotifArtist: String? = null
    private var lastNotifPlaying: Boolean? = null
    private var lastNotifTime: Long = 0L
    private var lastPostedProgress: Long = -10000L
    private val minNotifInterval = 1000L  // rebuild at most once per second

    // ── Widget throttle: only notify on actual state changes ──
    private var lastWidgetPlaying: Boolean? = null
    private var lastWidgetTitle: String? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        channelReady = ch != null
        android.util.Log.i("AsmrMedia", "onCreate channelReady=$channelReady importance=${ch?.importance}")

        // WakeLock: keep CPU on for uninterrupted playback
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ASMRHelper:PlaybackWakeLock"
        )

        // AudioFocus: receive callbacks for phone calls, alarms, etc.
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupMediaSession()
        playerManager.setStateListener { state ->
            updateNotification(state)
            updateMediaSessionState(state)
            manageWakeLock(state.isPlaying)
            manageAudioFocus(state.isPlaying)
            // Notify widget only on actual play/pause or title changes (not
            // on every 200ms progress tick — avoids excessive broadcasts).
            val currentTitle = state.currentAudio?.title ?: "ASMRHelper"
            if (state.isPlaying != lastWidgetPlaying || currentTitle != lastWidgetTitle) {
                lastWidgetPlaying = state.isPlaying
                lastWidgetTitle = currentTitle
                AsmrWidgetProvider.notifyStateChanged(
                    this, state.isPlaying, currentTitle
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always start the foreground service. Android requires a notification
        // for ALL foreground services — there's no way to run without one.
        // The user's "show_notification" pref only controls lock-screen
        // visibility, not whether the service runs.
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
        } catch (e: Exception) {
            android.util.Log.e("AsmrMedia", "startForeground failed: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // ── Process-death recovery: resume playback if the service was
        //     recreated by START_STICKY after being killed by the OS. ──
        if (intent == null && !state.isPlaying && state.currentAudio == null) {
            android.util.Log.i("AsmrMedia", "Restarting from process death — resuming playback")
            playerManager.resumeLastPlayback()
        }

        return START_STICKY
    }

    /**
     * Called when the user swipes the app away from the recent-tasks list.
     *
     * The DEFAULT implementation stops the service on many Chinese ROMs
     * (MIUI, ColorOS, OriginOS) even with stopWithTask="false" — they
     * override the AOSP behaviour. Overriding this as a no-op tells the
     * system "keep the service running — the user wants background playback."
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally do NOT call stopSelf() or super.onTaskRemoved()
        android.util.Log.i("AsmrMedia", "Task removed — service staying alive for playback")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        abandonAudioFocus()
        releaseWakeLock()
        mediaSession.isActive = false
        mediaSession.release()
        playerManager.setStateListener(null)
        super.onDestroy()
    }

    // ── WakeLock ──────────────────────────────────────────

    private fun manageWakeLock(isPlaying: Boolean) {
        if (isPlaying) {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // max timeout
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
    }

    // ── AudioFocus ────────────────────────────────────────

    private fun manageAudioFocus(isPlaying: Boolean) {
        if (isPlaying && !hasAudioFocus) {
            requestAudioFocus()
        } else if (!isPlaying && hasAudioFocus) {
            // Don't abandon on pause — only on permanent stop.
            // This keeps us registered for focus changes so we can auto-resume
            // when the interrupting app (e.g. phone call) releases focus.
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attr)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                val result = am.requestAudioFocus(audioFocusRequest!!)
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
            android.util.Log.d("AsmrMedia", "requestAudioFocus → granted=$hasAudioFocus")
        } catch (e: Exception) {
            android.util.Log.e("AsmrMedia", "requestAudioFocus failed: ${e.message}")
            hasAudioFocus = false
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
            hasAudioFocus = false
            android.util.Log.d("AsmrMedia", "abandonAudioFocus")
        } catch (e: Exception) {
            android.util.Log.e("AsmrMedia", "abandonAudioFocus failed: ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────

    /** Build the full MediaStyle notification — called only when content
     *  actually changes, NOT every 200ms progress tick. */
    private fun buildNotification(state: PlayerState): android.app.Notification {
        val prefs = getSharedPreferences("asmr_settings", MODE_PRIVATE)
        val showOnLockScreen = prefs.getBoolean("show_on_lockscreen", false)
        val loopLabel = when (state.loopMode) {
            LoopMode.NONE -> ""
            LoopMode.SINGLE -> " | 单曲循环"
            LoopMode.LIST -> " | 列表循环"
            else -> ""
        }
        val title = state.currentAudio?.title ?: "ASMRHelper"
        val subtitle = (state.currentAudio?.artist ?: "未在播放") + loopLabel

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)           // no sound/vibration on updates
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

    /** Smart notification update — only rebuilds when content changes.
     *  Progress-only ticks are skipped (throttled to once per second). */
    private fun updateNotification(state: PlayerState) {
        // Always show a notification — Android requires it for foreground
        // services. The pref only controls lock-screen visibility, not whether
        // the notification exists. Without a notification, startForeground()
        // crashes the service after 5 seconds (ANR).
        val title = state.currentAudio?.title
        val artist = state.currentAudio?.artist
        val isPlaying = state.isPlaying

        // Check if content actually changed (title, artist, play/pause)
        val contentChanged = title != lastNotifTitle
            || artist != lastNotifArtist
            || isPlaying != lastNotifPlaying

        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastNotifTime
        val needsThrottle = timeSinceLast < minNotifInterval

        if (contentChanged || (!needsThrottle && now - lastPostedProgress >= minNotifInterval)) {
            try {
                val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
                nm.notify(Constants.NOTIFICATION_ID, buildNotification(state))
                lastNotifTitle = title
                lastNotifArtist = artist
                lastNotifPlaying = isPlaying
                lastNotifTime = now
                lastPostedProgress = now
            } catch (_: Exception) { }
        }
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
                override fun onPlay() {
                    android.util.Log.d("AsmrMedia", "Headset: PLAY received")
                    playerManager.handleEvent(PlayerEvent.Resume)
                }
                override fun onPause() {
                    android.util.Log.d("AsmrMedia", "Headset: PAUSE received")
                    playerManager.handleEvent(PlayerEvent.Pause)
                }
                override fun onSkipToNext() {
                    android.util.Log.d("AsmrMedia", "Headset: SKIP_NEXT received")
                    playerManager.handleEvent(PlayerEvent.Next)
                }
                override fun onSkipToPrevious() {
                    android.util.Log.d("AsmrMedia", "Headset: SKIP_PREV received")
                    playerManager.handleEvent(PlayerEvent.Previous)
                }
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
