package com.resqmesh.bletester.model

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Fundamental wire-format packet exchanged over BLE mesh links.
 * Fully compatible with the ResQMesh protocol specification.
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

    fun isExpired(): Boolean = ttl <= 0

    fun isBroadcast(): Boolean = receiverId == BROADCAST_ID || receiverId.equals("ALL", ignoreCase = true)

    fun isAddressedTo(nodeId: String): Boolean {
        return isBroadcast() || receiverId.equals(nodeId, ignoreCase = true)
    }

    fun createForwardCopy(): MessagePacket {
        return copy(
            ttl = (ttl - 1).coerceAtLeast(0),
            hopCount = hopCount + 1
        )
    }

    override fun compareTo(other: MessagePacket): Int {
        val priorityComparison = other.priority.compareTo(this.priority)
        if (priorityComparison != 0) return priorityComparison
        return this.timestamp.compareTo(other.timestamp)
    }

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

        // ResQMesh Standard BLE UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE2D-0000-1000-8000-00805F9B34FB")

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

        fun fromWireString(wire: String): MessagePacket? {
            return try {
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
