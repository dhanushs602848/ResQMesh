package com.resqmesh.app.data.model

import androidx.compose.ui.graphics.Color
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.WarningAmber

/**
 * Representation of a discovered or linked BLE mesh node.
 * Designed for direct binding to team members' BLE GATT & advertising pipeline.
 */
data class MeshNode(
    val id: String,
    val name: String,
    val rssi: Int,
    val hopCount: Int,
    val isRelayCapable: Boolean,
    val lastSeenTimestamp: Long,
    val status: NodeStatus = NodeStatus.ONLINE
)

enum class NodeStatus {
    ONLINE,
    RELAYING,
    STALE,
    DISCONNECTED
}

/**
 * Emergency incident classification.
 */
enum class IncidentPriority(val displayName: String, val color: Color) {
    CRITICAL("CRITICAL DISTRESS", EmergencyRed),
    HIGH("HIGH PRIORITY", WarningAmber),
    NORMAL("ADVISORY", ElectricCyan),
    INFO("SYSTEM LOG", SafeGreen)
}

/**
 * Status of backend cloud uplink for newly generated emergency incidents.
 */
enum class UploadStatus(val displayName: String, val color: Color) {
    NOT_APPLICABLE("LOCAL ONLY", SafeGreen),
    PENDING("PENDING", WarningAmber),
    UPLOADING("UPLOADING", ElectricCyan),
    UPLOADED("UPLOADED", SafeGreen),
    FAILED("FAILED / RETRY QUEUED", WarningAmber)
}

/**
 * Emergency broadcast alert or peer message relayed over mesh.
 */
data class EmergencyMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val title: String,
    val content: String,
    val timestamp: Long,
    val priority: IncidentPriority,
    val hopCount: Int,
    val location: GeoCoordinate? = null,
    val isAcknowledged: Boolean = false,
    val uploadStatus: UploadStatus = UploadStatus.NOT_APPLICABLE,
    val originNodeId: String? = null,
    val isReceived: Boolean = false,
    val isDemo: Boolean = false,
    val acknowledgedBy: List<String> = emptyList(),
    val acknowledgedAt: Long? = null
)

/**
 * Geolocation coordinates for emergency pinpointing.
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tactical hardware state of local device.
 */
data class SystemTelemetry(
    val isBluetoothEnabled: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val isMeshRelayActive: Boolean = true,
    val batteryPercent: Int = 85,
    val connectedPeerCount: Int = 0,
    val totalPacketsRelayed: Int = 0,
    val activeSosBeacon: Boolean = false
)
