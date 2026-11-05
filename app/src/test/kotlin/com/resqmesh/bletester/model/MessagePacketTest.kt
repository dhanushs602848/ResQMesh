package com.resqmesh.bletester.model

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MessagePacketTest {

    @Test
    fun packet_wireString_serializationAndDeserialization() {
        val original = MessagePacket(
            id = UUID.randomUUID().toString(),
            senderId = "NODE_A",
            receiverId = "NODE_B",
            payload = "Emergency alert: Medical assistance needed at Zone 3",
            timestamp = 1700000000000L,
            messageType = MessageType.SOS,
            ttl = 5,
            priority = 100,
            hopCount = 1
        )

        val wire = original.toWireString()
        assertNotNull(wire)
        assertTrue(wire.contains("NODE_A"))
        assertTrue(wire.contains("SOS"))

        val parsed = MessagePacket.fromWireString(wire)
        assertNotNull(parsed)
        assertEquals(original.id, parsed?.id)
        assertEquals(original.senderId, parsed?.senderId)
        assertEquals(original.receiverId, parsed?.receiverId)
        assertEquals(original.payload, parsed?.payload)
        assertEquals(original.timestamp, parsed?.timestamp)
        assertEquals(original.messageType, parsed?.messageType)
        assertEquals(original.ttl, parsed?.ttl)
        assertEquals(original.priority, parsed?.priority)
        assertEquals(original.hopCount, parsed?.hopCount)
    }

    @Test
    fun packet_byteArray_conversion() {
        val original = MessagePacket.createSos(
            senderId = "ALPHA_1",
            payload = "Flash flood warning"
        )

        val bytes = original.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val restored = MessagePacket.fromByteArray(bytes)
        assertNotNull(restored)
        assertEquals(original.id, restored?.id)
        assertEquals(MessageType.SOS, restored?.messageType)
        assertEquals("Flash flood warning", restored?.payload)
    }

    @Test
    fun packet_createAck_populatesTargetId() {
        val original = MessagePacket(
            id = "MSG-12345",
            senderId = "SENDER_NODE",
            receiverId = "RECEIVER_NODE",
            payload = "Status update",
            messageType = MessageType.CHAT
        )

        val ack = MessagePacket.createAck(original, responderNodeId = "RECEIVER_NODE")
        assertEquals(MessageType.ACK, ack.messageType)
        assertEquals("RECEIVER_NODE", ack.senderId)
        assertEquals("SENDER_NODE", ack.receiverId)
        assertEquals("MSG-12345", ack.ackForId)
        assertEquals("ACK:MSG-12345", ack.payload)
    }

    @Test
    fun packet_ttlDecrement_andExpiry() {
        val packet = MessagePacket(
            senderId = "NODE_1",
            payload = "Test",
            ttl = 1,
            hopCount = 0
        )
        assertFalse(packet.isExpired())

        val forwarded = packet.createForwardCopy()
        assertEquals(0, forwarded.ttl)
        assertEquals(1, forwarded.hopCount)
        assertTrue(forwarded.isExpired())
    }
}
