package com.resqmesh.app.di

import com.resqmesh.app.data.audio.AndroidAudioAlertManager
import com.resqmesh.app.data.audio.AudioAlertManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioAlertManager(
        impl: AndroidAudioAlertManager
    ): AudioAlertManager
}
