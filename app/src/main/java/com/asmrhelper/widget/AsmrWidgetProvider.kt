package com.asmrhelper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.media.session.MediaButtonReceiver
import com.asmrhelper.MainActivity
import com.asmrhelper.R

class AsmrWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE = "com.asmrhelper.WIDGET_UPDATE"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TITLE = "title"

        /**
         * Called from AsmrMediaService when playback state changes.
         * Updates all widget instances to reflect current state.
         */
        fun notifyStateChanged(context: Context, isPlaying: Boolean, title: String) {
            val intent = Intent(context, AsmrWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_TITLE, title)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "ASMRHelper"
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, AsmrWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, appWidgetManager, id, isPlaying, title)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, false, "ASMRHelper")
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        isPlaying: Boolean,
        title: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_asmr)

        // Title
        views.setTextViewText(R.id.widget_title, title)

        // Play/Pause button icon
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause
                      else android.R.drawable.ic_media_play
        views.setImageViewResource(R.id.widget_play_pause, iconRes)

        // Play/Pause button action
        val action = if (isPlaying) PlaybackStateCompat.ACTION_PAUSE
                     else PlaybackStateCompat.ACTION_PLAY
        val mediaIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(context, action)
        if (mediaIntent != null) {
            views.setOnClickPendingIntent(R.id.widget_play_pause, mediaIntent)
        }

        // Click on widget root → open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, widgetId, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
