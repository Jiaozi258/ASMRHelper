package com.asmrhelper.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.asmrhelper.player.BinauralBeatEngine
import com.asmrhelper.player.EqualizerController
import com.asmrhelper.player.RefererHttpDataSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /** 音频属性：声明为音乐播放。
     *  `handleAudioFocus=false` — 音频焦点由 AudioFocusManager 统一管理，
     *  避免两个 ExoPlayer 各自争抢焦点导致点播放自动切回暂停的回归。 */
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    /** 浏览器 UA：在线流 CDN 校验 Referer 时也常校验 UA。 */
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    /** 在线流需带 Referer/User-Agent，否则网易云/QQ CDN 返回 403；本地文件走 file:// 不受影响。 */
    private fun mediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val httpFactory = RefererHttpDataSourceFactory(
            DefaultHttpDataSource.Factory().setUserAgent(BROWSER_USER_AGENT)
        )
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
    }

    @Provides
    @Singleton
    @MainPlayer
    fun provideMainPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory(context))
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
            .build()

    @Provides
    @Singleton
    @BackgroundPlayer
    fun provideBackgroundPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory(context))
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
            .build()

    @Provides
    @Singleton
    fun provideBinauralBeatEngine(): BinauralBeatEngine =
        BinauralBeatEngine()

    @Provides
    @Singleton
    fun provideEqualizerController(
        @MainPlayer mainPlayer: ExoPlayer,
        @ApplicationContext context: Context
    ): EqualizerController = EqualizerController(mainPlayer, context)
}
