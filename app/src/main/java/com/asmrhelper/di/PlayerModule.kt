package com.asmrhelper.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.asmrhelper.player.BinauralBeatEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    @MainPlayer
    fun provideMainPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()

    @Provides
    @Singleton
    @BackgroundPlayer
    fun provideBackgroundPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()

    @Provides
    @Singleton
    fun provideBinauralBeatEngine(): BinauralBeatEngine =
        BinauralBeatEngine()
}
