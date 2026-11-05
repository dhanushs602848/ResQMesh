package com.resqmesh.routing

import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageType
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages end-to-end delivery acknowledgements (ACKs) and retransmissions.
 *
 * Responsibilities:
 * 1. Generates ACKs when direct messages arrive at their final destination.
 * 2. Tracks outgoing unicast messages pending ACK confirmation.
 * 3. Identifies unacknowledged messages eligible for store-and-forward retransmission.
 * 4. Resolves delivery status to DELIVERED or FAILED.
 */
class AcknowledgementManager(
    private val maxRetries: Int = 3,
    private val ackTimeoutMs: Long = 5000L
) {
    /**
     * State container for an in-flight message awaiting ACK.
     */
    data class PendingAck(
        val packet: MessagePacket,
        var retryCount: Int = 0,
        var lastSentTimestamp: Long = System.currentTimeMillis()
    )

    private val pendingAcks = ConcurrentHashMap<String, PendingAck>()

    /**
     * Registers an outgoing packet that requires end-to-end delivery confirmation.
     * Broadcasts and ACK packets are not tracked because they do not require an ACK.
     */
    fun trackMessage(packet: MessagePacket) {
        if (packet.isBroadcast() || packet.messageType == MessageType.ACK) {
            return
        }
        pendingAcks[packet.id] = PendingAck(
            packet = packet,
            retryCount = 0,
            lastSentTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Processes an incoming ACK packet.
     *
     * @param ackForMessageId The original message UUID acknowledged.
     * @return The original [MessagePacket] that was confirmed, or null if unknown/already resolved.
     */
    fun processAck(ackForMessageId: String?): MessagePacket? {
        if (ackForMessageId == null) return null
        val resolved = pendingAcks.remove(ackForMessageId)
        return resolved?.packet
    }

    /**
     * Checks all pending messages and returns those that have timed out and need retransmission.
     * If a message has reached [maxRetries], it is permanently removed and included in the failed list.
     *
     * @param currentTime Current system time.
     * @param onFailed Callback invoked when a message has exceeded its maximum retry limit.
     * @return List of packets to be retransmitted.
     */
    fun collectRetries(
        currentTime: Long = System.currentTimeMillis(),
        onFailed: ((MessagePacket) -> Unit)? = null
    ): List<MessagePacket> {
        val packetsToRetry = mutableListOf<MessagePacket>()

        for ((id, pending) in pendingAcks) {
            if (currentTime - pending.lastSentTimestamp >= ackTimeoutMs) {
                if (pending.retryCount >= maxRetries) {
                    // Maximum attempts exhausted
                    pendingAcks.remove(id)
                    onFailed?.invoke(pending.packet)
                } else {
                    // Schedule retransmission
                    pending.retryCount++
                    pending.lastSentTimestamp = currentTime
                    packetsToRetry.add(pending.packet)
                }
            }
        }

        return packetsToRetry
    }

    /**
     * Generates an ACK packet if the received packet is a unicast message intended for this node.
     * Returns null if the packet is a broadcast or already an ACK (preventing ACK loops).
     */
    fun generateAck(packet: MessagePacket, localNodeId: String): MessagePacket? {
        // Do not acknowledge broadcasts or ACK packets
        if (packet.isBroadcast() || packet.messageType == MessageType.ACK) {
            return null
        }

        // Only generate ACK if this device is the designated recipient
        if (packet.receiverId.equals(localNodeId, ignoreCase = true)) {
            return MessagePacket.createAck(
                originalPacket = packet,
                responderNodeId = localNodeId
            )
        }

        return null
    }

    /**
     * Checks if a specific message is currently awaiting ACK.
     */
    fun isPending(messageId: String): Boolean = pendingAcks.containsKey(messageId)

    /**
     * Returns the number of messages currently awaiting ACK.
     */
    fun pendingCount(): Int = pendingAcks.size

    /**
     * Removes an in-flight message without waiting for an ACK.
     */
    fun cancelTracking(messageId: String) {
        pendingAcks.remove(messageId)
    }

    /**
     * Clears all tracking state.
     */
    fun clear() {
        pendingAcks.clear()
    }
}
