package com.resqmesh.routing

import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AcknowledgementManagerTest {

    private lateinit var ackManager: AcknowledgementManager

    @Before
    fun setUp() {
        ackManager = AcknowledgementManager(maxRetries = 2, ackTimeoutMs = 1000L)
    }

    @Test
    fun testTrackUnicastMessage() {
        val unicast = MessagePacket(
            senderId = "Alice",
            receiverId = "Bob",
            payload = "Hello Bob"
        )

        ackManager.trackMessage(unicast)
        assertTrue(ackManager.isPending(unicast.id))
        assertEquals(1, ackManager.pendingCount())
    }

    @Test
    fun testBroadcastsAndAcksNotTracked() {
        val broadcast = MessagePacket(
            senderId = "Alice",
            receiverId = MessagePacket.BROADCAST_ID,
            payload = "Broadcast to all"
        )
        val ackPacket = MessagePacket(
            senderId = "Alice",
            receiverId = "Bob",
            payload = "ACK:123",
            messageType = MessageType.ACK
        )

        ackManager.trackMessage(broadcast)
        ackManager.trackMessage(ackPacket)

        assertEquals(0, ackManager.pendingCount())
        assertFalse(ackManager.isPending(broadcast.id))
        assertFalse(ackManager.isPending(ackPacket.id))
    }

    @Test
    fun testProcessAckResolvesMessage() {
        val message = MessagePacket(
            id = "msg-999",
            senderId = "Alice",
            receiverId = "Bob",
            payload = "Important"
        )
        ackManager.trackMessage(message)
        assertTrue(ackManager.isPending(message.id))

        // Bob responds with ACK referencing msg-999
        val resolved = ackManager.processAck("msg-999")
        assertNotNull(resolved)
        assertEquals(message.id, resolved!!.id)
        assertFalse(ackManager.isPending(message.id))
        assertEquals(0, ackManager.pendingCount())
    }

    @Test
    fun testGenerateAckOnlyForDirectMessages() {
        val directMessageForMe = MessagePacket(
            senderId = "Alice",
            receiverId = "LocalNode",
            payload = "Hello"
        )
        val directMessageForOther = MessagePacket(
            senderId = "Alice",
            receiverId = "Charlie",
            payload = "Hello Charlie"
        )
        val broadcastMessage = MessagePacket(
            senderId = "Alice",
            receiverId = MessagePacket.BROADCAST_ID,
            payload = "Notice"
        )

        val ackForMe = ackManager.generateAck(directMessageForMe, "LocalNode")
        assertNotNull(ackForMe)
        assertEquals("LocalNode", ackForMe!!.senderId)
        assertEquals("Alice", ackForMe.receiverId)
        assertEquals(MessageType.ACK, ackForMe.messageType)
        assertEquals(directMessageForMe.id, ackForMe.ackForId)

        val ackForOther = ackManager.generateAck(directMessageForOther, "LocalNode")
        assertNull(ackForOther)

        val ackForBroadcast = ackManager.generateAck(broadcastMessage, "LocalNode")
        assertNull(ackForBroadcast)
    }

    @Test
    fun testRetriesAndFailureCallback() {
        val packet = MessagePacket(
            id = "retry-msg",
            senderId = "Alice",
            receiverId = "Bob",
            payload = "Unreachable destination"
        )
        ackManager.trackMessage(packet)

        val baseTime = System.currentTimeMillis()

        // 1. Before timeout: 0 retries
        val noRetries = ackManager.collectRetries(currentTime = baseTime + 500)
        assertTrue(noRetries.isEmpty())

        // 2. First timeout (1500ms): retry #1 collected
        val firstRetry = ackManager.collectRetries(currentTime = baseTime + 1500)
        assertEquals(1, firstRetry.size)
        assertEquals(packet.id, firstRetry[0].id)

        // 3. Second timeout (3000ms): retry #2 collected
        val secondRetry = ackManager.collectRetries(currentTime = baseTime + 3000)
        assertEquals(1, secondRetry.size)

        // 4. Third timeout (4500ms): maxRetries (2) exceeded, triggers onFailed
        var failedPacket: MessagePacket? = null
        val thirdRetry = ackManager.collectRetries(currentTime = baseTime + 4500, onFailed = {
            failedPacket = it
        })

        assertTrue(thirdRetry.isEmpty())
        assertNotNull(failedPacket)
        assertEquals(packet.id, failedPacket!!.id)
        assertFalse(ackManager.isPending(packet.id))
    }
}
