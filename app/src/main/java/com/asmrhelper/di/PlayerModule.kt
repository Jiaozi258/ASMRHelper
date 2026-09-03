package com.asmrhelper.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.player.BinauralBeatEngine
import com.asmrhelper.player.EqualizerController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /** Audio attributes that tell the OS this is music playback.
     *  `handleAudioFocus=false` — 音频焦点由 AudioFocusManager 统一管理，
     *  避免两个 ExoPlayer 各自争抢焦点导致点播放自动切回暂停的回归。 */
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Provides
    @Singleton
    @MainPlayer
    fun provideMainPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
            .build()

    @Provides
    @Singleton
    @BackgroundPlayer
    fun provideBackgroundPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
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
