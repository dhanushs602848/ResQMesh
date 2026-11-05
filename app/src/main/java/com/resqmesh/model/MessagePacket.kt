package com.resqmesh.model

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Represents the fundamental over-the-air packet exchanged across the ResQMesh BLE network.
 *
 * @property id Unique message identifier (UUID v4 string).
 * @property senderId Identifier of the originating node/device.
 * @property receiverId Target node identifier, or [BROADCAST_ID] for network-wide broadcasts.
 * @property payload The message body, text, or serialized telemetry.
 * @property timestamp Unix epoch timestamp in milliseconds when packet was generated.
 * @property messageType Functional category ([MessageType.SOS], [MessageType.CHAT], etc.).
 * @property ttl Time-To-Live hop counter. Decremented on each relay. Dropped when <= 0.
 * @property priority Scheduling priority. Higher numbers are dequeued and forwarded first.
 * @property hopCount Number of intermediate relay hops this packet has traversed.
 * @property ackForId For ACK packets, references the target message UUID being acknowledged.
 */
data class MessagePacket(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val receiverId: String = BROADCAST_ID,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.CHAT,
    val ttl: Int = DEFAULT_TTL,
    val priority: Int = messageType.defaultPriority,
    val hopCount: Int = 0,
    val ackForId: String? = null
) : Comparable<MessagePacket> {

    /**
     * True if the packet's TTL has expired and should not be forwarded.
     */
    fun isExpired(): Boolean = ttl <= 0

    /**
     * True if this message is intended for all nodes in the mesh.
     */
    fun isBroadcast(): Boolean = receiverId == BROADCAST_ID || receiverId.equals("ALL", ignoreCase = true)

    /**
     * Checks if this packet is addressed to the specified node ID or is a broadcast.
     */
    fun isAddressedTo(nodeId: String): Boolean {
        return isBroadcast() || receiverId.equals(nodeId, ignoreCase = true)
    }

    /**
     * Returns a new packet with TTL decremented by 1 and hopCount incremented by 1.
     */
    fun createForwardCopy(): MessagePacket {
        return copy(
            ttl = (ttl - 1).coerceAtLeast(0),
            hopCount = hopCount + 1
        )
    }

    /**
     * Natural ordering for PriorityQueues:
     * Higher priority comes first. If priorities are equal, older timestamps come first (FIFO).
     */
    override fun compareTo(other: MessagePacket): Int {
        val priorityComparison = other.priority.compareTo(this.priority)
        if (priorityComparison != 0) return priorityComparison
        return this.timestamp.compareTo(other.timestamp)
    }

    /**
     * Compact pipe-delimited wire encoding suitable for BLE transfer.
     * Coordinate with Member 4 for protocol compliance.
     */
    fun toWireString(): String {
        val sanitizedPayload = payload.replace("\n", "\\n").replace("|", "\\|")
        val ackField = ackForId ?: ""
        return "$id|$senderId|$receiverId|${messageType.name}|$ttl|$priority|$timestamp|$hopCount|$ackField|$sanitizedPayload"
    }

    fun toByteArray(): ByteArray = toWireString().toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val BROADCAST_ID = "*"
        const val DEFAULT_TTL = 7
        const val MAX_TTL = 15

        /**
         * Creates an emergency SOS packet with critical priority and broadcast destination.
         */
        fun createSos(
            senderId: String,
            payload: String,
            ttl: Int = DEFAULT_TTL
        ): MessagePacket {
            return MessagePacket(
                id = UUID.randomUUID().toString(),
                senderId = senderId,
                receiverId = BROADCAST_ID,
                payload = payload,
                timestamp = System.currentTimeMillis(),
                messageType = MessageType.SOS,
                ttl = ttl,
                priority = MessageType.SOS.defaultPriority,
                hopCount = 0
            )
        }

        /**
         * Creates an ACK packet in response to a received direct message.
         */
        fun createAck(
            originalPacket: MessagePacket,
            responderNodeId: String
        ): MessagePacket {
            return MessagePacket(
                id = UUID.randomUUID().toString(),
                senderId = responderNodeId,
                receiverId = originalPacket.senderId,
                payload = "ACK:${originalPacket.id}",
                timestamp = System.currentTimeMillis(),
                messageType = MessageType.ACK,
                ttl = DEFAULT_TTL,
                priority = MessageType.ACK.defaultPriority,
                hopCount = 0,
                ackForId = originalPacket.id
            )
        }

        /**
         * Parses a wire-format string into a [MessagePacket].
         */
        fun fromWireString(wire: String): MessagePacket? {
            return try {
                // Format: id|senderId|receiverId|messageType|ttl|priority|timestamp|hopCount|ackField|payload
                val parts = wire.split("|", limit = 10)
                if (parts.size < 10) return null

                val id = parts[0]
                val senderId = parts[1]
                val receiverId = parts[2]
                val type = MessageType.fromString(parts[3])
                val ttl = parts[4].toIntOrNull() ?: DEFAULT_TTL
                val priority = parts[5].toIntOrNull() ?: type.defaultPriority
                val timestamp = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                val hopCount = parts[7].toIntOrNull() ?: 0
                val ackForId = parts[8].ifEmpty { null }
                val payload = parts[9].replace("\\|", "|").replace("\\n", "\n")

                MessagePacket(
                    id = id,
                    senderId = senderId,
                    receiverId = receiverId,
                    payload = payload,
                    timestamp = timestamp,
                    messageType = type,
                    ttl = ttl,
                    priority = priority,
                    hopCount = hopCount,
                    ackForId = ackForId
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromByteArray(bytes: ByteArray): MessagePacket? {
            val wire = String(bytes, StandardCharsets.UTF_8)
            return fromWireString(wire)
        }
    }
}
