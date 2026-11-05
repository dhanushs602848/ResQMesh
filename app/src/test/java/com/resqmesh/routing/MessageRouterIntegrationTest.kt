package com.resqmesh.routing

import com.resqmesh.data.MessageDao
import com.resqmesh.data.MessageEntity
import com.resqmesh.data.MessageRepository
import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Full integration test for MessageRouter, MockBleTransport, and MessageRepository.
 */
class MessageRouterIntegrationTest {

    private lateinit var fakeDao: FakeMessageDao
    private lateinit var repository: MessageRepository
    private lateinit var transport: MockBleTransport
    private lateinit var router: MessageRouter

    private val localNode = "Node_Alpha"

    @Before
    fun setUp() {
        fakeDao = FakeMessageDao()
        repository = MessageRepository(fakeDao)
        transport = MockBleTransport(isConnectedState = true)
        router = MessageRouter(
            localNodeId = localNode,
            repository = repository,
            deduplicationCache = DeduplicationCache(capacity = 100),
            messageQueue = MessageQueue(maxCapacity = 100),
            ackManager = AcknowledgementManager(maxRetries = 2, ackTimeoutMs = 1000L),
            bleTransport = transport,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatcher = Dispatchers.Unconfined
        )
        router.attachTransport(transport)
    }

    @Test
    fun testDirectMessageArrivalGeneratesAckAndSaves(): Unit = runBlocking {
        val incoming = MessagePacket(
            id = "msg-101",
            senderId = "Node_Bravo",
            receiverId = localNode,
            payload = "Need coordinates",
            messageType = MessageType.CHAT,
            ttl = 5
        )

        val processed = router.processIncomingPacket(incoming)
        assertTrue("Direct message to local node should be processed", processed)

        // Verify message stored in DB as RECEIVED
        val stored = repository.getMessage("msg-101")
        assertNotNull(stored)
        assertEquals(MessageStatus.RECEIVED, stored!!.status)
        assertEquals("Need coordinates", stored.payload)

        // Verify ACK packet was generated and sent across transport
        val ackPacket = transport.sentPackets.firstOrNull { it.messageType == MessageType.ACK }
        assertNotNull("Router must transmit an ACK in response to direct message", ackPacket)
        assertEquals(localNode, ackPacket!!.senderId)
        assertEquals("Node_Bravo", ackPacket.receiverId)
        assertEquals("msg-101", ackPacket.ackForId)
    }

    @Test
    fun testSosBroadcastReceivedAndForwarded(): Unit = runBlocking {
        val sos = MessagePacket.createSos(
            senderId = "Rescue_1",
            payload = "Flash flood at river crossing",
            ttl = 5
        )

        val processed = router.processIncomingPacket(sos)
        assertTrue(processed)

        // SOS must be stored locally as RECEIVED
        val stored = repository.getMessage(sos.id)
        assertNotNull(stored)
        assertEquals(MessageStatus.RECEIVED, stored!!.status)

        // SOS is a broadcast with TTL > 1, so it must also be forwarded onward to neighbors!
        val forwarded = transport.sentPackets.firstOrNull { it.id == sos.id }
        assertNotNull("Broadcast SOS must be relayed across BLE mesh", forwarded)
        assertEquals(4, forwarded!!.ttl)
        assertEquals(1, forwarded.hopCount)
    }

    @Test
    fun testIntermediateHopForwardingDecrementsTtl(): Unit = runBlocking {
        val packetForOther = MessagePacket(
            id = "relay-1",
            senderId = "Node_Bravo",
            receiverId = "Node_Charlie", // Not for localNode (Alpha)
            payload = "Relay this message",
            ttl = 4,
            hopCount = 0
        )

        val processed = router.processIncomingPacket(packetForOther)
        assertTrue(processed)

        // Packet must NOT be marked RECEIVED; should be forwarded
        val forwarded = transport.sentPackets.firstOrNull { it.id == "relay-1" }
        assertNotNull("Intermediary packet should be forwarded", forwarded)
        assertEquals(3, forwarded!!.ttl)
        assertEquals(1, forwarded.hopCount)
    }

    @Test
    fun testPacketWithTtlOneNotForwarded(): Unit = runBlocking {
        val dyingPacket = MessagePacket(
            id = "dead-hop",
            senderId = "Node_Bravo",
            receiverId = "Node_Charlie",
            payload = "Last breath",
            ttl = 1 // TTL is 1, decrementing would reach 0
        )

        transport.clearHistory()
        router.processIncomingPacket(dyingPacket)

        // Packet should NOT be retransmitted over the transport
        val forwarded = transport.sentPackets.firstOrNull { it.id == "dead-hop" }
        assertNull("Packet with TTL <= 1 must not be forwarded", forwarded)
    }

    @Test
    fun testDuplicatePacketDropped(): Unit = runBlocking {
        val packet = MessagePacket(
            id = "dup-1",
            senderId = "Node_Bravo",
            receiverId = localNode,
            payload = "Duplicate test"
        )

        val firstArrival = router.processIncomingPacket(packet)
        assertTrue(firstArrival)

        val secondArrival = router.processIncomingPacket(packet)
        assertFalse("Duplicate message packet must be dropped by deduplication cache", secondArrival)
    }

    @Test
    fun testOfflineStoreAndForwardFlushesOnConnect(): Unit = runBlocking {
        // Disconnect transport (simulate out-of-range / radio off)
        transport.isConnectedState = false

        // Send message while offline
        val sent = router.sendMessage(
            receiverId = "Node_Bravo",
            payload = "Offline message"
        )

        // Message stored in DB with PENDING status
        val stored = repository.getMessage(sent.id)
        assertNotNull(stored)
        assertEquals(MessageStatus.PENDING, stored!!.status)

        // Transport sent nothing while offline
        assertEquals(0, transport.sentPackets.size)

        // Mesh connection restored!
        transport.isConnectedState = true
        router.flushQueue()

        // Packet is now transmitted and marked as SENT
        assertEquals(1, transport.sentPackets.size)
        assertEquals(sent.id, transport.sentPackets[0].id)

        val updated = repository.getMessage(sent.id)
        assertEquals(MessageStatus.SENT, updated!!.status)
    }

    @Test
    fun testEndToEndAckDeliveryUpdatesStatus(): Unit = runBlocking {
        // 1. Send unicast message to Node_Bravo
        val sent = router.sendMessage(
            receiverId = "Node_Bravo",
            payload = "Are you safe?"
        )
        assertEquals(MessageStatus.SENT, repository.getMessage(sent.id)!!.status)

        // 2. Incoming ACK packet from Node_Bravo arrives
        val ack = MessagePacket.createAck(
            originalPacket = sent,
            responderNodeId = "Node_Bravo"
        )

        router.processIncomingPacket(ack)

        // Status must transition to DELIVERED!
        val delivered = repository.getMessage(sent.id)
        assertNotNull(delivered)
        assertEquals(MessageStatus.DELIVERED, delivered!!.status)
    }

    /**
     * In-memory test implementation of [MessageDao].
     */
    private class FakeMessageDao : MessageDao {
        val table = ConcurrentHashMap<String, MessageEntity>()
        private val stateFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

        private fun notifyChange() {
            stateFlow.value = table.values.sortedByDescending { it.timestamp }
        }

        override suspend fun insertMessage(message: MessageEntity) {
            table[message.id] = message
            notifyChange()
        }

        override suspend fun insertMessages(messages: List<MessageEntity>) {
            messages.forEach { table[it.id] = it }
            notifyChange()
        }

        override suspend fun updateMessage(message: MessageEntity) {
            table[message.id] = message
            notifyChange()
        }

        override suspend fun updateStatus(id: String, status: MessageStatus) {
            val existing = table[id]
            if (existing != null) {
                table[id] = existing.copy(status = status)
                notifyChange()
            }
        }

        override suspend fun updateStatusAndRetry(id: String, status: MessageStatus, retryCount: Int) {
            val existing = table[id]
            if (existing != null) {
                table[id] = existing.copy(status = status, retryCount = retryCount)
                notifyChange()
            }
        }

        override suspend fun getMessageById(id: String): MessageEntity? = table[id]

        override fun getAllMessagesFlow(): Flow<List<MessageEntity>> = stateFlow

        override fun getMessagesByTypeFlow(type: MessageType): Flow<List<MessageEntity>> {
            return stateFlow.map { list -> list.filter { it.messageType == type } }
        }

        override fun getSosMessagesFlow(): Flow<List<MessageEntity>> {
            return stateFlow.map { list -> list.filter { it.messageType == MessageType.SOS } }
        }

        override suspend fun getPendingMessages(): List<MessageEntity> {
            return table.values.filter { it.status == MessageStatus.PENDING }.sortedWith(
                compareByDescending<MessageEntity> { it.priority }.thenBy { it.timestamp }
            )
        }

        override suspend fun getMessagesByStatus(status: MessageStatus): List<MessageEntity> {
            return table.values.filter { it.status == status }
        }

        override fun getConversationFlow(myId: String, peerId: String): Flow<List<MessageEntity>> {
            return stateFlow.map { list ->
                list.filter {
                    (it.senderId == peerId && it.receiverId == myId) ||
                    (it.senderId == myId && it.receiverId == peerId) ||
                    (it.receiverId == "*")
                }.sortedBy { it.timestamp }
            }
        }

        override suspend fun deleteMessageById(id: String) {
            table.remove(id)
            notifyChange()
        }

        override suspend fun clearAllMessages() {
            table.clear()
            notifyChange()
        }

        override fun getUnreadCountFlow(): Flow<Int> {
            return stateFlow.map { list -> list.count { it.status == MessageStatus.RECEIVED && !it.isOutgoing } }
        }
    }
}
