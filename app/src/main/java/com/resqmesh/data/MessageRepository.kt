package com.resqmesh.data

import com.resqmesh.model.MessagePacket
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository providing a clean API for message storage, status tracking, and UI reactivity.
 */
class MessageRepository(private val messageDao: MessageDao) {

    /**
     * Real-time stream of all messages in descending chronological order.
     */
    val allMessagesFlow: Flow<List<MessageEntity>> = messageDao.getAllMessagesFlow()

    /**
     * Real-time stream of SOS emergency alerts for high-visibility UI banners.
     */
    val sosMessagesFlow: Flow<List<MessageEntity>> = messageDao.getSosMessagesFlow()

    /**
     * Real-time stream of unread incoming messages.
     */
    val unreadCountFlow: Flow<Int> = messageDao.getUnreadCountFlow()

    /**
     * Real-time conversation thread between this device and another peer node (or broadcast).
     */
    fun getConversationFlow(myId: String, peerId: String): Flow<List<MessageEntity>> {
        return messageDao.getConversationFlow(myId, peerId)
    }

    /**
     * Persists a message packet to the database.
     */
    suspend fun saveMessage(
        packet: MessagePacket,
        status: MessageStatus,
        isOutgoing: Boolean,
        retryCount: Int = 0
    ): MessageEntity {
        val entity = MessageEntity.fromPacket(packet, status, isOutgoing, retryCount)
        messageDao.insertMessage(entity)
        return entity
    }

    /**
     * Updates the delivery/lifecycle status of an existing message.
     */
    suspend fun updateStatus(messageId: String, status: MessageStatus) {
        messageDao.updateStatus(messageId, status)
    }

    /**
     * Updates both status and retry counter for store-and-forward retransmissions.
     */
    suspend fun updateStatusAndRetry(messageId: String, status: MessageStatus, retryCount: Int) {
        messageDao.updateStatusAndRetry(messageId, status, retryCount)
    }

    /**
     * Marks an outgoing message as delivered when its ACK arrives.
     */
    suspend fun markDelivered(messageId: String) {
        messageDao.updateStatus(messageId, MessageStatus.DELIVERED)
    }

    /**
     * Retrieves all pending messages awaiting transmission, sorted by priority.
     */
    suspend fun getPendingPackets(): List<MessagePacket> {
        return messageDao.getPendingMessages().map { it.toPacket() }
    }

    /**
     * Retrieves a single message by ID.
     */
    suspend fun getMessage(id: String): MessageEntity? {
        return messageDao.getMessageById(id)
    }

    /**
     * Deletes a specific message.
     */
    suspend fun deleteMessage(id: String) {
        messageDao.deleteMessageById(id)
    }

    /**
     * Clears all local message records.
     */
    suspend fun clearAll() {
        messageDao.clearAllMessages()
    }
}
