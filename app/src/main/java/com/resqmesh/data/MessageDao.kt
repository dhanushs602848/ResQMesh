package com.resqmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for ResQMesh local message persistence.
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status, retryCount = :retryCount WHERE id = :id")
    suspend fun updateStatusAndRetry(id: String, status: MessageStatus, retryCount: Int)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = :type ORDER BY timestamp DESC")
    fun getMessagesByTypeFlow(type: MessageType): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType = 'SOS' ORDER BY timestamp DESC")
    fun getSosMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'PENDING' ORDER BY priority DESC, timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = :status ORDER BY priority DESC, timestamp ASC")
    suspend fun getMessagesByStatus(status: MessageStatus): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE (senderId = :peerId AND receiverId = :myId) OR (senderId = :myId AND receiverId = :peerId) OR receiverId = '*' ORDER BY timestamp ASC")
    fun getConversationFlow(myId: String, peerId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM messages WHERE status = 'RECEIVED' AND isOutgoing = 0")
    fun getUnreadCountFlow(): Flow<Int>
}
