package com.resqmesh.app.data.ble

import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketFragmenter
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.app.data.model.NodeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests validating Phase 2.3 BLE GATT client outbound dispatch logic:
 * peer filtering, characteristic routing, fragmentation decisions, and delivery summary aggregation.
 */
class GattClientDispatchTest {

    private fun createSamplePacket(
        id: String = "SOS-1001",
        type: MeshPacketType = MeshPacketType.SOS,
        content: String = "Medical emergency at Camp Site Alpha"
    ): MeshPacket {
        return MeshPacket(
            messageId = id,
            originNodeId = "RQM-LOCAL",
            senderNodeId = "RQM-LOCAL",
            packetType = type,
            priority = IncidentPriority.CRITICAL,
            title = "Distress Signal",
            content = content,
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = 1730850000000L,
            hopCount = 0,
            maxHops = 7
        )
    }

    @Test
    fun targetCharacteristic_routesSosPacketToSosCharacteristic() {
        val packet = createSamplePacket(type = MeshPacketType.SOS)
        val targetCharUuid: UUID = when (packet.packetType) {
            MeshPacketType.SOS -> BleConstants.SOS_CHARACTERISTIC_UUID
            MeshPacketType.ACK -> BleConstants.ACK_CHARACTERISTIC_UUID
        }

        assertEquals(BleConstants.SOS_CHARACTERISTIC_UUID, targetCharUuid)
        assertEquals("7b1f3c8b-92e4-4d8b-b6d1-4e9a8f2c3b5d", targetCharUuid.toString())
    }

    @Test
    fun targetCharacteristic_routesAckPacketToAckCharacteristic() {
        val packet = createSamplePacket(type = MeshPacketType.ACK)
        val targetCharUuid: UUID = when (packet.packetType) {
            MeshPacketType.SOS -> BleConstants.SOS_CHARACTERISTIC_UUID
            MeshPacketType.ACK -> BleConstants.ACK_CHARACTERISTIC_UUID
        }

        assertEquals(BleConstants.ACK_CHARACTERISTIC_UUID, targetCharUuid)
        assertEquals("7b1f3c8c-92e4-4d8b-b6d1-4e9a8f2c3b5d", targetCharUuid.toString())
    }

    @Test
    fun peerFiltering_onlyDispatchesToOnlinePeers() {
        val peers = listOf(
            MeshNode("11:22:33:44:55:01", "Peer 1", -65, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE),
            MeshNode("11:22:33:44:55:02", "Peer 2", -85, 1, true, System.currentTimeMillis() - 40000, NodeStatus.STALE),
            MeshNode("11:22:33:44:55:03", "Peer 3", -95, 1, true, System.currentTimeMillis() - 80000, NodeStatus.DISCONNECTED),
            MeshNode("11:22:33:44:55:04", "Peer 4", -70, 1, true, System.currentTimeMillis(), NodeStatus.ONLINE)
        )

        val targetPeers = peers.filter { it.status == NodeStatus.ONLINE }
        assertEquals(2, targetPeers.size)
        assertTrue(targetPeers.any { it.id == "11:22:33:44:55:01" })
        assertTrue(targetPeers.any { it.id == "11:22:33:44:55:04" })
        assertFalse(targetPeers.any { it.id == "11:22:33:44:55:02" })
    }

    @Test
    fun fragmentationDecision_unfragmentedWhenFitsWithinMtu() {
        val packet = createSamplePacket(content = "Short alert")
        val rawBytes = packet.toByteArray()

        val negotiatedMtu = 512
        val maxPayloadPerWrite = (negotiatedMtu - 3).coerceAtLeast(20) // 509 bytes

        val shouldFragment = rawBytes.size > maxPayloadPerWrite
        assertFalse("Packet size (${rawBytes.size} bytes) should easily fit within 512 MTU", shouldFragment)

        val chunks = if (!shouldFragment) {
            listOf(rawBytes)
        } else {
            MeshPacketFragmenter.fragment(packet, maxChunkSize = maxPayloadPerWrite - 25).map { it.toByteArray() }
        }

        assertEquals(1, chunks.size)
    }

    @Test
    fun fragmentationDecision_fragmentsWhenExceedsLegacyMtu() {
        val packet = createSamplePacket(
            content = "Detailed high-priority distress report containing multi-sentence incident descriptions across dense mountainous terrain with coordinates."
        )
        val rawBytes = packet.toByteArray()

        val legacyMtu = 23
        val maxPayloadPerWrite = (legacyMtu - 3).coerceAtLeast(20) // 20 bytes

        val shouldFragment = rawBytes.size > maxPayloadPerWrite
        assertTrue("Packet (${rawBytes.size} bytes) must be fragmented when MTU is 23", shouldFragment)

        val fragmentChunkLimit = (maxPayloadPerWrite - 25).coerceAtLeast(20)
        val fragments = MeshPacketFragmenter.fragment(packet, maxChunkSize = fragmentChunkLimit)
        assertTrue("Should produce multiple fragments for small MTU", fragments.size > 1)
    }

    @Test
    fun broadcastSummary_aggregatesMixedPeerResultsCorrectly() {
        val results = listOf(
            BleDispatchResult(
                peerAddress = "11:22:33:44:55:01",
                peerName = "ResQ Node Alpha",
                isDelivered = true,
                fragmentsSent = 1
            ),
            BleDispatchResult(
                peerAddress = "11:22:33:44:55:02",
                peerName = "ResQ Node Beta",
                isDelivered = false,
                error = "GATT write failed with status 133 on chunk 0"
            ),
            BleDispatchResult(
                peerAddress = "11:22:33:44:55:03",
                peerName = "ResQ Node Gamma",
                isDelivered = true,
                fragmentsSent = 3
            )
        )

        val summary = BleBroadcastSummary(
            messageId = "SOS-1001",
            totalPeersAttempted = results.size,
            successfulDeliveries = results.count { it.isDelivered },
            results = results
        )

        assertEquals("SOS-1001", summary.messageId)
        assertEquals(3, summary.totalPeersAttempted)
        assertEquals(2, summary.successfulDeliveries)
        assertTrue(summary.isDeliveredToAnyPeer)
    }

    @Test
    fun broadcastSummary_zeroDeliveriesReportsFalseForDeliveredToAny() {
        val results = listOf(
            BleDispatchResult(
                peerAddress = "11:22:33:44:55:01",
                peerName = "Peer 1",
                isDelivered = false,
                error = "Connection timed out"
            )
        )

        val summary = BleBroadcastSummary(
            messageId = "SOS-1002",
            totalPeersAttempted = 1,
            successfulDeliveries = 0,
            results = results
        )

        assertEquals(0, summary.successfulDeliveries)
        assertFalse(summary.isDeliveredToAnyPeer)
    }

    @Test
    fun broadcastSummary_emptyPeersListReportsZeroCleanly() {
        val summary = BleBroadcastSummary(
            messageId = "SOS-1003",
            totalPeersAttempted = 0,
            successfulDeliveries = 0,
            results = emptyList()
        )

        assertEquals(0, summary.totalPeersAttempted)
        assertEquals(0, summary.successfulDeliveries)
        assertFalse(summary.isDeliveredToAnyPeer)
    }
}
