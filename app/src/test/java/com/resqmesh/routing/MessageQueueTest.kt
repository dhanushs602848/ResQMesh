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

class MessageQueueTest {

    private lateinit var queue: MessageQueue

    @Before
    fun setUp() {
        queue = MessageQueue(maxCapacity = 10)
    }

    @Test
    fun testSosFirstPrioritization() {
        val chat1 = MessagePacket(senderId = "A", payload = "Chat 1", messageType = MessageType.CHAT, timestamp = 100)
        val beacon = MessagePacket(senderId = "B", payload = "Beacon", messageType = MessageType.BEACON, timestamp = 200)
        val chat2 = MessagePacket(senderId = "C", payload = "Chat 2", messageType = MessageType.CHAT, timestamp = 300)
        val sos = MessagePacket.createSos(senderId = "D", payload = "SOS EMERGENCY")

        // Enqueue regular traffic first
        queue.enqueue(chat1)
        queue.enqueue(beacon)
        queue.enqueue(chat2)

        // Enqueue SOS last
        queue.enqueue(sos)

        // Despite arriving last, SOS MUST be dequeued first!
        val first = queue.dequeue()
        assertNotNull(first)
        assertEquals(MessageType.SOS, first!!.messageType)
        assertEquals("SOS EMERGENCY", first.payload)

        // Next should be CHAT (higher priority than BEACON) in FIFO order
        val second = queue.dequeue()
        assertEquals("Chat 1", second!!.payload)

        val third = queue.dequeue()
        assertEquals("Chat 2", third!!.payload)

        val fourth = queue.dequeue()
        assertEquals("Beacon", fourth!!.payload)

        assertTrue(queue.isEmpty())
    }

    @Test
    fun testFifoForEqualPriority() {
        val pkt1 = MessagePacket(senderId = "A", payload = "First", priority = 50, timestamp = 1000)
        val pkt2 = MessagePacket(senderId = "B", payload = "Second", priority = 50, timestamp = 2000)
        val pkt3 = MessagePacket(senderId = "C", payload = "Third", priority = 50, timestamp = 3000)

        queue.enqueue(pkt3)
        queue.enqueue(pkt1)
        queue.enqueue(pkt2)

        assertEquals("First", queue.dequeue()!!.payload)
        assertEquals("Second", queue.dequeue()!!.payload)
        assertEquals("Third", queue.dequeue()!!.payload)
    }

    @Test
    fun testExpiredPacketsDroppedOnDequeue() {
        val valid = MessagePacket(senderId = "A", payload = "Valid", ttl = 3)
        val expired = MessagePacket(senderId = "B", payload = "Expired", ttl = 0)

        // Direct enqueue of expired packet should return false
        assertFalse(queue.enqueue(expired))

        queue.enqueue(valid)
        assertEquals(1, queue.size())
        assertEquals("Valid", queue.dequeue()!!.payload)
    }

    @Test
    fun testBufferOverflowProtectsSos() {
        val smallQueue = MessageQueue(maxCapacity = 3)

        val chat1 = MessagePacket(senderId = "A", payload = "Chat 1", messageType = MessageType.CHAT, priority = 50)
        val chat2 = MessagePacket(senderId = "B", payload = "Chat 2", messageType = MessageType.CHAT, priority = 50)
        val beacon = MessagePacket(senderId = "C", payload = "Beacon", messageType = MessageType.BEACON, priority = 10)

        smallQueue.enqueue(chat1)
        smallQueue.enqueue(chat2)
        smallQueue.enqueue(beacon)
        assertEquals(3, smallQueue.size())

        // Enqueuing an SOS alert must succeed by evicting the lowest priority non-SOS packet (Beacon)
        val sos = MessagePacket.createSos(senderId = "D", payload = "SOS Distress")
        val added = smallQueue.enqueue(sos)
        assertTrue(added)
        assertEquals(3, smallQueue.size())

        // Verify SOS is first, beacon was dropped
        val p1 = smallQueue.dequeue()
        assertEquals(MessageType.SOS, p1!!.messageType)

        val remaining = smallQueue.toList().map { it.payload }
        assertTrue(remaining.contains("Chat 1"))
        assertTrue(remaining.contains("Chat 2"))
        assertFalse(remaining.contains("Beacon"))
    }

    @Test
    fun testRemovePacket() {
        val p1 = MessagePacket(id = "id-1", senderId = "A", payload = "1")
        val p2 = MessagePacket(id = "id-2", senderId = "B", payload = "2")

        queue.enqueue(p1)
        queue.enqueue(p2)

        val removed = queue.remove("id-1")
        assertTrue(removed)
        assertEquals(1, queue.size())
        assertEquals("id-2", queue.peek()!!.id)
    }
}
