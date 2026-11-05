package com.resqmesh.data

import androidx.room.TypeConverter
import com.resqmesh.model.MessageStatus
import com.resqmesh.model.MessageType

/**
 * Type converters for Room to persist enum fields safely as strings.
 */
class Converters {

    @TypeConverter
    fun fromMessageType(type: MessageType?): String? = type?.name

    @TypeConverter
    fun toMessageType(value: String?): MessageType = MessageType.fromString(value)

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus?): String? = status?.name

    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus = MessageStatus.fromString(value)
}
