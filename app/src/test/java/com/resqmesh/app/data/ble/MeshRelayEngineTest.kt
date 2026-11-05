package com.resqmesh.app.data.ble

import com.resqmesh.app.data.model.InMemoryDuplicateDetector
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.app.data.model.NodeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeshRelayEngineTest {

    private lateinit var mockBleManager: MockBleManager
    private lateinit var duplicateDetector: InMemoryDuplicateDetector
    private lateinit var relayEngine: DefaultMeshRelayEngine

    private class MockBleManager(
        override val localNodeId: String = "RQM-LOCAL-RELAY"
    ) : BleManager {
        override val discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
        override val bleStatus = MutableStateFlow(BleServiceStatus())
        override val inboundPackets = MutableSharedFlow<MeshPacket>()

        val dispatchedPackets = mutableListOf<MeshPacket>()
        val excludedPeerIdsRecorded = mutableListOf<Set<String>>()
        var dispatchReturnValue: BleBroadcastSummary? = null

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
            excludedPeerIdsRecorded.add(excludePeerIds)
            return dispatchReturnValue ?: BleBroadcastSummary(
                messageId = packet.messageId,
                totalPeersAttempted = 1,
                successfulDeliveries = 1,
                results = listOf(
                    BleDispatchResult("AA:BB:CC:DD:EE:02", "Remote Peer 2", true, 1)
                )
            )
        }
    }

    @Before
    fun setUp() {
        mockBleManager = MockBleManager()
        duplicateDetector = InMemoryDuplicateDetector(maxCapacity = 100, ttlMs = 60_000L)
        relayEngine = DefaultMeshRelayEngine(
            bleManager = mockBleManager,
            duplicateDetector = duplicateDetector
        )
    }

    private fun createInboundPacket(
        messageId: String = "SOS-9001",
        originNodeId: String = "RQM-ORIGIN-01",
        senderNodeId: String = "RQM-SENDER-02",
        packetType: MeshPacketType = MeshPacketType.SOS,
        hopCount: Int = 0,
        maxHops: Int = 7
    ): MeshPacket {
        return MeshPacket(
            messageId = messageId,
            originNodeId = originNodeId,
            senderNodeId = senderNodeId,
            packetType = packetType,
            priority = IncidentPriority.CRITICAL,
            title = "Flood Evacuation Distress",
            content = "Need immediate rescue boat at coordinates.",
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = 1730850000000L,
            hopCount = hopCount,
            maxHops = maxHops
        )
    }

    @Test
    fun oneHopForwarding_incrementsHopAndDispatchesToPeers() = runBlocking {
        // Discovered online peer candidate available
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(hopCount = 0, maxHops = 7)
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.RELAYED, result.decision)
        assertNotNull(result.relayedPacket)
        assertEquals(1, result.relayedPacket!!.hopCount)
        assertEquals(mockBleManager.localNodeId, result.relayedPacket!!.senderNodeId)
        assertEquals("RQM-ORIGIN-01", result.relayedPacket!!.originNodeId)
        assertEquals("SOS-9001", result.relayedPacket!!.messageId)

        assertEquals(1, mockBleManager.dispatchedPackets.size)
        assertEquals(1, mockBleManager.dispatchedPackets.first().hopCount)
    }

    @Test
    fun multipleHopForwarding_incrementsHopCorrectly() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:03", "Remote Node 3", -70, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(hopCount = 3, maxHops = 7)
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.RELAYED, result.decision)
        assertEquals(4, result.relayedPacket!!.hopCount)
        assertEquals(1, mockBleManager.dispatchedPackets.size)
        assertEquals(4, mockBleManager.dispatchedPackets.first().hopCount)
    }

    @Test
    fun hopCountIncrement_preservesOriginalMessageIdAndOriginNodeId() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(
            messageId = "SOS-PRESERVE-42",
            originNodeId = "ORIGINATOR-NODE-X",
            senderNodeId = "FORWARDER-NODE-Y",
            hopCount = 1,
            maxHops = 5
        )

        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.RELAYED, result.decision)
        val forwarded = result.relayedPacket!!
        assertEquals("SOS-PRESERVE-42", forwarded.messageId)
        assertEquals("ORIGINATOR-NODE-X", forwarded.originNodeId)
        assertEquals(mockBleManager.localNodeId, forwarded.senderNodeId)
        assertEquals(2, forwarded.hopCount)
        assertEquals(5, forwarded.maxHops)
        assertEquals(inbound.title, forwarded.title)
        assertEquals(inbound.content, forwarded.content)
        assertEquals(inbound.priority, forwarded.priority)
        assertEquals(inbound.latitude, forwarded.latitude)
        assertEquals(inbound.longitude, forwarded.longitude)
        assertEquals(inbound.timestamp, forwarded.timestamp)
    }

    @Test
    fun maximumHopLimit_dropsPacketWhenHopLimitReached() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Hop count already equal to maxHops (7 of 7)
        val inbound = createInboundPacket(hopCount = 7, maxHops = 7)
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.DROPPED_MAX_HOPS_EXCEEDED, result.decision)
        assertTrue(mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun maximumHopLimit_dropsPacketWhenHopCountExceedsLimit() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Hop count 6 with maxHops 6 -> next would be 7, which exceeds maxHops
        val inbound = createInboundPacket(hopCount = 6, maxHops = 6)
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.DROPPED_MAX_HOPS_EXCEEDED, result.decision)
        assertTrue(mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun duplicatePacketRejection_forwardsAtMostOnce() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(messageId = "SOS-ONCE-ONLY")

        // First arrival: should relay successfully
        val result1 = relayEngine.processAndRelay(inbound)
        assertEquals(RelayDecision.RELAYED, result1.decision)
        assertEquals(1, mockBleManager.dispatchedPackets.size)

        // Second arrival of same message ID: must be rejected as duplicate
        val result2 = relayEngine.processAndRelay(inbound)
        assertEquals(RelayDecision.DROPPED_DUPLICATE, result2.decision)
        // No additional packet dispatched
        assertEquals(1, mockBleManager.dispatchedPackets.size)
    }

    @Test
    fun loopPrevention_dropsPacketOriginatedBySelf() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Packet originated by this local node that bounced back from the mesh
        val loopedPacket = createInboundPacket(
            originNodeId = mockBleManager.localNodeId,
            senderNodeId = "RQM-PEER-ECHO"
        )

        val result = relayEngine.processAndRelay(loopedPacket)
        assertEquals(RelayDecision.DROPPED_ORIGINATED_BY_SELF, result.decision)
        assertTrue("Self-originated packet must never be relayed back onto the mesh", mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun loopPrevention_doesNotForwardBackToImmediateSenderOrOrigin() = runBlocking {
        // Discovered peers includes immediate sender, origin, and an eligible third peer
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:SENDER", "RQM-SENDER-02", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE),
            MeshNode("11:22:33:44:55:ORIGIN", "RQM-ORIGIN-01", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE),
            MeshNode("11:22:33:44:55:PEER3", "Forward Peer 3", -70, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(
            originNodeId = "RQM-ORIGIN-01",
            senderNodeId = "RQM-SENDER-02"
        )

        val result = relayEngine.processAndRelay(inbound, incomingPeerAddress = "11:22:33:44:55:SENDER")
        assertEquals(RelayDecision.RELAYED, result.decision)

        // Verify recorded excluded IDs contain incoming address, senderNodeId, and originNodeId
        val recordedExcludes = mockBleManager.excludedPeerIdsRecorded.first()
        assertTrue(recordedExcludes.contains("11:22:33:44:55:SENDER"))
        assertTrue(recordedExcludes.contains("RQM-SENDER-02"))
        assertTrue(recordedExcludes.contains("RQM-ORIGIN-01"))
    }

    @Test
    fun noAvailablePeers_returnsSkippedNoPeers() = runBlocking {
        // No discovered peers in range
        mockBleManager.discoveredNodes.value = emptyList()

        val inbound = createInboundPacket()
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.SKIPPED_NO_PEERS, result.decision)
        assertNotNull(result.relayedPacket)
        assertTrue(mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun noAvailablePeers_whenAllPeersAreExcludedOrOffline() = runBlocking {
        // Only peer is the sender
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:01", "RQM-SENDER-02", -60, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val inbound = createInboundPacket(senderNodeId = "RQM-SENDER-02")
        val result = relayEngine.processAndRelay(inbound, incomingPeerAddress = "11:22:33:44:55:01")

        assertEquals(RelayDecision.SKIPPED_NO_PEERS, result.decision)
        assertTrue(mockBleManager.dispatchedPackets.isEmpty())
    }

    @Test
    fun relayFailureHandling_returnsRelayFailedWithoutThrowing() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        // Mock dispatch failure (e.g. GATT connection timeout)
        mockBleManager.dispatchReturnValue = BleBroadcastSummary(
            messageId = "SOS-FAIL",
            totalPeersAttempted = 1,
            successfulDeliveries = 0,
            results = listOf(
                BleDispatchResult("11:22:33:44:55:02", "Remote Node 2", false, 0, "GATT connection timeout")
            )
        )

        val inbound = createInboundPacket(messageId = "SOS-FAIL")
        val result = relayEngine.processAndRelay(inbound)

        assertEquals(RelayDecision.RELAY_FAILED, result.decision)
        assertNotNull(result.broadcastSummary)
        assertFalse(result.broadcastSummary!!.isDeliveredToAnyPeer)
    }

    @Test
    fun ackPacket_relayedByRelayEngine() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val ackPacket = createInboundPacket(
            messageId = "ACK-1234",
            originNodeId = "RQM-REMOTE-ORIGIN",
            senderNodeId = "RQM-PREVIOUS-NODE",
            packetType = MeshPacketType.ACK
        )

        val result = relayEngine.processAndRelay(ackPacket)
        assertEquals(RelayDecision.RELAYED, result.decision)
        assertEquals(1, mockBleManager.dispatchedPackets.size)
    }

    @Test
    fun relayPreservesNullCoordinatesWhenNoGps() = runBlocking {
        mockBleManager.discoveredNodes.value = listOf(
            MeshNode("11:22:33:44:55:02", "Remote Node 2", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val packetWithoutGps = MeshPacket(
            messageId = "SOS-NO-COORDS",
            originNodeId = "RQM-ORIGIN-NOGPS",
            senderNodeId = "RQM-PREV-NODE",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Distress Without Coordinates",
            content = "Offline SOS without satellite fix",
            latitude = null,
            longitude = null,
            timestamp = 1730850000000L,
            hopCount = 1,
            maxHops = 5
        )

        val result = relayEngine.processAndRelay(packetWithoutGps)
        assertEquals(RelayDecision.RELAYED, result.decision)
        val relayed = result.relayedPacket!!
        org.junit.Assert.assertNull("Null latitude must be preserved without fabrication", relayed.latitude)
        org.junit.Assert.assertNull("Null longitude must be preserved without fabrication", relayed.longitude)
        assertEquals(2, relayed.hopCount)
    }
}
