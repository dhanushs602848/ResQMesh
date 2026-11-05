package com.resqmesh.app.data.repository

import android.util.Log
import com.resqmesh.app.data.ble.BleBroadcastSummary
import com.resqmesh.app.data.ble.BleManager
import com.resqmesh.app.data.ble.BleServiceStatus
import com.resqmesh.app.data.ble.MeshRelayEngine
import com.resqmesh.app.data.ble.RelayDecision
import com.resqmesh.app.data.ble.RelayResult
import com.resqmesh.app.data.local.dao.IncidentDao
import com.resqmesh.app.data.local.entity.IncidentEntity
import com.resqmesh.app.data.location.LocationState
import com.resqmesh.app.data.location.LocationStatus
import com.resqmesh.app.data.location.LocationTracker
import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.app.data.model.NodeStatus
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.data.model.UploadStatus
import com.resqmesh.app.data.remote.ResQMeshApi
import com.resqmesh.app.data.remote.RetrofitClient
import com.resqmesh.app.data.remote.dto.IncidentUploadRequest
import com.resqmesh.app.data.remote.dto.LocationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clean architectural contract for Mesh, BLE, GPS, and SOS communication.
 */
interface MeshRepository {
    fun getDiscoveredNodes(): Flow<List<MeshNode>>
    fun getEmergencyBroadcasts(): Flow<List<EmergencyMessage>>
    fun getSystemTelemetry(): Flow<SystemTelemetry>
    fun getLastKnownLocation(): Flow<GeoCoordinate?>
    fun getLocationState(): Flow<LocationState>
    fun getPendingUploadCount(): Flow<Int>
    fun getLatestUploadStatus(): Flow<UploadStatus?>
    fun getBleStatus(): Flow<BleServiceStatus>

    suspend fun startDiscovery()
    suspend fun stopDiscovery()

    suspend fun broadcastSos(
        priority: IncidentPriority,
        title: String,
        details: String,
        coordinate: GeoCoordinate?
    ): Result<String>

    suspend fun retryPendingUploads(): Result<Int>
    suspend fun cancelSos(): Result<Unit>
    suspend fun acknowledgeMessage(messageId: String): Result<Unit>
    suspend fun refreshTopology()
}

/**
 * Production implementation integrating actual hardware BLE scanning and advertising,
 * persistent Room offline queueing, and backend cloud uplink.
 */
@Singleton
class DefaultMeshRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val api: ResQMeshApi,
    private val bleManager: BleManager,
    private val meshRelayEngine: MeshRelayEngine,
    private val locationTracker: LocationTracker
) : MeshRepository {

    // Constructor passing (incidentDao, api, bleManager, meshRelayEngine)
    constructor(
        incidentDao: IncidentDao,
        api: ResQMeshApi,
        bleManager: BleManager,
        meshRelayEngine: MeshRelayEngine
    ) : this(
        incidentDao = incidentDao,
        api = api,
        bleManager = bleManager,
        meshRelayEngine = meshRelayEngine,
        locationTracker = createDefaultMockLocationTracker()
    )

    // Secondary constructor for tests passing (incidentDao, api, bleManager)
    constructor(
        incidentDao: IncidentDao,
        api: ResQMeshApi,
        bleManager: BleManager
    ) : this(
        incidentDao = incidentDao,
        api = api,
        bleManager = bleManager,
        meshRelayEngine = object : MeshRelayEngine {
            override suspend fun processAndRelay(packet: MeshPacket, incomingPeerAddress: String?): RelayResult =
                RelayResult(packet.messageId, RelayDecision.SKIPPED_NO_PEERS, null, null, "Default mock relay engine")
            override fun isAlreadyRelayed(messageId: String): Boolean = false
            override fun resetDuplicateTracking() {}
        },
        locationTracker = createDefaultMockLocationTracker()
    )

    // Zero-arg constructor for unit testing without Dagger/Hilt
    constructor() : this(
        incidentDao = object : IncidentDao {
            override suspend fun insertOrUpdate(incident: IncidentEntity) {}
            override suspend fun getPendingIncidents(): List<IncidentEntity> = emptyList()
            override fun getAllIncidents(): Flow<List<IncidentEntity>> = flowOf(emptyList())
            override suspend fun getIncidentById(id: String): IncidentEntity? = null
            override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
            override suspend fun markUploaded(id: String) {}
            override fun getPendingCount(): Flow<Int> = flowOf(0)
            override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
        },
        api = RetrofitClient.api,
        bleManager = object : BleManager {
            override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
            override val bleStatus = MutableStateFlow(BleServiceStatus())
            override val localNodeId: String = "RQM-TEST"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket): BleBroadcastSummary {
                return BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
            }
        },
        meshRelayEngine = object : MeshRelayEngine {
            override suspend fun processAndRelay(packet: MeshPacket, incomingPeerAddress: String?): RelayResult =
                RelayResult(packet.messageId, RelayDecision.SKIPPED_NO_PEERS, null, null, "Default mock relay engine")
            override fun isAlreadyRelayed(messageId: String): Boolean = false
            override fun resetDuplicateTracking() {}
        },
        locationTracker = createDefaultMockLocationTracker()
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _latestUploadStatus = MutableStateFlow<UploadStatus?>(null)

    // Demo alerts remain for fallback UI context until remote peers broadcast genuine emergency signals
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
                location = GeoCoordinate(12.9716, 77.5946, 920.0, 4.5f),
                uploadStatus = UploadStatus.NOT_APPLICABLE,
                isDemo = true
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
                location = GeoCoordinate(12.9820, 77.6080),
                uploadStatus = UploadStatus.NOT_APPLICABLE,
                isDemo = true
            ),
            EmergencyMessage(
                id = "BC-720",
                senderId = "LOCAL-HOST",
                senderName = "Mesh Router Daemon",
                title = "Mesh Routing Table Synchronized",
                content = "3 peer relay nodes linked into autonomous multi-hop message forwarding group.",
                timestamp = System.currentTimeMillis() - 2040000,
                priority = IncidentPriority.INFO,
                hopCount = 0,
                uploadStatus = UploadStatus.NOT_APPLICABLE,
                isDemo = true
            )
        )
    )

    init {
        observeInboundPackets()
    }

    private fun observeInboundPackets() {
        repositoryScope.launch {
            bleManager.inboundPackets.collect { packet ->
                handleInboundPacket(packet)
            }
        }
    }

    internal suspend fun handleInboundPacket(packet: MeshPacket) {
        when (packet.packetType) {
            MeshPacketType.SOS -> handleInboundSos(packet)
            MeshPacketType.ACK -> handleInboundAck(packet)
        }
    }

    internal suspend fun handleInboundSos(packet: MeshPacket) {
        // 1. Validate packet invariants
        try {
            packet.validate()
        } catch (e: Exception) {
            Log.w("MeshRepository", "Rejecting malformed inbound SOS packet [${packet.messageId}]: ${e.message}")
            return
        }

        // 2. Prevent duplicate broadcast addition
        if (_broadcasts.value.any { it.id == packet.messageId }) {
            Log.d("MeshRepository", "Duplicate inbound SOS packet [${packet.messageId}] ignored")
            return
        }

        val location = if (packet.latitude != null && packet.longitude != null) {
            GeoCoordinate(latitude = packet.latitude, longitude = packet.longitude)
        } else {
            null
        }

        val emergencyMessage = EmergencyMessage(
            id = packet.messageId,
            senderId = packet.senderNodeId,
            senderName = "Peer ${packet.senderNodeId.takeLast(6)}",
            title = packet.title,
            content = packet.content,
            timestamp = packet.timestamp,
            priority = packet.priority,
            hopCount = packet.hopCount,
            location = location,
            uploadStatus = UploadStatus.PENDING,
            originNodeId = packet.originNodeId,
            isReceived = true,
            isDemo = false
        )

        // Prepend to broadcast list so UI updates immediately
        _broadcasts.value = listOf(emergencyMessage) + _broadcasts.value

        // 3. Save to Room database offline queue
        val isoTimestamp = try {
            java.time.Instant.ofEpochMilli(packet.timestamp).toString()
        } catch (_: Exception) {
            null
        }

        val entity = IncidentEntity(
            id = packet.messageId,
            senderId = packet.senderNodeId,
            senderName = "Peer ${packet.senderNodeId}",
            title = packet.title,
            content = packet.content,
            priority = packet.priority.name,
            latitude = packet.latitude,
            longitude = packet.longitude,
            accuracy = null,
            timestamp = packet.timestamp,
            isoTimestamp = isoTimestamp,
            uploadStatus = "PENDING",
            originNodeId = packet.originNodeId,
            hopCount = packet.hopCount,
            isReceived = true
        )

        try {
            incidentDao.insertOrUpdate(entity)
            Log.i("MeshRepository", "Inbound SOS [${packet.messageId}] persisted to Room DB")
        } catch (e: Exception) {
            Log.e("MeshRepository", "Failed to persist inbound SOS to Room: ${e.message}")
        }

        // 4. Evaluate and perform Multi-Hop Mesh Relay in background without delaying local alert display or Room persistence
        repositoryScope.launch {
            try {
                val relayResult = meshRelayEngine.processAndRelay(packet)
                Log.i("MeshRepository", "Relay evaluation for [${packet.messageId}]: ${relayResult.decision} - ${relayResult.message}")
                if (relayResult.decision == RelayDecision.RELAYED) {
                    _telemetry.value = _telemetry.value.copy(
                        totalPacketsRelayed = _telemetry.value.totalPacketsRelayed + 1
                    )
                }
            } catch (e: Exception) {
                Log.e("MeshRepository", "Unexpected exception during mesh relay of [${packet.messageId}]: ${e.message}")
            }
        }

        // 5. Asynchronously generate and transmit BLE ACK back towards the sender/originator
        repositoryScope.launch {
            sendAckForInboundSos(packet)
        }
    }

    internal suspend fun sendAckForInboundSos(
        sosPacket: MeshPacket,
        maxRetries: Int = 2
    ) {
        // Never ACK our own local SOS
        if (sosPacket.originNodeId == bleManager.localNodeId) return

        val ackPacket = try {
            MeshPacket.createAck(sosPacket, bleManager.localNodeId)
        } catch (e: Exception) {
            Log.w("MeshRepository", "Failed to construct ACK packet for [${sosPacket.messageId}]: ${e.message}")
            return
        }

        var attempt = 0
        var delivered = false

        while (attempt < maxRetries && !delivered) {
            attempt++
            try {
                val summary = bleManager.dispatchMeshPacket(ackPacket)
                if (summary.isDeliveredToAnyPeer) {
                    delivered = true
                    Log.i("MeshRepository", "ACK for [${sosPacket.messageId}] delivered on attempt $attempt")
                } else {
                    Log.d("MeshRepository", "ACK dispatch attempt $attempt for [${sosPacket.messageId}] delivered to 0 peers")
                    if (attempt < maxRetries) {
                        delay(1000L)
                    }
                }
            } catch (e: Exception) {
                Log.w("MeshRepository", "Exception on ACK dispatch attempt $attempt for [${sosPacket.messageId}]: ${e.message}")
                if (attempt < maxRetries) {
                    delay(1000L)
                }
            }
        }
    }

    internal suspend fun handleInboundAck(packet: MeshPacket) {
        // 1. Invariant validation
        try {
            packet.validate()
        } catch (e: Exception) {
            Log.w("MeshRepository", "Rejecting malformed inbound ACK packet [${packet.messageId}]: ${e.message}")
            return
        }

        val originalMessageId = packet.getOriginalMessageId() ?: return
        val acknowledgingNodeId = packet.getAcknowledgingNodeId() ?: packet.senderNodeId

        Log.i("MeshRepository", "Received ACK [${packet.messageId}] for original SOS [$originalMessageId] from node [$acknowledgingNodeId]")

        // 2. Check if this node originated the referenced SOS
        val matchingBroadcast = _broadcasts.value.firstOrNull { it.id == originalMessageId }
        val isOriginatedBySelf = packet.originNodeId == bleManager.localNodeId ||
                (matchingBroadcast != null && !matchingBroadcast.isReceived)

        if (isOriginatedBySelf && matchingBroadcast != null) {
            // Update in-memory broadcast with new acknowledging node if not already present
            val currentAcks = matchingBroadcast.acknowledgedBy
            if (!currentAcks.contains(acknowledgingNodeId)) {
                val updatedAcks = currentAcks + acknowledgingNodeId
                val ackTimestamp = System.currentTimeMillis()
                _broadcasts.value = _broadcasts.value.map { msg ->
                    if (msg.id == originalMessageId) {
                        msg.copy(
                            isAcknowledged = true,
                            acknowledgedBy = updatedAcks,
                            acknowledgedAt = ackTimestamp
                        )
                    } else msg
                }
                Log.i("MeshRepository", "Marked local SOS [$originalMessageId] as ACKNOWLEDGED by [$acknowledgingNodeId] (Total acks: ${updatedAcks.size})")

                // Update Room persistence
                try {
                    incidentDao.addAcknowledgment(originalMessageId, acknowledgingNodeId, ackTimestamp)
                } catch (e: Exception) {
                    Log.e("MeshRepository", "Failed to update Room acknowledgment for [$originalMessageId]: ${e.message}")
                }
            }
        } else {
            // 3. This node was not the originator; relay the ACK packet onward towards originNodeId
            repositoryScope.launch {
                try {
                    val relayResult = meshRelayEngine.processAndRelay(packet)
                    Log.i("MeshRepository", "ACK relay evaluation for [${packet.messageId}]: ${relayResult.decision} - ${relayResult.message}")
                } catch (e: Exception) {
                    Log.e("MeshRepository", "Unexpected exception during ACK relay of [${packet.messageId}]: ${e.message}")
                }
            }
        }
    }

    private val _telemetry = MutableStateFlow(
        SystemTelemetry(
            isBluetoothEnabled = false,
            isLocationEnabled = true,
            isMeshRelayActive = false,
            batteryPercent = 88,
            connectedPeerCount = 0,
            totalPacketsRelayed = 0,
            activeSosBeacon = false
        )
    )

    // Real discovered peers from BluetoothLeScanner
    override fun getDiscoveredNodes(): Flow<List<MeshNode>> = bleManager.discoveredNodes

    override fun getEmergencyBroadcasts(): Flow<List<EmergencyMessage>> = _broadcasts.asStateFlow()

    // Dynamically combines base telemetry with real hardware BLE state, live peer count, and GPS status
    override fun getSystemTelemetry(): Flow<SystemTelemetry> {
        return combine(
            _telemetry,
            bleManager.discoveredNodes,
            bleManager.bleStatus,
            locationTracker.locationState
        ) { base, nodes, ble, locState ->
            base.copy(
                isBluetoothEnabled = ble.isBluetoothEnabled,
                isLocationEnabled = locState.status != LocationStatus.LOCATION_DISABLED &&
                    locState.status != LocationStatus.PERMISSION_DENIED &&
                    locState.status != LocationStatus.UNAVAILABLE,
                isMeshRelayActive = ble.isAdvertising,
                connectedPeerCount = nodes.size
            )
        }
    }

    override fun getLastKnownLocation(): Flow<GeoCoordinate?> = locationTracker.locationState.map { it.coordinate }
    override fun getLocationState(): Flow<LocationState> = locationTracker.locationState
    override fun getLatestUploadStatus(): Flow<UploadStatus?> = _latestUploadStatus.asStateFlow()
    override fun getPendingUploadCount(): Flow<Int> = incidentDao.getPendingCount()
    override fun getBleStatus(): Flow<BleServiceStatus> = bleManager.bleStatus

    override suspend fun startDiscovery() {
        bleManager.startMeshDiscovery()
        locationTracker.startTracking()
    }

    override suspend fun stopDiscovery() {
        bleManager.stopMeshDiscovery()
        locationTracker.stopTracking()
    }

    override suspend fun broadcastSos(
        priority: IncidentPriority,
        title: String,
        details: String,
        coordinate: GeoCoordinate?
    ): Result<String> {
        val newId = "SOS-${System.currentTimeMillis() % 10000}"
        val now = System.currentTimeMillis()
        val resolvedCoordinate = coordinate ?: locationTracker.getCurrentCoordinate()

        val newMsg = EmergencyMessage(
            id = newId,
            senderId = bleManager.localNodeId,
            senderName = "ResQ Operator (${bleManager.localNodeId})",
            title = title,
            content = details,
            timestamp = now,
            priority = priority,
            hopCount = 0,
            location = resolvedCoordinate,
            uploadStatus = UploadStatus.PENDING
        )

        // 1. Immediately preserve offline local SOS broadcast and activate beacon
        _broadcasts.value = listOf(newMsg) + _broadcasts.value
        _telemetry.value = _telemetry.value.copy(activeSosBeacon = true)
        _latestUploadStatus.value = UploadStatus.PENDING

        // 2. Persist into Room queue
        val isoTimestamp = try {
            java.time.Instant.ofEpochMilli(now).toString()
        } catch (_: Exception) {
            null
        }

        val entity = IncidentEntity(
            id = newId,
            senderId = newMsg.senderId,
            senderName = newMsg.senderName,
            title = newMsg.title,
            content = newMsg.content,
            priority = priority.name,
            latitude = resolvedCoordinate?.latitude,
            longitude = resolvedCoordinate?.longitude,
            accuracy = resolvedCoordinate?.accuracyMeters,
            timestamp = now,
            isoTimestamp = isoTimestamp,
            uploadStatus = "PENDING"
        )

        // 3. Asynchronously persist and attempt uplink without blocking caller or main thread
        repositoryScope.launch {
            try {
                incidentDao.insertOrUpdate(entity)
            } catch (e: Exception) {
                Log.e("MeshRepository", "Room persistence exception: ${e.message}")
            }
            performUpload(newId, entity)
        }

        // 4. Concurrently attempt 1-hop BLE GATT dispatch to discovered peers
        repositoryScope.launch {
            try {
                val packet = MeshPacket(
                    messageId = newId,
                    originNodeId = bleManager.localNodeId,
                    senderNodeId = bleManager.localNodeId,
                    packetType = MeshPacketType.SOS,
                    priority = priority,
                    title = title,
                    content = details,
                    latitude = resolvedCoordinate?.latitude,
                    longitude = resolvedCoordinate?.longitude,
                    timestamp = now,
                    hopCount = 0,
                    maxHops = MeshPacket.DEFAULT_MAX_HOPS
                )
                val summary = bleManager.dispatchMeshPacket(packet)
                Log.i("MeshRepository", "1-hop BLE SOS dispatch for $newId complete: ${summary.successfulDeliveries}/${summary.totalPeersAttempted} delivered")
            } catch (e: Exception) {
                Log.e("MeshRepository", "Exception during 1-hop BLE SOS dispatch for $newId: ${e.message}")
            }
        }

        return Result.success(newId)
    }

    private suspend fun performUpload(id: String, entity: IncidentEntity) {
        withContext(Dispatchers.IO) {
            updateMessageStatus(id, UploadStatus.UPLOADING)
            try {
                incidentDao.updateStatus(id, "UPLOADING")
            } catch (_: Exception) {}

            val locationDto = if (entity.latitude != null && entity.longitude != null) {
                LocationDto(
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    accuracy = entity.accuracy
                )
            } else null

            val request = IncidentUploadRequest(
                id = entity.id,
                senderId = entity.senderId,
                senderName = entity.senderName,
                title = entity.title,
                content = entity.content,
                priority = entity.priority,
                location = locationDto,
                timestamp = entity.isoTimestamp
            )

            try {
                val response = api.uploadIncident(request)
                val body = response.body()
                if (response.isSuccessful && (body?.success == true || body?.duplicate == true)) {
                    Log.i("MeshRepository", "Incident $id successfully uploaded to backend (duplicate=${body?.duplicate})")
                    updateMessageStatus(id, UploadStatus.UPLOADED)
                    try {
                        incidentDao.markUploaded(id)
                    } catch (_: Exception) {}
                } else {
                    val code = response.code()
                    val errorMsg = "HTTP $code"
                    Log.w("MeshRepository", "Backend returned error for incident $id: $errorMsg")
                    updateMessageStatus(id, UploadStatus.FAILED)
                    try {
                        incidentDao.updateStatus(id, "FAILED", errorMsg)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                val errorMsg = "Network unreachable: ${e.localizedMessage ?: e.message}"
                Log.w("MeshRepository", "Offline/network failure uploading incident $id: $errorMsg")
                updateMessageStatus(id, UploadStatus.FAILED)
                try {
                    incidentDao.updateStatus(id, "FAILED", errorMsg)
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateMessageStatus(id: String, status: UploadStatus) {
        _latestUploadStatus.value = status
        _broadcasts.value = _broadcasts.value.map { msg ->
            if (msg.id == id) msg.copy(uploadStatus = status) else msg
        }
    }

    override suspend fun retryPendingUploads(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val pending = incidentDao.getPendingIncidents()
                var successCount = 0
                for (item in pending) {
                    performUpload(item.id, item)
                    val updated = incidentDao.getIncidentById(item.id)
                    if (updated?.uploadStatus == "UPLOADED") {
                        successCount++
                    }
                }
                Result.success(successCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelSos(): Result<Unit> {
        _telemetry.value = _telemetry.value.copy(activeSosBeacon = false)
        return Result.success(Unit)
    }

    override suspend fun acknowledgeMessage(messageId: String): Result<Unit> {
        _broadcasts.value = _broadcasts.value.map {
            if (it.id == messageId) {
                val currentAcks = it.acknowledgedBy
                val updatedAcks = if (currentAcks.isEmpty()) listOf("LOCAL") else currentAcks
                it.copy(
                    isAcknowledged = true,
                    acknowledgedBy = updatedAcks,
                    acknowledgedAt = System.currentTimeMillis()
                )
            } else it
        }
        return Result.success(Unit)
    }

    override suspend fun refreshTopology() {
        bleManager.refreshDiscovery()
    }

    companion object {
        fun createDefaultMockLocationTracker(): LocationTracker {
            return object : LocationTracker {
                private val _state = MutableStateFlow(LocationState(status = LocationStatus.SEARCHING))
                override val locationState: StateFlow<LocationState> = _state.asStateFlow()
                override fun startTracking() {}
                override fun stopTracking() {}
                override fun refreshLocation() {}
                override fun getCurrentCoordinate(): GeoCoordinate? = _state.value.coordinate
            }
        }
    }
}
