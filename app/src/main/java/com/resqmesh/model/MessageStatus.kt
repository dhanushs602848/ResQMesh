package com.resqmesh.model

/**
 * Tracks the delivery and lifecycle state of a message within the local node.
 */
enum class MessageStatus {
    /** Created locally, queued in the local store-and-forward queue. */
    PENDING,

    /** Actively being transmitted or broadcast over BLE. */
    SENDING,

    /** Successfully broadcast or transmitted to one or more BLE mesh neighbors. */
    SENT,

    /** End-to-end delivery confirmed via incoming ACK from the destination node. */
    DELIVERED,

    /** Received by this intermediate node and forwarded onward toward destination. */
    FORWARDED,

    /** Inbound message intended for and successfully received by this node. */
    RECEIVED,

    /** Failed to transmit after exhausting retry attempts or BLE link dropped. */
    FAILED,

    /** Dropped because TTL reached zero or message expired in queue. */
    EXPIRED;

    companion object {
        fun fromString(status: String?): MessageStatus {
            return entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: PENDING
        }
    }
}
