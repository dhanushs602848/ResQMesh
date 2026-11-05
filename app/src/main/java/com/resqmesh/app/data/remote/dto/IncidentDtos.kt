
package com.resqmesh.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request DTO for uploading an emergency incident.
 * Matches POST /api/uplink on the ResQMesh backend.
 */
data class IncidentUploadRequest(

    @SerializedName("id")
    val id: String,

    @SerializedName("senderId")
    val senderId: String? = null,

    @SerializedName("senderName")
    val senderName: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    // Use IncidentPriority.name when creating this request.
    @SerializedName("priority")
    val priority: String,

    @SerializedName("location")
    val location: LocationDto? = null,

    // ISO-8601 timestamp, for example: 2026-11-05T13:30:00.000Z
    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * Location information attached to an incident.
 */
data class LocationDto(

    @SerializedName("latitude")
    val latitude: Double,

    @SerializedName("longitude")
    val longitude: Double,

    @SerializedName("accuracy")
    val accuracy: Float? = null
)

/**
 * Response from POST /api/uplink.
 */
data class IncidentUploadResponse(

    @SerializedName("success")
    val success: Boolean,

    @SerializedName("duplicate")
    val duplicate: Boolean? = false,

    @SerializedName("incidentId")
    val incidentId: String? = null,

    @SerializedName("receivedAt")
    val receivedAt: String? = null,

    @SerializedName("message")
    val message: String? = null
)

/**
 * Response from GET /api/health.
 */
data class HealthCheckResponse(

    @SerializedName("success")
    val success: Boolean,

    @SerializedName("service")
    val service: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null
)
