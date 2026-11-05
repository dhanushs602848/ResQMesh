package com.resqmesh.security.replay

/**
 * Result of checking message freshness and uniqueness against the replay protection cache.
 */
enum class ReplayValidationResult {
    /** Message is within the freshness window and has not been seen before. */
    VALID,

    /** Message ID was already processed within the active cache window. */
    DUPLICATE,

    /** Message timestamp is outside the allowable clock-skew window (too old or far in future). */
    EXPIRED,

    /** Message ID does not conform to required format rules (e.g. invalid UUID v4). */
    INVALID_FORMAT
}

interface ReplayProtectionValidator {

    /**
     * Checks whether a message is fresh and has not already been processed.
     *
     * @param messageId Unique UUID v4 (or legacy if enabled) message ID.
     * @param timestamp Message creation time in epoch milliseconds.
     * @return true if valid and new, false if duplicate, expired, or format is invalid.
     */
    fun validateAndRecord(messageId: String, timestamp: Long): Boolean {
        return checkAndRecord(messageId, timestamp) == ReplayValidationResult.VALID
    }

    /**
     * Checks whether a message is fresh, returning a distinct [ReplayValidationResult].
     *
     * @param messageId Unique message ID.
     * @param timestamp Message creation time in epoch milliseconds.
     * @return [ReplayValidationResult] classifying validity, duplicate, or expired status.
     */
    fun checkAndRecord(messageId: String, timestamp: Long): ReplayValidationResult {
        return if (validateAndRecord(messageId, timestamp)) {
            ReplayValidationResult.VALID
        } else {
            ReplayValidationResult.DUPLICATE
        }
    }

    /** Clears the replay cache. */
    fun clear()
}
