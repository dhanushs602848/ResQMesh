package com.resqmesh.routing

import com.resqmesh.data.MessageEntity
import com.resqmesh.data.MessageRepository
import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Core routing and messaging engine for ResQMesh.
 *
 * Coordinates:
 * - Incoming message processing & deduplication
 * - Destination inspection (local delivery vs. hop forwarding)
 * - Time-To-Live (TTL) decrement & loop prevention
 * - Strict SOS-first queue prioritization
 * - Offline store-and-forward persistence
 * - End-to-end acknowledgement (ACK) generation and delivery tracking
 * - BLE interface integration with Member 1
 * - Live UI flow integration with Member 3
 */
class MessageRouter(
    val localNodeId: String,
    val repository: MessageRepository,
    val deduplicationCache: DeduplicationCache = DeduplicationCache(),
    val messageQueue: MessageQueue = MessageQueue(),
    val ackManager: AcknowledgementManager = AcknowledgementManager(),
    private var bleTransport: BleMeshTransport? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val queueMutex = Mutex()
    private var workerJob: Job? = null
    private var retryJob: Job? = null

    // Shared flow emitting newly arrived messages destined for this device or broadcasts
    private val _incomingMessages = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MessagePacket> = _incomingMessages.asSharedFlow()

    // Shared flow emitting SOS alerts immediately
    private val _sosAlerts = MutableSharedFlow<MessagePacket>(extraBufferCapacity = 64)
    val sosAlerts: SharedFlow<MessagePacket> = _sosAlerts.asSharedFlow()

    init {
        startWorkerLoops()
    }

    /**
     * Plugs in Member 1's BLE mesh transport implementation.
     */
    fun attachTransport(transport: BleMeshTransport) {
        this.bleTransport = transport
        transport.setPacketReceivedListener { packet ->
            scope.launch(dispatcher) {
                processIncomingPacket(packet)
            }
        }
        // Connection established or restored: flush queued packets
        triggerFlush()
    }

    /**
     * Ingests and processes an incoming packet received from the BLE mesh.
     *
     * @param packet Over-the-air packet.
     * @return True if processed or forwarded; false if dropped (duplicate/expired).
     */
    suspend fun processIncomingPacket(packet: MessagePacket): Boolean {
        // 1. Duplicate check: reject if already seen within deduplication window
        if (!deduplicationCache.record(packet.id)) {
            return false
        }

        // 2. TTL check: reject if packet is already dead
        if (packet.isExpired()) {
            return false
        }

        // 3. ACK packet handling: resolve pending delivery
        if (packet.messageType == MessageType.ACK) {
            val acknowledgedId = packet.ackForId
            if (acknowledgedId != null) {
                val acknowledgedPacket = ackManager.processAck(acknowledgedId)
                if (acknowledgedPacket != null) {
                    repository.updateStatus(acknowledgedId, MessageStatus.DELIVERED)
                }
            }
        }

        val isForMe = packet.isAddressedTo(localNodeId)

        // 4. Inbound destination processing
        if (isForMe) {
            // Save received message to Room database
            repository.saveMessage(
                packet = packet,
                status = MessageStatus.RECEIVED,
                isOutgoing = false
            )

            // Emit to UI flows
            _incomingMessages.tryEmit(packet)
            if (packet.messageType == MessageType.SOS) {
                _sosAlerts.tryEmit(packet)
            }

            // Generate ACK if this is a direct unicast message
            val ackPacket = ackManager.generateAck(packet, localNodeId)
            if (ackPacket != null) {
                enqueueForTransmission(ackPacket)
            }
        }

        // 5. Forwarding / relay check:
        // Forward if the packet is a network broadcast OR addressed to someone else
        val shouldForward = packet.isBroadcast() || !packet.receiverId.equals(localNodeId, ignoreCase = true)

        if (shouldForward && packet.ttl > 1) {
            val forwardPacket = packet.createForwardCopy()

            // Intermediate nodes can optionally record forwarding state
            if (!isForMe) {
                repository.saveMessage(
                    packet = forwardPacket,
                    status = MessageStatus.FORWARDED,
                    isOutgoing = false
                )
            }

            enqueueForTransmission(forwardPacket)
            return true
        }

        return isForMe
    }

    /**
     * Originates and sends a new message created on this device.
     *
     * @param receiverId Destination node ID or [MessagePacket.BROADCAST_ID].
     * @param payload Text content or payload data.
     * @param messageType Type of message.
     * @param priority Scheduling priority.
     * @return The created [MessagePacket].
     */
    suspend fun sendMessage(
        receiverId: String,
        payload: String,
        messageType: MessageType = MessageType.CHAT,
        priority: Int = messageType.defaultPriority,
        ttl: Int = MessagePacket.DEFAULT_TTL
    ): MessagePacket {
        val packet = MessagePacket(
            senderId = localNodeId,
            receiverId = receiverId,
            payload = payload,
            messageType = messageType,
            priority = priority,
            ttl = ttl
        )

        // Save to local database with PENDING status
        repository.saveMessage(
            packet = packet,
            status = MessageStatus.PENDING,
            isOutgoing = true
        )

        // Track for ACK if unicast
        ackManager.trackMessage(packet)

        // Enqueue for BLE transmission
        enqueueForTransmission(packet)
        return packet
    }

    /**
     * Broadcasts an emergency SOS alert across the entire mesh with maximum priority.
     */
    suspend fun sendSosAlert(payload: String): MessagePacket {
        return sendMessage(
            receiverId = MessagePacket.BROADCAST_ID,
            payload = payload,
            messageType = MessageType.SOS,
            priority = MessageType.SOS.defaultPriority,
            ttl = MessagePacket.DEFAULT_TTL
        )
    }

    /**
     * Enqueues a packet into the memory queue and database, then triggers immediate flush.
     */
    fun enqueueForTransmission(packet: MessagePacket) {
        messageQueue.enqueue(packet)
        triggerFlush()
    }

    /**
     * Flushes queued packets to the BLE transport in SOS-first priority order.
     */
    fun triggerFlush() {
        scope.launch(dispatcher) {
            flushQueue()
        }
    }

    /**
     * Core queue flush loop. Dequeues packets in priority order and transmits over BLE.
     */
    suspend fun flushQueue() {
        queueMutex.withLock {
            val transport = bleTransport
            if (transport == null || !transport.isConnected()) {
                // Offline: store-and-forward preserves packets in messageQueue and Room DB
                return
            }

            while (!messageQueue.isEmpty()) {
                val packet = messageQueue.peek() ?: break

                // If expired, discard
                if (packet.isExpired()) {
                    messageQueue.dequeue()
                    repository.updateStatus(packet.id, MessageStatus.EXPIRED)
                    continue
                }

                // Attempt BLE transmission
                val success = try {
                    transport.sendPacket(packet)
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    messageQueue.dequeue()
                    // If this was an outgoing message originated by us, mark as SENT
                    if (packet.senderId == localNodeId) {
                        repository.updateStatus(packet.id, MessageStatus.SENT)
                    }
                } else {
                    // BLE transport busy or queue full; leave packet in queue for next flush
                    break
                }
            }
        }
    }

    /**
     * Restores un-transmitted PENDING messages from the Room database into the in-memory queue.
     * Called during app startup for offline store-and-forward resilience.
     */
    suspend fun restorePendingMessages() {
        val pendingPackets = repository.getPendingPackets()
        messageQueue.enqueueAll(pendingPackets)
        triggerFlush()
    }

    /**
     * Starts background workers for periodic queue flushing and ACK timeout retries.
     */
    private fun startWorkerLoops() {
        workerJob?.cancel()
        workerJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(1000L)
                if (!messageQueue.isEmpty() && bleTransport?.isConnected() == true) {
                    flushQueue()
                }
            }
        }

        retryJob?.cancel()
        retryJob = scope.launch(dispatcher) {
            while (isActive) {
                delay(2000L)
                handleAckRetries()
            }
        }
    }

    /**
     * Checks unacknowledged messages and requeues those needing retransmission.
     */
    private suspend fun handleAckRetries() {
        val retries = ackManager.collectRetries(
            onFailed = { failedPacket ->
                scope.launch(dispatcher) {
                    repository.updateStatus(failedPacket.id, MessageStatus.FAILED)
                }
            }
        )

        for (packet in retries) {
            // Re-enqueue for transmission
            messageQueue.enqueue(packet)
            repository.updateStatusAndRetry(packet.id, MessageStatus.PENDING, retryCount = 1)
        }

        if (retries.isNotEmpty()) {
            triggerFlush()
        }
    }

    /**
     * Shuts down background loops (e.g. when application or service terminates).
     */
    fun shutdown() {
        workerJob?.cancel()
        retryJob?.cancel()
    }
}
