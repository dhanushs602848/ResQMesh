package com.resqmesh.app.data.ble

import com.resqmesh.app.data.local.dao.IncidentDao
import com.resqmesh.app.data.local.entity.IncidentEntity
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.InMemoryDuplicateDetector
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.app.data.model.NodeStatus
import com.resqmesh.app.data.remote.RetrofitClient
import com.resqmesh.app.data.repository.DefaultMeshRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AckSystemTest {

    private lateinit var mockBleManager: MockBleManager
    private lateinit var mockDao: MockIncidentDao
    private lateinit var relayEngine: DefaultMeshRelayEngine
    private lateinit var repository: DefaultMeshRepository

    private class MockBleManager(
        override val localNodeId: String = "RQM-LOCAL-10"
    ) : BleManager {
        override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
        override val bleStatus = MutableStateFlow(BleServiceStatus())
        override val inboundPackets = MutableSharedFlow<MeshPacket>()

        val dispatchedPackets = mutableListOf<MeshPacket>()
        var dispatchFailureCount = 0

        override fun startMeshDiscovery() {}
        override fun stopMeshDiscovery() {}
        override fun refreshDiscovery() {}

        override suspend fun dispatchMeshPacket(packet: MeshPacket): BleBroadcastSummary {
            return dispatchMeshPacket(packet, emptySet())
        }

        override suspend fun dispatchMeshPacket(
            packet: MeshPacket,
            excludePeerIds: Set<String>
        ): BleBroadcastSummary {
            dispatchedPackets.add(packet)
            return if (dispatchFailureCount > 0) {
                dispatchFailureCount--
                BleBroadcastSummary(packet.messageId, 1, 0, emptyList())
            } else {
                BleBroadcastSummary(
                    messageId = packet.messageId,
                    totalPeersAttempted = 1,
                    successfulDeliveries = 1,
                    results = listOf(BleDispatchResult("AA:BB:CC:DD:EE:01", "Peer 1", true, 1))
                )
            }
        }
    }

    private class MockIncidentDao : IncidentDao {
        val insertedEntities = mutableListOf<IncidentEntity>()
        val acknowledgedUpdates = mutableListOf<Triple<String, String, Long>>()

        override suspend fun insertOrUpdate(incident: IncidentEntity) {
            insertedEntities.removeIf { it.id == incident.id }
            insertedEntities.add(incident)
        }

        override suspend fun getPendingIncidents(): List<IncidentEntity> = emptyList()
        override fun getAllIncidents(): Flow<List<IncidentEntity>> = flowOf(emptyList())
        override suspend fun getIncidentById(id: String): IncidentEntity? = insertedEntities.firstOrNull { it.id == id }
        override suspend fun updateStatus(id: String, status: String, error: String?, attemptAt: Long?) {}
        override suspend fun markUploaded(id: String) {}
        override fun getPendingCount(): Flow<Int> = flowOf(0)

        override suspend fun updateAcknowledgment(id: String, acknowledgedBy: String, timestamp: Long) {
            acknowledgedUpdates.add(Triple(id, acknowledgedBy, timestamp))
            val existing = getIncidentById(id)
            if (existing != null) {
                val updated = existing.copy(isAcknowledged = true, acknowledgedBy = acknowledgedBy, acknowledgedAt = timestamp)
                insertOrUpdate(updated)
            }
        }
    }

    @Before
    fun setUp() {
        mockBleManager = MockBleManager()
        mockDao = MockIncidentDao()
        relayEngine = DefaultMeshRelayEngine(
            bleManager = mockBleManager,
            duplicateDetector = InMemoryDuplicateDetector(100, 60_000L)
        )
        repository = DefaultMeshRepository(
            incidentDao = mockDao,
            api = RetrofitClient.api,
            bleManager = mockBleManager,
            meshRelayEngine = relayEngine
        )
    }

    private fun createSampleSos(
        id: String = "SOS-ORIG-100",
        originNodeId: String = "RQM-PHONE-A",
        senderNodeId: String = "RQM-PHONE-A",
        hopCount: Int = 0,
        maxHops: Int = 7
    ): MeshPacket {
        return MeshPacket(
            messageId = id,
            originNodeId = originNodeId,
            senderNodeId = senderNodeId,
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Severe Flash Flood Warning",
            content = "Need immediate rescue boat at lowlands",
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = 1730850000000L,
            hopCount = hopCount,
            maxHops = maxHops
        )
    }

    @Test
    fun ackPacketCreation_validatesFieldsAndCarriesOriginalIds() {
        val sos = createSampleSos()
        val ack = MeshPacket.createAck(sos, "RQM-LOCAL-10")

        assertEquals(MeshPacketType.ACK, ack.packetType)
        assertTrue(ack.messageId.startsWith("ACK-SOS-ORIG-100"))
        assertEquals("RQM-PHONE-A", ack.originNodeId)
        assertEquals("RQM-LOCAL-10", ack.senderNodeId)
        assertEquals("SOS-ORIG-100", ack.getOriginalMessageId())
        assertEquals("RQM-LOCAL-10", ack.getAcknowledgingNodeId())
        assertEquals(0, ack.hopCount)
        assertEquals(sos.maxHops, ack.maxHops)
    }

    @Test
    fun automaticAck_sentOnValidInboundSosReception() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:01", "Remote Sender", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inboundSos = createSampleSos(
            id = "SOS-IN-1",
            originNodeId = "RQM-PHONE-REMOTE",
            senderNodeId = "RQM-PHONE-REMOTE"
        )

        repository.handleInboundPacket(inboundSos)

        // Give background repositoryScope time to transmit ACK
        delay(250)

        // Verify ACK packet was dispatched
        val ackDispatched = mockBleManager.dispatchedPackets.firstOrNull { it.packetType == MeshPacketType.ACK }
        assertNotNull("Automatic ACK must be dispatched on valid incoming SOS", ackDispatched)
        assertEquals("SOS-IN-1", ackDispatched!!.getOriginalMessageId())
        assertEquals("RQM-LOCAL-10", ackDispatched.getAcknowledgingNodeId())
        assertEquals("RQM-PHONE-REMOTE", ackDispatched.originNodeId)
    }

    @Test
    fun noAckSent_forDuplicateSos() = runBlocking {
        val inboundSos = createSampleSos(id = "SOS-DUP-1", originNodeId = "RQM-REMOTE-99")

        repository.handleInboundPacket(inboundSos)
        delay(200)
        val ackCountFirst = mockBleManager.dispatchedPackets.count { it.packetType == MeshPacketType.ACK }
        assertEquals(1, ackCountFirst)

        // Second delivery of same message ID
        repository.handleInboundPacket(inboundSos)
        delay(200)
        val ackCountSecond = mockBleManager.dispatchedPackets.count { it.packetType == MeshPacketType.ACK }
        assertEquals("Duplicate SOS should not trigger a second ACK", 1, ackCountSecond)
    }

    @Test
    fun noAckSent_forOwnLocalSos() = runBlocking {
        // SOS originated by this node
        val ownSos = createSampleSos(id = "SOS-OWN-1", originNodeId = mockBleManager.localNodeId)

        repository.sendAckForInboundSos(ownSos)
        delay(100)

        val ackCount = mockBleManager.dispatchedPackets.count { it.packetType == MeshPacketType.ACK }
        assertEquals("Node must never generate an ACK for its own SOS", 0, ackCount)
    }

    @Test
    fun inboundAckProcessing_marksLocalSosAsAcknowledged() = runBlocking {
        // 1. Locally broadcast an SOS
        repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "Test Local Emergency",
            details = "Testing ACK reception",
            coordinate = GeoCoordinate(12.9716, 77.5946)
        )

        val localSos = repository.getEmergencyBroadcasts().first().first()
        assertFalse("Initial local SOS should not be acknowledged", localSos.isAcknowledged)

        // 2. Incoming ACK arrives from remote peer acknowledging local SOS
        val ackPacket = MeshPacket(
            messageId = "ACK-${localSos.id}-RQM-PEER-B",
            originNodeId = mockBleManager.localNodeId,
            senderNodeId = "RQM-PEER-B",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-${localSos.id}",
            content = "RQM-PEER-B",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )

        repository.handleInboundPacket(ackPacket)

        val updatedSos = repository.getEmergencyBroadcasts().first().first { it.id == localSos.id }
        assertTrue("Local SOS must be marked acknowledged", updatedSos.isAcknowledged)
        assertEquals(listOf("RQM-PEER-B"), updatedSos.acknowledgedBy)
        assertNotNull(updatedSos.acknowledgedAt)
    }

    @Test
    fun multipleAcknowledgingPeers_accumulatesDistinctPeerIds() = runBlocking {
        repository.broadcastSos(
            priority = IncidentPriority.CRITICAL,
            title = "Multi-Recipient SOS",
            details = "Distress signal",
            coordinate = null
        )

        val localSos = repository.getEmergencyBroadcasts().first().first()

        // ACK from Peer B
        val ackB = MeshPacket(
            messageId = "ACK-${localSos.id}-PEER-B",
            originNodeId = mockBleManager.localNodeId,
            senderNodeId = "PEER-B",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-${localSos.id}",
            content = "PEER-B",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )
        repository.handleInboundPacket(ackB)

        // ACK from Peer C
        val ackC = MeshPacket(
            messageId = "ACK-${localSos.id}-PEER-C",
            originNodeId = mockBleManager.localNodeId,
            senderNodeId = "PEER-C",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-${localSos.id}",
            content = "PEER-C",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )
        repository.handleInboundPacket(ackC)

        // Duplicate ACK from Peer B
        repository.handleInboundPacket(ackB)

        val broadcast = repository.getEmergencyBroadcasts().first().first { it.id == localSos.id }
        assertEquals(2, broadcast.acknowledgedBy.size)
        assertTrue(broadcast.acknowledgedBy.contains("PEER-B"))
        assertTrue(broadcast.acknowledgedBy.contains("PEER-C"))
    }

    @Test
    fun ackPacketsNeverAppearAsEmergencyAlerts() = runBlocking {
        val initialAlertCount = repository.getEmergencyBroadcasts().first().size

        val ackPacket = MeshPacket(
            messageId = "ACK-ISOLATED-99",
            originNodeId = "RQM-OTHER",
            senderNodeId = "RQM-PEER",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-SOS-1234",
            content = "RQM-PEER",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 5
        )

        repository.handleInboundPacket(ackPacket)

        val alertCountAfter = repository.getEmergencyBroadcasts().first().size
        assertEquals("ACK packets must never be added to emergency alert feed", initialAlertCount, alertCountAfter)
    }

    @Test
    fun ackRelay_intermediateNodeForwardsAckTowardOriginator() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:ORIGIN", "RQM-ORIGIN-A", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // This node is an intermediate relay (RQM-LOCAL-10); origin is RQM-ORIGIN-A
        val ackFromPeerC = MeshPacket(
            messageId = "ACK-SOS-999-PEER-C",
            originNodeId = "RQM-ORIGIN-A",
            senderNodeId = "PEER-C",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-SOS-999",
            content = "PEER-C",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )

        val result = relayEngine.processAndRelay(ackFromPeerC, incomingPeerAddress = "11:22:33:44:55:PEER_C")

        assertEquals(RelayDecision.RELAYED, result.decision)
        assertNotNull(result.relayedPacket)
        assertEquals(1, result.relayedPacket!!.hopCount)
        assertEquals(mockBleManager.localNodeId, result.relayedPacket!!.senderNodeId)
        assertEquals("RQM-ORIGIN-A", result.relayedPacket!!.originNodeId)
    }

    @Test
    fun ackRelay_destinationReachedHaltsRelay() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Node 2", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Origin is this local node
        val ackForSelf = MeshPacket(
            messageId = "ACK-SOS-SELF-PEER-B",
            originNodeId = mockBleManager.localNodeId,
            senderNodeId = "PEER-B",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-SOS-SELF",
            content = "PEER-B",
            timestamp = System.currentTimeMillis(),
            hopCount = 1,
            maxHops = 7
        )

        val result = relayEngine.processAndRelay(ackForSelf)
        assertEquals(RelayDecision.DESTINATION_REACHED, result.decision)
        assertTrue(mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun ackLoopPrevention_dropsDuplicateAck() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:ORIGIN", "RQM-ORIGIN-A", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val ackPacket = MeshPacket(
            messageId = "ACK-ONCE-ONLY",
            originNodeId = "RQM-ORIGIN-A",
            senderNodeId = "PEER-C",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK-SOS-1",
            content = "PEER-C",
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )

        val result1 = relayEngine.processAndRelay(ackPacket)
        assertEquals(RelayDecision.RELAYED, result1.decision)

        val result2 = relayEngine.processAndRelay(ackPacket)
        assertEquals(RelayDecision.DROPPED_DUPLICATE, result2.decision)
    }

    @Test
    fun roomPersistence_savesAcknowledgmentRecord() = runBlocking {
        val initialEntity = IncidentEntity(
            id = "SOS-PERSIST-1",
            senderId = mockBleManager.localNodeId,
            senderName = "Local Operator",
            title = "Emergency in Sector 5",
            content = "Distress signal",
            priority = "CRITICAL",
            latitude = 12.9,
            longitude = 77.5,
            accuracy = 5f,
            timestamp = System.currentTimeMillis(),
            isoTimestamp = null,
            uploadStatus = "PENDING"
        )
        mockDao.insertOrUpdate(initialEntity)

        mockDao.addAcknowledgment("SOS-PERSIST-1", "RESPONDER-ALPHA", 1730855000000L)

        val updated = mockDao.getIncidentById("SOS-PERSIST-1")
        assertNotNull(updated)
        assertTrue(updated!!.isAcknowledged)
        assertEquals("RESPONDER-ALPHA", updated.acknowledgedBy)
        assertEquals(1730855000000L, updated.acknowledgedAt)
    }

    @Test
    fun bluetoothDispatchFailureAndRetryHandling() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:01", "Remote Node", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Configure 1 failure before succeeding on second attempt
        mockBleManager.dispatchFailureCount = 1

        val sos = createSampleSos(id = "SOS-RETRY-TEST", originNodeId = "RQM-REMOTE-33")
        repository.sendAckForInboundSos(sos, maxRetries = 2)

        // Dispatched should have been called twice (first failed, second succeeded)
        assertEquals(2, mockBleManager.dispatchedPackets.size)
    }
}
