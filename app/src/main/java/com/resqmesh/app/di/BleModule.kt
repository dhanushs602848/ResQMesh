package com.resqmesh.app.di

import com.resqmesh.app.data.ble.BleManager
import com.resqmesh.app.data.ble.DefaultBleManager
import com.resqmesh.app.data.ble.DefaultGattPacketDispatcher
import com.resqmesh.app.data.ble.DefaultMeshRelayEngine
import com.resqmesh.app.data.ble.GattPacketDispatcher
import com.resqmesh.app.data.ble.MeshRelayEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    @Singleton
    abstract fun bindBleManager(
        impl: DefaultBleManager
    ): BleManager

    @Binds
    @Singleton
    abstract fun bindGattPacketDispatcher(
        impl: DefaultGattPacketDispatcher
    ): GattPacketDispatcher

    @Binds
    @Singleton
    abstract fun bindMeshRelayEngine(
        impl: DefaultMeshRelayEngine
    ): MeshRelayEngine
}
