package com.resqmesh.app.data.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MeshPacketTest {

    private fun createStandardPacket(
        messageId: String = "SOS-8472",
        originNodeId: String = "RQM-ALFA",
        senderNodeId: String = "RQM-BETA",
        packetType: MeshPacketType = MeshPacketType.SOS,
        priority: IncidentPriority = IncidentPriority.CRITICAL,
        title: String = "Medical Emergency at Sector 4",
        content: String = "First responder assistance required immediately. Fracture and severe bleeding.",
        latitude: Double? = 12.971598,
        longitude: Double? = 77.594562,
        timestamp: Long = 1730840000000L,
        hopCount: Int = 1,
        maxHops: Int = 7
    ): MeshPacket {
        return MeshPacket(
            messageId = messageId,
            originNodeId = originNodeId,
            senderNodeId = senderNodeId,
            packetType = packetType,
            priority = priority,
            title = title,
            content = content,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            hopCount = hopCount,
            maxHops = maxHops
        )
    }

    @Test
    fun serializeAndDeserialize_validSosPacket_preservesAllFields() {
        val original = createStandardPacket()
        val bytes = original.toByteArray()

        assertTrue("Packet bytes should not be empty", bytes.isNotEmpty())
        assertTrue("Packet should fit within BLE limit", bytes.size <= MeshPacket.MAX_PACKET_SIZE_BYTES)

        val deserialized = MeshPacket.fromByteArray(bytes)

        assertEquals(original.messageId, deserialized.messageId)
        assertEquals(original.originNodeId, deserialized.originNodeId)
        assertEquals(original.senderNodeId, deserialized.senderNodeId)
        assertEquals(original.packetType, deserialized.packetType)
        assertEquals(original.priority, deserialized.priority)
        assertEquals(original.title, deserialized.title)
        assertEquals(original.content, deserialized.content)
        assertEquals(original.latitude!!, deserialized.latitude!!, 0.000001)
        assertEquals(original.longitude!!, deserialized.longitude!!, 0.000001)
        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.hopCount, deserialized.hopCount)
        assertEquals(original.maxHops, deserialized.maxHops)
    }

    @Test
    fun serializeAndDeserialize_validAckPacket_preservesAllFields() {
        val ackPacket = MeshPacket(
            messageId = "ACK-SOS-8472",
            originNodeId = "RQM-DELTA",
            senderNodeId = "RQM-DELTA",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.INFO,
            title = "ACK: SOS-8472 Received",
            content = "Distress signal acknowledged by base operator.",
            latitude = null,
            longitude = null,
            timestamp = 1730840005000L,
            hopCount = 0,
            maxHops = 5
        )

        val bytes = ackPacket.toByteArray()
        val deserialized = MeshPacket.fromByteArray(bytes)

        assertEquals(MeshPacketType.ACK, deserialized.packetType)
        assertEquals(IncidentPriority.INFO, deserialized.priority)
        assertEquals("ACK-SOS-8472", deserialized.messageId)
        assertNull(deserialized.latitude)
        assertNull(deserialized.longitude)
    }

    @Test
    fun serializeAndDeserialize_withoutLocation_preservesNullCoordinates() {
        val packet = createStandardPacket(latitude = null, longitude = null)
        val bytes = packet.toByteArray()
        val deserialized = MeshPacket.fromByteArray(bytes)

        assertNull(deserialized.latitude)
        assertNull(deserialized.longitude)
    }

    @Test
    fun checksumMismatch_tamperedBytes_throwsChecksumMismatchException() {
        val packet = createStandardPacket()
        val bytes = packet.toByteArray()

        // Tamper with one content byte (before the 4-byte CRC at the end)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0xFF).toByte()

        try {
            MeshPacket.fromByteArray(bytes)
            fail("Should have failed with ChecksumMismatchException")
        } catch (e: ChecksumMismatchException) {
            assertTrue("Expected ChecksumMismatchException", true)
        }
    }

    @Test
    fun invalidMagicBytes_throwsMalformedPacketException() {
        val packet = createStandardPacket()
        val bytes = packet.toByteArray()

        bytes[0] = 0x00 // Invalidate first magic byte

        try {
            MeshPacket.fromByteArray(bytes)
            fail("Should have failed due to invalid magic bytes")
        } catch (e: MalformedPacketException) {
            assertTrue(e.message?.contains("magic bytes") == true)
        }
    }

    @Test
    fun unsupportedProtocolVersion_throwsUnsupportedProtocolVersionException() {
        val packet = createStandardPacket()
        val bytes = packet.toByteArray()

        // Set version byte to 99
        bytes[2] = 99.toByte()

        // Recompute CRC so it passes CRC check and hits version check
        val crc = java.util.zip.CRC32()
        crc.update(bytes, 0, bytes.size - 4)
        val newCrc = crc.value
        val payloadLen = bytes.size - 4
        bytes[payloadLen] = ((newCrc shr 24) and 0xFF).toByte()
        bytes[payloadLen + 1] = ((newCrc shr 16) and 0xFF).toByte()
        bytes[payloadLen + 2] = ((newCrc shr 8) and 0xFF).toByte()
        bytes[payloadLen + 3] = (newCrc and 0xFF).toByte()

        try {
            MeshPacket.fromByteArray(bytes)
            fail("Should have failed due to unsupported protocol version")
        } catch (e: UnsupportedProtocolVersionException) {
            assertEquals(99.toByte(), e.version)
        }
    }

    @Test(expected = MalformedPacketException::class)
    fun blankMessageId_throwsMalformedPacketException() {
        createStandardPacket(messageId = "   ")
    }

    @Test(expected = MalformedPacketException::class)
    fun invalidCharactersInMessageId_throwsMalformedPacketException() {
        createStandardPacket(messageId = "SOS@8472!")
    }

    @Test(expected = MalformedPacketException::class)
    fun blankOriginNodeId_throwsMalformedPacketException() {
        createStandardPacket(originNodeId = "")
    }

    @Test(expected = MalformedPacketException::class)
    fun blankSenderNodeId_throwsMalformedPacketException() {
        createStandardPacket(senderNodeId = "")
    }

    @Test(expected = MalformedPacketException::class)
    fun hopCountExceedingMaxHops_throwsMalformedPacketException() {
        createStandardPacket(hopCount = 8, maxHops = 7)
    }

    @Test(expected = MalformedPacketException::class)
    fun negativeHopCount_throwsMalformedPacketException() {
        createStandardPacket(hopCount = -1)
    }

    @Test(expected = MalformedPacketException::class)
    fun latitudeOutOfRange_throwsMalformedPacketException() {
        createStandardPacket(latitude = 95.0, longitude = 77.0)
    }

    @Test(expected = MalformedPacketException::class)
    fun longitudeOutOfRange_throwsMalformedPacketException() {
        createStandardPacket(latitude = 12.0, longitude = 195.0)
    }

    @Test(expected = MalformedPacketException::class)
    fun partialCoordinates_throwsMalformedPacketException() {
        createStandardPacket(latitude = 12.0, longitude = null)
    }

    @Test(expected = MalformedPacketException::class)
    fun titleExceedingMaxLength_throwsMalformedPacketException() {
        createStandardPacket(title = "A".repeat(MeshPacket.MAX_TITLE_LENGTH + 1))
    }

    @Test(expected = MalformedPacketException::class)
    fun contentExceedingMaxLength_throwsMalformedPacketException() {
        createStandardPacket(content = "A".repeat(MeshPacket.MAX_CONTENT_LENGTH + 1))
    }

    @Test
    fun packetFragmenterAndReassembler_multiChunkFragmentation_reassemblesExactly() {
        val original = createStandardPacket(
            content = "Detailed multi-sentence tactical distress report across rugged mountain terrain. Need oxygen and stretcher for evacuation."
        )

        // Fragment with small chunks (e.g., 40 bytes) to force multiple fragments
        val fragments = MeshPacketFragmenter.fragment(original, maxChunkSize = 40)
        assertTrue("Should produce multiple fragments", fragments.size > 1)

        val reassembler = MeshPacketReassembler()
        var assembledPacket: MeshPacket? = null

        fragments.forEach { fragment ->
            val result = reassembler.addFragment(fragment)
            if (result != null) {
                assembledPacket = result
            }
        }

        assertNotNull("Packet should be successfully reassembled", assembledPacket)
        assertEquals(original.messageId, assembledPacket?.messageId)
        assertEquals(original.title, assembledPacket?.title)
        assertEquals(original.content, assembledPacket?.content)
        assertEquals(original.latitude!!, assembledPacket?.latitude!!, 0.000001)
    }

    @Test
    fun meshPacketFragment_binarySerialization_preservesFragment() {
        val chunkData = "Test BLE chunk data bytes".toByteArray(Charsets.UTF_8)
        val fragment = MeshPacketFragment(
            messageId = "SOS-FRAG-1",
            fragmentIndex = 2,
            totalFragments = 5,
            data = chunkData
        )

        val bytes = fragment.toByteArray()
        val deserialized = MeshPacketFragment.fromByteArray(bytes)

        assertEquals("SOS-FRAG-1", deserialized.messageId)
        assertEquals(2, deserialized.fragmentIndex)
        assertEquals(5, deserialized.totalFragments)
        assertArrayEquals(chunkData, deserialized.data)
    }

    @Test
    fun duplicateDetector_tracksSeenAndPreventsDuplicates() {
        val detector = InMemoryDuplicateDetector(maxCapacity = 100, ttlMs = 5000L)

        assertFalse("First check should not be duplicate", detector.isDuplicate("MSG-101"))
        assertTrue("First markSeen should return true (is new)", detector.markSeen("MSG-101"))

        assertTrue("Second check should be duplicate", detector.isDuplicate("MSG-101"))
        assertFalse("Second markSeen should return false (already seen)", detector.markSeen("MSG-101"))

        assertFalse("Different message should not be duplicate", detector.isDuplicate("MSG-102"))
    }
}
