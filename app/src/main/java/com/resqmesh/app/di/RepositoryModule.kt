package com.resqmesh.app.di

import com.resqmesh.app.data.repository.DefaultMeshRepository
import com.resqmesh.app.data.repository.MeshRepository
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
    abstract fun bindMeshRepository(
        impl: DefaultMeshRepository
    ): MeshRepository
}
