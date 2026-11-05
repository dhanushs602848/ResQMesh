package com.resqmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for persisting and querying queued emergency incidents.
 */
@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(incident: IncidentEntity)

    @Query("SELECT * FROM incident_queue WHERE uploadStatus != 'UPLOADED' ORDER BY timestamp ASC")
    suspend fun getPendingIncidents(): List<IncidentEntity>

    @Query("SELECT * FROM incident_queue ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incident_queue WHERE id = :id LIMIT 1")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Query("UPDATE incident_queue SET uploadStatus = :status, retryCount = retryCount + 1, lastError = :error, lastAttemptAt = :attemptAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null, attemptAt: Long? = System.currentTimeMillis())

    @Query("UPDATE incident_queue SET uploadStatus = 'UPLOADED', lastError = null WHERE id = :id")
    suspend fun markUploaded(id: String)

    @Query("SELECT COUNT(*) FROM incident_queue WHERE uploadStatus != 'UPLOADED'")
    fun getPendingCount(): Flow<Int>

    @Query("UPDATE incident_queue SET isAcknowledged = 1, acknowledgedBy = :acknowledgedBy, acknowledgedAt = :timestamp WHERE id = :id")
    suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long)

    suspend fun addAcknowledgment(id: String, ackNodeId: String, timestamp: Long = System.currentTimeMillis()) {
        val incident = getIncidentById(id) ?: return
        val currentNodes = incident.acknowledgedBy?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (!currentNodes.contains(ackNodeId)) {
            val updatedNodes = (currentNodes + ackNodeId).joinToString(",")
            updateAcknowledgment(id, updatedNodes, timestamp)
        }
    }
}
