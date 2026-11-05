package com.resqmesh.app.di

import android.content.Context
import androidx.room.Room
import com.resqmesh.app.data.local.ResQMeshDatabase
import com.resqmesh.app.data.local.dao.IncidentDao
import com.resqmesh.app.data.remote.ResQMeshApi
import com.resqmesh.app.data.remote.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ResQMeshDatabase {
        return Room.databaseBuilder(
            context,
            ResQMeshDatabase::class.java,
            "resqmesh_database.db"
        )
            .addMigrations(
                ResQMeshDatabase.MIGRATION_1_2,
                ResQMeshDatabase.MIGRATION_2_3,
                ResQMeshDatabase.MIGRATION_1_3
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideIncidentDao(database: ResQMeshDatabase): IncidentDao {
        return database.incidentDao()
    }

    @Provides
    @Singleton
    fun provideResQMeshApi(): ResQMeshApi {
        return RetrofitClient.api
    }
}
