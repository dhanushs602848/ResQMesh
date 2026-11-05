package com.resqmesh.app.data.repository

import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.NodeStatus
import com.resqmesh.app.data.model.SystemTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clean architectural contract for Mesh, BLE, GPS, and SOS communication.
 * Team members working on BLE GATT or Location can directly implement or extend this interface.
 */
interface MeshRepository {
    fun getDiscoveredNodes(): Flow<List<MeshNode>>
    fun getEmergencyBroadcasts(): Flow<List<EmergencyMessage>>
    fun getSystemTelemetry(): Flow<SystemTelemetry>
    fun getLastKnownLocation(): Flow<GeoCoordinate?>

    suspend fun broadcastSos(
        priority: IncidentPriority,
        title: String,
        details: String,
        coordinate: GeoCoordinate?
    ): Result<String>

    suspend fun cancelSos(): Result<Unit>
    suspend fun acknowledgeMessage(messageId: String): Result<Unit>
    suspend fun refreshTopology()
}

/**
 * Default offline implementation providing clean initial demo telemetry
 * and clear integration stubs for Member 2 (BLE) and Member 3 (GPS/Backend).
 */
@Singleton
class DefaultMeshRepository @Inject constructor() : MeshRepository {

    private val _nodes = MutableStateFlow<List<MeshNode>>(
        listOf(
            MeshNode("RQM-042", "Relay Node Delta", -64, 1, true, System.currentTimeMillis() - 45000, NodeStatus.RELAYING),
            MeshNode("RQM-109", "Mobile Responder Unit", -78, 2, false, System.currentTimeMillis() - 120000, NodeStatus.ONLINE),
            MeshNode("RQM-312", "Emergency Hub Echo", -59, 1, true, System.currentTimeMillis() - 25000, NodeStatus.ONLINE)
        )
    )

    private val _broadcasts = MutableStateFlow<List<EmergencyMessage>>(
        listOf(
            EmergencyMessage(
                id = "BC-901",
                senderId = "RQM-NODE-ALPHA",
                senderName = "Forward Post Alpha",
                title = "Critical Medical Distress Broadcast",
                content = "Relayed SOS: First responder assistance required at Sector 4 North coordinates.",
                timestamp = System.currentTimeMillis() - 180000,
                priority = IncidentPriority.CRITICAL,
                hopCount = 2,
                location = GeoCoordinate(12.9716, 77.5946, 920.0, 4.5f)
            ),
            EmergencyMessage(
                id = "BC-844",
                senderId = "WEATHER-RELAY-02",
                senderName = "Telemetry Sentinel",
                title = "Flash Flood Advisory Alert",
                content = "Regional river water rise detected. Mesh routing switched to elevated bridge nodes.",
                timestamp = System.currentTimeMillis() - 1080000,
                priority = IncidentPriority.HIGH,
                hopCount = 3,
                location = GeoCoordinate(12.9820, 77.6080)
            ),
            EmergencyMessage(
                id = "BC-720",
                senderId = "LOCAL-HOST",
                senderName = "Mesh Router Daemon",
                title = "Mesh Routing Table Synchronized",
                content = "3 peer relay nodes linked into autonomous multi-hop message forwarding group.",
                timestamp = System.currentTimeMillis() - 2040000,
                priority = IncidentPriority.INFO,
                hopCount = 0
            )
        )
    )

    private val _telemetry = MutableStateFlow(
        SystemTelemetry(
            isBluetoothEnabled = true,
            isLocationEnabled = true,
            isMeshRelayActive = true,
            batteryPercent = 88,
            connectedPeerCount = 3,
            totalPacketsRelayed = 14,
            activeSosBeacon = false
        )
    )

    private val _location = MutableStateFlow<GeoCoordinate?>(
        GeoCoordinate(12.9716, 77.5946, 920.0, 5.0f)
    )

    override fun getDiscoveredNodes(): Flow<List<MeshNode>> = _nodes.asStateFlow()
    override fun getEmergencyBroadcasts(): Flow<List<EmergencyMessage>> = _broadcasts.asStateFlow()
    override fun getSystemTelemetry(): Flow<SystemTelemetry> = _telemetry.asStateFlow()
    override fun getLastKnownLocation(): Flow<GeoCoordinate?> = _location.asStateFlow()

    override suspend fun broadcastSos(
        priority: IncidentPriority,
        title: String,
        details: String,
        coordinate: GeoCoordinate?
    ): Result<String> {
        val newId = "SOS-${System.currentTimeMillis() % 10000}"
        val newMsg = EmergencyMessage(
            id = newId,
            senderId = "LOCAL-HOST",
            senderName = "ResQ Operator (Local Host)",
            title = title,
            content = details,
            timestamp = System.currentTimeMillis(),
            priority = priority,
            hopCount = 0,
            location = coordinate
        )
        _broadcasts.value = listOf(newMsg) + _broadcasts.value
        _telemetry.value = _telemetry.value.copy(activeSosBeacon = true)
        return Result.success(newId)
    }

    override suspend fun cancelSos(): Result<Unit> {
        _telemetry.value = _telemetry.value.copy(activeSosBeacon = false)
        return Result.success(Unit)
    }

    override suspend fun acknowledgeMessage(messageId: String): Result<Unit> {
        _broadcasts.value = _broadcasts.value.map {
            if (it.id == messageId) it.copy(isAcknowledged = true) else it
        }
        return Result.success(Unit)
    }

    override suspend fun refreshTopology() {
        // Trigger for member integration
    }
}
