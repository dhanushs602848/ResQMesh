package com.resqmesh.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType

/**
 * Room database entity storing messages persisted locally on the device.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["priority"]),
        Index(value = ["status"]),
        Index(value = ["senderId"]),
        Index(value = ["receiverId"]),
        Index(value = ["messageType"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val receiverId: String,
    val payload: String,
    val timestamp: Long,
    val messageType: MessageType,
    val ttl: Int,
    val priority: Int,
    val status: MessageStatus,
    val hopCount: Int = 0,
    val ackForId: String? = null,
    val isOutgoing: Boolean = false,
    val retryCount: Int = 0
) {
    /**
     * Converts this stored entity back into a network-ready [MessagePacket].
     */
    fun toPacket(): MessagePacket {
        return MessagePacket(
            id = id,
            senderId = senderId,
            receiverId = receiverId,
            payload = payload,
            timestamp = timestamp,
            messageType = messageType,
            ttl = ttl,
            priority = priority,
            hopCount = hopCount,
            ackForId = ackForId
        )
    }

    companion object {
        fun fromPacket(
            packet: MessagePacket,
            status: MessageStatus,
            isOutgoing: Boolean,
            retryCount: Int = 0
        ): MessageEntity {
            return MessageEntity(
                id = packet.id,
                senderId = packet.senderId,
                receiverId = packet.receiverId,
                payload = packet.payload,
                timestamp = packet.timestamp,
                messageType = packet.messageType,
                ttl = packet.ttl,
                priority = packet.priority,
                status = status,
                hopCount = packet.hopCount,
                ackForId = packet.ackForId,
                isOutgoing = isOutgoing,
                retryCount = retryCount
            )
        }
    }
}
