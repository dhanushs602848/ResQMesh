package com.resqmesh.app.data.ble

import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketFragment
import com.resqmesh.app.data.model.MeshPacketFragmenter
import com.resqmesh.app.data.model.MeshPacketReassembler
import com.resqmesh.app.data.model.MeshPacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests validating GATT packet reception, parsing, CRC32 verification,
 * and fragment reassembly as executed by the BLE GATT server.
 */
class GattPacketProcessingTest {

    private fun createSampleSosPacket(
        messageId: String = "SOS-9911",
        originNode: String = "RQM-TEST-1",
        senderNode: String = "RQM-TEST-2",
        content: String = "High water levels detected. Immediate tactical evacuation required at designated coordinates."
    ): MeshPacket {
        return MeshPacket(
            messageId = messageId,
            originNodeId = originNode,
            senderNodeId = senderNode,
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Critical Flash Flood Evacuation",
            content = content,
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = 1730842000000L,
            hopCount = 0,
            maxHops = 7
        )
    }

    private fun simulateInboundGattWrite(
        bytes: ByteArray,
        charUuid: UUID,
        reassembler: MeshPacketReassembler
    ): MeshPacket? {
        if (bytes.isEmpty()) return null

        return try {
            when {
                bytes.size >= 2 && bytes[0] == MeshPacket.MAGIC_1 && bytes[1] == MeshPacket.MAGIC_2 -> {
                    MeshPacket.fromByteArray(bytes)
                }
                bytes.size >= 2 && bytes[0] == MeshPacketFragment.FRAGMENT_MAGIC_1 && bytes[1] == MeshPacketFragment.FRAGMENT_MAGIC_2 -> {
                    val fragment = MeshPacketFragment.fromByteArray(bytes)
                    reassembler.addFragment(fragment)
                }
                else -> {
                    MeshPacket.fromByteArray(bytes)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun gattWrite_unfragmentedValidSosPacket_isAcceptedAndDecoded() {
        val reassembler = MeshPacketReassembler()
        val original = createSampleSosPacket()
        val rawBytes = original.toByteArray()

        val decoded = simulateInboundGattWrite(rawBytes, BleConstants.SOS_CHARACTERISTIC_UUID, reassembler)

        assertNotNull("Valid unfragmented packet should be accepted", decoded)
        assertEquals("SOS-9911", decoded?.messageId)
        assertEquals(IncidentPriority.CRITICAL, decoded?.priority)
        assertEquals("Critical Flash Flood Evacuation", decoded?.title)
        assertEquals(12.9716, decoded?.latitude!!, 0.0001)
    }

    @Test
    fun gattWrite_fragmentedSosPacket_reassemblesUponLastFragment() {
        val reassembler = MeshPacketReassembler()
        val original = createSampleSosPacket(
            content = "Detailed multi-hop flood report with high-priority evacuation instructions across multiple rugged sectors."
        )

        // Fragment into small chunks to simulate MTU limits (e.g. 50 bytes)
        val fragments = MeshPacketFragmenter.fragment(original, maxChunkSize = 50)
        assertTrue("Packet should be divided into multiple fragments", fragments.size > 1)

        var finalPacket: MeshPacket? = null
        fragments.forEachIndexed { index, fragment ->
            val fragmentBytes = fragment.toByteArray()
            val result = simulateInboundGattWrite(fragmentBytes, BleConstants.SOS_CHARACTERISTIC_UUID, reassembler)
            if (index < fragments.size - 1) {
                assertNull("Intermediary fragments should not emit complete packet yet", result)
            } else {
                finalPacket = result
            }
        }

        assertNotNull("Final fragment should trigger complete packet emission", finalPacket)
        assertEquals(original.messageId, finalPacket?.messageId)
        assertEquals(original.title, finalPacket?.title)
        assertEquals(original.content, finalPacket?.content)
    }

    @Test
    fun gattWrite_corruptedChecksumBytes_isSafelyRejected() {
        val reassembler = MeshPacketReassembler()
        val original = createSampleSosPacket()
        val bytes = original.toByteArray()

        // Corrupt payload
        bytes[bytes.size - 8] = (bytes[bytes.size - 8].toInt() xor 0xAA).toByte()

        val result = simulateInboundGattWrite(bytes, BleConstants.SOS_CHARACTERISTIC_UUID, reassembler)
        assertNull("Corrupted packet bytes must be rejected safely with null", result)
    }

    @Test
    fun gattWrite_emptyByteArray_isRejectedWithoutException() {
        val reassembler = MeshPacketReassembler()
        val result = simulateInboundGattWrite(ByteArray(0), BleConstants.SOS_CHARACTERISTIC_UUID, reassembler)
        assertNull("Empty bytes must return null cleanly", result)
    }

    @Test
    fun gattWrite_invalidMagicBytes_isRejectedSafely() {
        val reassembler = MeshPacketReassembler()
        val garbageBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10)

        val result = simulateInboundGattWrite(garbageBytes, BleConstants.SOS_CHARACTERISTIC_UUID, reassembler)
        assertNull("Garbage bytes without valid protocol magic must be rejected safely", result)
    }

    @Test
    fun gattWrite_ackCharacteristicUUID_processesAckPacket() {
        val reassembler = MeshPacketReassembler()
        val ack = MeshPacket(
            messageId = "ACK-SOS-9911",
            originNodeId = "RQM-RESCUE-1",
            senderNodeId = "RQM-RESCUE-1",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK: SOS-9911",
            content = "Rescue team dispatched to target coordinates.",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 5
        )

        val bytes = ack.toByteArray()
        val decoded = simulateInboundGattWrite(bytes, BleConstants.ACK_CHARACTERISTIC_UUID, reassembler)

        assertNotNull("Valid ACK packet should be decoded successfully", decoded)
        assertEquals(MeshPacketType.ACK, decoded?.packetType)
        assertEquals("ACK-SOS-9911", decoded?.messageId)
    }
}
