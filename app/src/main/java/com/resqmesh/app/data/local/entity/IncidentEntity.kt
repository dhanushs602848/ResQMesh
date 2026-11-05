package com.resqmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an emergency incident queued for backend upload.
 * Ensures persistent offline storage so no SOS signals are lost even if the app
 * is killed or the device is offline during creation.
 */
@Entity(tableName = "incident_queue")
data class IncidentEntity(
    @PrimaryKey
    val id: String,
    val senderId: String?,
    val senderName: String?,
    val title: String,
    val content: String,
    val priority: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val timestamp: Long,
    val isoTimestamp: String?,
    val uploadStatus: String, // "PENDING", "UPLOADING", "UPLOADED", "FAILED"
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val originNodeId: String? = null,
    val hopCount: Int = 0,
    val isReceived: Boolean = false,
    val isAcknowledged: Boolean = false,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null
)
