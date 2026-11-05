package com.resqmesh.app.di

import android.content.Context
import com.resqmesh.security.crypto.EncryptedPayloadCodec
import com.resqmesh.security.keys.AndroidKeystoreResponderKeyProvider
import com.resqmesh.security.keys.DefaultResponderKeyProvisioner
import com.resqmesh.security.keys.ResponderKeyProvider
import com.resqmesh.security.keys.ResponderKeyProvisioner
import com.resqmesh.security.replay.InMemoryReplayProtectionValidator
import com.resqmesh.security.replay.ReplayProtectionValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module exposing core:security primitives to the ResQMesh application.
 *
 * NOTE: The security module is deliberately not connected to the live BLE transmission route
 * until physical A->B->C relay verification is completed and approved.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideAndroidKeystoreResponderKeyProvider(
        @ApplicationContext context: Context
    ): AndroidKeystoreResponderKeyProvider {
        return AndroidKeystoreResponderKeyProvider(context)
    }

    @Provides
    @Singleton
    fun provideResponderKeyProvider(
        keystoreProvider: AndroidKeystoreResponderKeyProvider
    ): ResponderKeyProvider {
        return keystoreProvider
    }

    @Provides
    @Singleton
    fun provideResponderKeyProvisioner(
        keystoreProvider: AndroidKeystoreResponderKeyProvider
    ): ResponderKeyProvisioner {
        return DefaultResponderKeyProvisioner(keystoreProvider)
    }

    @Provides
    @Singleton
    fun provideEncryptedPayloadCodec(
        keyProvider: ResponderKeyProvider
    ): EncryptedPayloadCodec {
        return EncryptedPayloadCodec(keyProvider)
    }

    @Provides
    @Singleton
    fun provideReplayProtectionValidator(): ReplayProtectionValidator {
        return InMemoryReplayProtectionValidator()
    }
}
