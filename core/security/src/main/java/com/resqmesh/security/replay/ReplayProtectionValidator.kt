package com.resqmesh.security.replay

interface ReplayProtectionValidator {

    /**
     * Checks whether a message is fresh and has not already been processed.
     *
     * @param messageId Unique UUID v4 message ID.
     * @param timestamp Message creation time in epoch milliseconds.
     * @return true if valid and new, false if duplicate or timestamp is invalid.
     */
    fun validateAndRecord(messageId: String, timestamp: Long): Boolean

    /** Clears the replay cache. */
    fun clear()
}
