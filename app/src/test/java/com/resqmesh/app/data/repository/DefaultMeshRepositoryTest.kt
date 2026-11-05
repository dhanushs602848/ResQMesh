package com.resqmesh.app.data.repository

import com.resqmesh.app.data.ble.BleManager
import com.resqmesh.app.data.ble.BleServiceStatus
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.NodeStatus
import com.resqmesh.app.data.model.UploadStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultMeshRepositoryTest {

    private lateinit var repository: DefaultMeshRepository

    @Before
    fun setUp() {
        repository = DefaultMeshRepository()
    }

    @Test
    fun getDiscoveredNodes_returnsEmptyInitially() = runBlocking {
        val nodes = repository.getDiscoveredNodes().first()
        assertTrue("Initial real peer discovery should start empty until hardware detects peers", nodes.isEmpty())
    }

    @Test
    fun getEmergencyBroadcasts_returnsInitialBroadcasts() = runBlocking {
        val broadcasts = repository.getEmergencyBroadcasts().first()
        assertTrue("Repository should have initial broadcasts", broadcasts.isNotEmpty())
        assertEquals(3, broadcasts.size)
    }

    @Test
    fun getSystemTelemetry_returnsHealthyDefaults() = runBlocking {
        val telemetry = repository.getSystemTelemetry().first()
        assertTrue(telemetry.isLocationEnabled)
        assertFalse(telemetry.activeSosBeacon)
        assertEquals(0, telemetry.connectedPeerCount)
    }

    @Test
    fun broadcastSos_addsBroadcastAndActivatesBeacon() = runBlocking {
        val testCoord = GeoCoordinate(12.9716, 77.5946)
        val result = repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "TEST CRITICAL SOS",
            details = "Testing emergency beacon broadcast",
            coordinate = testCoord
        )

        assertTrue(result.isSuccess)
        val broadcasts = repository.getEmergencyBroadcasts().first()
        val latest = broadcasts.first()
        assertEquals("TEST CRITICAL SOS", latest.title)
        assertEquals(IncidentPriority.CRITICAL, latest.priority)
        assertEquals(testCoord, latest.location)
        assertTrue(latest.uploadStatus == UploadStatus.PENDING || latest.uploadStatus == UploadStatus.UPLOADING)

        val telemetry = repository.getSystemTelemetry().first()
        assertTrue("Telemetry activeSosBeacon must be true after SOS", telemetry.activeSosBeacon)
    }

    @Test
    fun cancelSos_deactivatesBeacon() = runBlocking {
        repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "TEST SOS",
            details = "Testing",
            coordinate = null
        )
        assertTrue(repository.getSystemTelemetry().first().activeSosBeacon)

        val cancelResult = repository.cancelSos()
        assertTrue(cancelResult.isSuccess)
        assertFalse("Telemetry activeSosBeacon must be false after cancel", repository.getSystemTelemetry().first().activeSosBeacon)
    }

    @Test
    fun acknowledgeMessage_marksMessageAcknowledged() = runBlocking {
        val broadcasts = repository.getEmergencyBroadcasts().first()
        val targetId = broadcasts.first().id

        val ackResult = repository.acknowledgeMessage(targetId)
        assertTrue(ackResult.isSuccess)

        val updatedBroadcasts = repository.getEmergencyBroadcasts().first()
        val target = updatedBroadcasts.first { it.id == targetId }
        assertTrue("Message should be marked acknowledged", target.isAcknowledged)
    }

    @Test
    fun getPendingUploadCount_returnsZeroForInitialRepository() = runBlocking {
        val count = repository.getPendingUploadCount().first()
        assertEquals(0, count)
    }

    @Test
    fun telemetry_dynamicallyReflectsDiscoveredPeers() = runBlocking {
        val testNodes = MutableStateFlow<List<MeshNode>>(emptyList())
        val mockBle = object : BleManager {
            override val discoveredNodes = testNodes
            override val bleStatus = MutableStateFlow(BleServiceStatus(isBluetoothEnabled = true, isAdvertising = true))
            override val localNodeId = "RQM-TEST"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket): com.resqmesh.app.data.ble.BleBroadcastSummary {
                return com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
            }
        }

        val customRepo = DefaultMeshRepository(
            incidentDao = repository.let {
                object : com.resqmesh.app.data.local.dao.IncidentDao {
                    override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {}
                    override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                    override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                    override suspend fun getIncidentById(id: String) = null
                    override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                    override suspend fun markUploaded(id: String) {}
                    override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                    override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
                }
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = mockBle
        )

        assertEquals(0, customRepo.getSystemTelemetry().first().connectedPeerCount)

        testNodes.value = listOf(
            MeshNode("AA:BB:CC:DD:EE:01", "Peer 1", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE),
            MeshNode("AA:BB:CC:DD:EE:02", "Peer 2", -75, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val updatedTelemetry = customRepo.getSystemTelemetry().first()
        assertEquals(2, updatedTelemetry.connectedPeerCount)
        assertTrue(updatedTelemetry.isBluetoothEnabled)
        assertTrue(updatedTelemetry.isMeshRelayActive)
    }

    @Test
    fun handleInboundPacket_validSos_addsBroadcastAndPersistsToRoom() = runBlocking {
        val insertedEntities = mutableListOf<com.resqmesh.app.data.local.entity.IncidentEntity>()
        val mockBle = object : BleManager {
            override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
            override val bleStatus = MutableStateFlow(BleServiceStatus())
            override val localNodeId = "RQM-LOCAL"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket) =
                com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
        }

        val testRepo = DefaultMeshRepository(
            incidentDao = object : com.resqmesh.app.data.local.dao.IncidentDao {
                override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {
                    insertedEntities.add(incident)
                }
                override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                override suspend fun getIncidentById(id: String) = null
                override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                override suspend fun markUploaded(id: String) {}
                override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = mockBle
        )

        val inboundPacket = MeshPacket(
            messageId = "SOS-INBOUND-1",
            originNodeId = "RQM-REMOTE-99",
            senderNodeId = "RQM-RELAY-12",
            packetType = com.resqmesh.app.data.model.MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Flash Flood Inbound Distress",
            content = "Need immediate rescue boat at low-lying riverbank coordinates.",
            latitude = 12.9800,
            longitude = 77.6100,
            timestamp = 1730855000000L,
            hopCount = 1,
            maxHops = 7
        )

        testRepo.handleInboundPacket(inboundPacket)

        val broadcasts = testRepo.getEmergencyBroadcasts().first()
        val latest = broadcasts.first()

        assertEquals("SOS-INBOUND-1", latest.id)
        assertEquals("Flash Flood Inbound Distress", latest.title)
        assertEquals(IncidentPriority.CRITICAL, latest.priority)
        assertTrue("Inbound alert should be marked as received", latest.isReceived)
        assertFalse("Inbound alert should not be marked as demo", latest.isDemo)
        assertEquals("RQM-REMOTE-99", latest.originNodeId)
        assertEquals(1, latest.hopCount)
        assertEquals(12.9800, latest.location!!.latitude, 0.0001)

        assertEquals("Should persist to Room database", 1, insertedEntities.size)
        val persisted = insertedEntities.first()
        assertEquals("SOS-INBOUND-1", persisted.id)
        assertTrue(persisted.isReceived)
        assertEquals("RQM-REMOTE-99", persisted.originNodeId)
        assertEquals(1, persisted.hopCount)
    }

    @Test
    fun handleInboundPacket_ackPacket_isFilteredOutAndNotAddedToBroadcasts() = runBlocking {
        val testRepo = DefaultMeshRepository()
        val initialCount = testRepo.getEmergencyBroadcasts().first().size

        val ackPacket = MeshPacket(
            messageId = "ACK-SOS-999",
            originNodeId = "RQM-SENDER",
            senderNodeId = "RQM-RECEIVER",
            packetType = com.resqmesh.app.data.model.MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK: SOS Received",
            content = "Packet acknowledged",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 5
        )

        testRepo.handleInboundPacket(ackPacket)

        val broadcastsAfter = testRepo.getEmergencyBroadcasts().first()
        assertEquals("ACK packets must not be added to emergency broadcasts", initialCount, broadcastsAfter.size)
    }

    @Test
    fun handleInboundPacket_duplicateSos_isIgnored() = runBlocking {
        val insertedEntities = mutableListOf<com.resqmesh.app.data.local.entity.IncidentEntity>()
        val mockBle = object : BleManager {
            override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
            override val bleStatus = MutableStateFlow(BleServiceStatus())
            override val localNodeId = "RQM-LOCAL"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket) =
                com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
        }

        val testRepo = DefaultMeshRepository(
            incidentDao = object : com.resqmesh.app.data.local.dao.IncidentDao {
                override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {
                    insertedEntities.add(incident)
                }
                override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                override suspend fun getIncidentById(id: String) = null
                override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                override suspend fun markUploaded(id: String) {}
                override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = mockBle
        )

        val inboundPacket = MeshPacket(
            messageId = "SOS-DUP-TEST",
            originNodeId = "RQM-PEER-1",
            senderNodeId = "RQM-PEER-1",
            packetType = com.resqmesh.app.data.model.MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Test Duplicate Incident",
            content = "Testing deduplication",
            latitude = null,
            longitude = null,
            timestamp = 1730855000000L,
            hopCount = 0,
            maxHops = 5
        )

        testRepo.handleInboundPacket(inboundPacket)
        val countAfterFirst = testRepo.getEmergencyBroadcasts().first().size

        // Send duplicate packet
        testRepo.handleInboundPacket(inboundPacket)
        val countAfterSecond = testRepo.getEmergencyBroadcasts().first().size

        assertEquals("Duplicate packet should not increase broadcast count", countAfterFirst, countAfterSecond)
        assertEquals("Duplicate should not be re-inserted into Room", 1, insertedEntities.size)
    }

    @Test
    fun handleInboundPacket_withoutGps_addsBroadcastCleanlyWithNullLocation() = runBlocking {
        val testRepo = DefaultMeshRepository()

        val packetWithoutGps = MeshPacket(
            messageId = "SOS-NO-GPS",
            originNodeId = "RQM-REMOTE-88",
            senderNodeId = "RQM-REMOTE-88",
            packetType = com.resqmesh.app.data.model.MeshPacketType.SOS,
            priority = IncidentPriority.NORMAL,
            title = "Advisory Without Coordinates",
            content = "Bridge closed due to road obstruction.",
            latitude = null,
            longitude = null,
            timestamp = 1730855000000L,
            hopCount = 0,
            maxHops = 5
        )

        testRepo.handleInboundPacket(packetWithoutGps)

        val latest = testRepo.getEmergencyBroadcasts().first().first()
        assertEquals("SOS-NO-GPS", latest.id)
        org.junit.Assert.assertNull("Location should be null when coordinates are absent", latest.location)
    }

    @Test
    fun handleInboundPacket_triggersMeshRelayEngine() = runBlocking {
        val relayedPackets = mutableListOf<MeshPacket>()
        val mockRelayEngine = object : com.resqmesh.app.data.ble.MeshRelayEngine {
            override suspend fun processAndRelay(
                packet: MeshPacket,
                incomingPeerAddress: String?
            ): com.resqmesh.app.data.ble.RelayResult {
                relayedPackets.add(packet)
                return com.resqmesh.app.data.ble.RelayResult(
                    packet.messageId,
                    com.resqmesh.app.data.ble.RelayDecision.RELAYED,
                    packet.copy(hopCount = packet.hopCount + 1),
                    null,
                    "Relayed in test"
                )
            }
            override fun isAlreadyRelayed(messageId: String): Boolean = false
            override fun resetDuplicateTracking() {}
        }

        val testRepo = DefaultMeshRepository(
            incidentDao = object : com.resqmesh.app.data.local.dao.IncidentDao {
                override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {}
                override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                override suspend fun getIncidentById(id: String) = null
                override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                override suspend fun markUploaded(id: String) {}
                override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = object : BleManager {
                override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
                override val bleStatus = MutableStateFlow(BleServiceStatus())
                override val localNodeId = "RQM-TEST-LOCAL"
                override val inboundPackets = MutableSharedFlow<MeshPacket>()
                override fun startMeshDiscovery() {}
                override fun stopMeshDiscovery() {}
                override fun refreshDiscovery() {}
                override suspend fun dispatchMeshPacket(packet: MeshPacket) =
                    com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
            },
            meshRelayEngine = mockRelayEngine
        )

        val inboundPacket = MeshPacket(
            messageId = "SOS-RELAY-TRIGGER",
            originNodeId = "RQM-ORIGIN-77",
            senderNodeId = "RQM-FORWARDER-88",
            packetType = com.resqmesh.app.data.model.MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Relay Integration Test",
            content = "Testing relay invocation from repository",
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = 1730855000000L,
            hopCount = 0,
            maxHops = 7
        )

        testRepo.handleInboundPacket(inboundPacket)

        // Allow background coroutine in repositoryScope to run
        kotlinx.coroutines.delay(200)

        assertEquals(1, relayedPackets.size)
        assertEquals("SOS-RELAY-TRIGGER", relayedPackets.first().messageId)
    }

    @Test
    fun getLastKnownLocation_initialState_returnsNullWithoutFabrication() = runBlocking {
        val initialLoc = repository.getLastKnownLocation().first()
        org.junit.Assert.assertNull("Initial repository location must be null; never fabricate coordinates", initialLoc)
    }

    @Test
    fun broadcastSos_withoutLocation_dispatchesSuccessfullyWithNullCoordinates() = runBlocking {
        val insertedEntities = mutableListOf<com.resqmesh.app.data.local.entity.IncidentEntity>()
        val dispatchedPackets = mutableListOf<MeshPacket>()

        val mockBle = object : BleManager {
            override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
            override val bleStatus = MutableStateFlow(BleServiceStatus())
            override val localNodeId = "RQM-TEST-NOGPS"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket): com.resqmesh.app.data.ble.BleBroadcastSummary {
                dispatchedPackets.add(packet)
                return com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
            }
        }

        val testRepo = DefaultMeshRepository(
            incidentDao = object : com.resqmesh.app.data.local.dao.IncidentDao {
                override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {
                    insertedEntities.add(incident)
                }
                override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                override suspend fun getIncidentById(id: String) = null
                override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                override suspend fun markUploaded(id: String) {}
                override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = mockBle
        )

        val result = testRepo.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "CRITICAL RESCUE NO GPS",
            details = "Trapped in bunker without satellite fix",
            coordinate = null
        )

        assertTrue("SOS dispatch must succeed immediately even without GPS coordinates", result.isSuccess)
        val broadcasts = testRepo.getEmergencyBroadcasts().first()
        val latest = broadcasts.first()
        org.junit.Assert.assertNull(latest.location)

        kotlinx.coroutines.delay(100)

        // Room persistence entity should have null coordinates
        assertEquals(1, insertedEntities.size)
        org.junit.Assert.assertNull(insertedEntities.first().latitude)
        org.junit.Assert.assertNull(insertedEntities.first().longitude)

        // Outbound BLE MeshPacket should have null coordinates
        assertEquals(1, dispatchedPackets.size)
        org.junit.Assert.assertNull(dispatchedPackets.first().latitude)
        org.junit.Assert.assertNull(dispatchedPackets.first().longitude)
    }

    @Test
    fun broadcastSos_withRealLocation_preservesCoordinatesInEntityAndPacket() = runBlocking {
        val insertedEntities = mutableListOf<com.resqmesh.app.data.local.entity.IncidentEntity>()
        val dispatchedPackets = mutableListOf<MeshPacket>()

        val mockBle = object : BleManager {
            override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
            override val bleStatus = MutableStateFlow(BleServiceStatus())
            override val localNodeId = "RQM-TEST-GPS"
            override val inboundPackets = MutableSharedFlow<MeshPacket>()
            override fun startMeshDiscovery() {}
            override fun stopMeshDiscovery() {}
            override fun refreshDiscovery() {}
            override suspend fun dispatchMeshPacket(packet: MeshPacket): com.resqmesh.app.data.ble.BleBroadcastSummary {
                dispatchedPackets.add(packet)
                return com.resqmesh.app.data.ble.BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
            }
        }

        val testRepo = DefaultMeshRepository(
            incidentDao = object : com.resqmesh.app.data.local.dao.IncidentDao {
                override suspend fun insertOrUpdate(incident: com.resqmesh.app.data.local.entity.IncidentEntity) {
                    insertedEntities.add(incident)
                }
                override suspend fun getPendingIncidents() = emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>()
                override fun getAllIncidents() = kotlinx.coroutines.flow.flowOf(emptyList<com.resqmesh.app.data.local.entity.IncidentEntity>())
                override suspend fun getIncidentById(id: String) = null
                override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
                override suspend fun markUploaded(id: String) {}
                override fun getPendingCount() = kotlinx.coroutines.flow.flowOf(0)
                override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {}
            },
            api = com.resqmesh.app.data.remote.RetrofitClient.api,
            bleManager = mockBle
        )

        val realCoord = GeoCoordinate(13.0827, 80.2707, 15.0, 3.5f)
        val result = testRepo.broadcastSos(
            priority = IncidentPriority.HIGH,
            title = "HIGH PRIORITY WITH REAL GPS",
            details = "Testing exact coordinate dispatch",
            coordinate = realCoord
        )

        assertTrue(result.isSuccess)
        val broadcasts = testRepo.getEmergencyBroadcasts().first()
        val latest = broadcasts.first()
        assertEquals(realCoord, latest.location)

        kotlinx.coroutines.delay(100)

        // Room entity has real coordinates and accuracy
        assertEquals(1, insertedEntities.size)
        assertEquals(13.0827, insertedEntities.first().latitude!!, 0.0001)
        assertEquals(80.2707, insertedEntities.first().longitude!!, 0.0001)
        assertEquals(3.5f, insertedEntities.first().accuracy!!, 0.01f)

        // Outbound BLE packet has real coordinates
        assertEquals(1, dispatchedPackets.size)
        assertEquals(13.0827, dispatchedPackets.first().latitude!!, 0.0001)
        assertEquals(80.2707, dispatchedPackets.first().longitude!!, 0.0001)
    }
}
