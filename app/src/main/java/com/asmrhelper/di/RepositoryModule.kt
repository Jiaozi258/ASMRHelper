package com.asmrhelper.di

import com.asmrhelper.data.repository.AudioRepositoryImpl
import com.asmrhelper.data.repository.PlaylistRepositoryImpl
import com.asmrhelper.data.repository.SettingsRepositoryImpl
import com.asmrhelper.data.repository.VideoAudioRepositoryImpl
import com.asmrhelper.domain.repository.AudioRepository
import com.asmrhelper.domain.repository.PlaylistRepository
import com.asmrhelper.domain.repository.SettingsRepository
import com.asmrhelper.domain.repository.VideoAudioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAudioRepository(impl: AudioRepositoryImpl): AudioRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVideoAudioRepository(impl: VideoAudioRepositoryImpl): VideoAudioRepository
}
