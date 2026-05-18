package com.asmrhelper.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainPlayer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackgroundPlayer
