package com.resqmesh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MessagePacketTest {

    @Test
    fun testPacketCreationAndDefaults() {
        val sender = "Node_Alpha"
        val receiver = "Node_Bravo"
        val payload = "Status OK"

        val packet = MessagePacket(
            senderId = sender,
            receiverId = receiver,
            payload = payload
        )

        assertNotNull(packet.id)
        // Verify ID is a valid UUID
        assertNotNull(UUID.fromString(packet.id))
        assertEquals(sender, packet.senderId)
        assertEquals(receiver, packet.receiverId)
        assertEquals(payload, packet.payload)
        assertEquals(MessageType.CHAT, packet.messageType)
        assertEquals(MessagePacket.DEFAULT_TTL, packet.ttl)
        assertEquals(MessageType.CHAT.defaultPriority, packet.priority)
        assertEquals(0, packet.hopCount)
        assertFalse(packet.isExpired())
        assertFalse(packet.isBroadcast())
        assertTrue(packet.isAddressedTo("Node_Bravo"))
        assertFalse(packet.isAddressedTo("Node_Charlie"))
    }

    @Test
    fun testSosPacketCreation() {
        val sos = MessagePacket.createSos(
            senderId = "Medic_1",
            payload = "Emergency! Medical supplies required at Sector 4"
        )

        assertNotNull(sos.id)
        assertEquals("Medic_1", sos.senderId)
        assertEquals(MessagePacket.BROADCAST_ID, sos.receiverId)
        assertEquals(MessageType.SOS, sos.messageType)
        assertEquals(100, sos.priority)
        assertTrue(sos.isBroadcast())
        assertTrue(sos.isAddressedTo("Any_Node"))
        assertEquals(MessagePacket.DEFAULT_TTL, sos.ttl)
        assertEquals(0, sos.hopCount)
    }

    @Test
    fun testTtlDecrementAndExpiry() {
        val packet = MessagePacket(
            senderId = "A",
            receiverId = "B",
            payload = "Ping",
            ttl = 3,
            hopCount = 0
        )

        val hop1 = packet.createForwardCopy()
        assertEquals(2, hop1.ttl)
        assertEquals(1, hop1.hopCount)
        assertFalse(hop1.isExpired())

        val hop2 = hop1.createForwardCopy()
        assertEquals(1, hop2.ttl)
        assertEquals(2, hop2.hopCount)
        assertFalse(hop2.isExpired())

        val hop3 = hop2.createForwardCopy()
        assertEquals(0, hop3.ttl)
        assertEquals(3, hop3.hopCount)
        assertTrue(hop3.isExpired())
    }

    @Test
    fun testAckPacketCreation() {
        val original = MessagePacket(
            senderId = "Rescuer_1",
            receiverId = "Base_Station",
            payload = "Report filed"
        )

        val ack = MessagePacket.createAck(
            originalPacket = original,
            responderNodeId = "Base_Station"
        )

        assertEquals("Base_Station", ack.senderId)
        assertEquals("Rescuer_1", ack.receiverId)
        assertEquals(MessageType.ACK, ack.messageType)
        assertEquals(original.id, ack.ackForId)
        assertEquals("ACK:${original.id}", ack.payload)
        assertEquals(MessageType.ACK.defaultPriority, ack.priority)
    }

    @Test
    fun testWireSerializationAndDeserialization() {
        val packet = MessagePacket(
            id = "test-uuid-1234",
            senderId = "Node_A",
            receiverId = "Node_B",
            payload = "Test payload with details",
            timestamp = 1700000000000L,
            messageType = MessageType.LOCATION,
            ttl = 5,
            priority = 40,
            hopCount = 2,
            ackForId = null
        )

        val wire = packet.toWireString()
        val parsed = MessagePacket.fromWireString(wire)

        assertNotNull(parsed)
        assertEquals(packet.id, parsed!!.id)
        assertEquals(packet.senderId, parsed.senderId)
        assertEquals(packet.receiverId, parsed.receiverId)
        assertEquals(packet.payload, parsed.payload)
        assertEquals(packet.timestamp, parsed.timestamp)
        assertEquals(packet.messageType, parsed.messageType)
        assertEquals(packet.ttl, parsed.ttl)
        assertEquals(packet.priority, parsed.priority)
        assertEquals(packet.hopCount, parsed.hopCount)
        assertNull(parsed.ackForId)

        // Test with bytes
        val bytes = packet.toByteArray()
        val fromBytes = MessagePacket.fromByteArray(bytes)
        assertEquals(packet, fromBytes)
    }

    @Test
    fun testSpecialCharactersInPayload() {
        val original = MessagePacket(
            senderId = "A",
            receiverId = "B",
            payload = "Line 1\nLine 2 with | pipe character"
        )

        val wire = original.toWireString()
        val parsed = MessagePacket.fromWireString(wire)

        assertNotNull(parsed)
        assertEquals(original.payload, parsed!!.payload)
    }
}
