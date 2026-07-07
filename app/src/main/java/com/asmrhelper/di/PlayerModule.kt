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
     *  `handleAudioFocus=true` lets ExoPlayer automatically request/abandon
     *  audio focus — critical for Chinese ROMs (MIUI/ColorOS) to not kill
     *  the app as "rogue background audio". */
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    @Provides
    @Singleton
    @MainPlayer
    fun provideMainPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ true)
            .build()

    @Provides
    @Singleton
    @BackgroundPlayer
    fun provideBackgroundPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ true)
            .build()

    @Provides
    @Singleton
    fun provideBinauralBeatEngine(): BinauralBeatEngine =
        BinauralBeatEngine()

    @Provides
    @Singleton
    fun provideEqualizerController(
        @MainPlayer mainPlayer: ExoPlayer
    ): EqualizerController = EqualizerController(mainPlayer)
}
