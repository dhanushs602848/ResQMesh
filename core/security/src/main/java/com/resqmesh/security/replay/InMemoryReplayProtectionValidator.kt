package com.resqmesh.security.replay

import java.util.LinkedHashMap
import java.util.UUID

class InMemoryReplayProtectionValidator(
    val allowLegacyFormat: Boolean = false,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ReplayProtectionValidator {

    companion object {
        const val EXPIRY_MS = 600_000L
        const val MAX_CACHE_SIZE = 1_000
        private val LEGACY_ID_REGEX = Regex("^[A-Za-z0-9._-]+$")
    }

    private val cache = LinkedHashMap<String, Long>(16, 0.75f, true)

    @Synchronized
    override fun checkAndRecord(messageId: String, timestamp: Long): ReplayValidationResult {
        if (!isValidMessageId(messageId)) return ReplayValidationResult.INVALID_FORMAT

        val now = nowMillis()

        val difference = try {
            Math.subtractExact(timestamp, now)
        } catch (e: ArithmeticException) {
            return ReplayValidationResult.EXPIRED
        }

        if (difference < -EXPIRY_MS || difference > EXPIRY_MS) {
            return ReplayValidationResult.EXPIRED
        }

        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val age = try {
                Math.subtractExact(now, iterator.next().value)
            } catch (e: ArithmeticException) {
                Long.MAX_VALUE
            }

            if (age >= EXPIRY_MS) {
                iterator.remove()
            }
        }

        if (cache.containsKey(messageId)) return ReplayValidationResult.DUPLICATE

        cache[messageId] = now

        while (cache.size > MAX_CACHE_SIZE) {
            val eldest = cache.entries.iterator()
            eldest.next()
            eldest.remove()
        }

        return ReplayValidationResult.VALID
    }

    @Synchronized
    override fun validateAndRecord(messageId: String, timestamp: Long): Boolean {
        return checkAndRecord(messageId, timestamp) == ReplayValidationResult.VALID
    }

    private fun isValidMessageId(messageId: String): Boolean {
        if (isValidUuidV4(messageId)) return true
        if (allowLegacyFormat) {
            return messageId.isNotBlank() &&
                messageId.length <= 64 &&
                LEGACY_ID_REGEX.matches(messageId)
        }
        return false
    }

    private fun isValidUuidV4(messageId: String): Boolean {
        return try {
            val uuid = UUID.fromString(messageId)
            uuid.version() == 4 &&
                uuid.variant() == 2 &&
                uuid.toString() == messageId
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    @Synchronized
    override fun clear() {
        cache.clear()
    }
}
