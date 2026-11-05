package com.resqmesh.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.resqmesh.app.data.local.dao.IncidentDao
import com.resqmesh.app.data.local.entity.IncidentEntity

/**
 * ResQMesh local Room database for offline-first persistence.
 */
@Database(entities = [IncidentEntity::class], version = 3, exportSchema = false)
abstract class ResQMeshDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN originNodeId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN hopCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN isReceived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN isAcknowledged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN acknowledgedBy TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE incident_queue ADD COLUMN acknowledgedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }
    }
}
