package com.resqmesh.security.replay

import java.util.LinkedHashMap
import java.util.UUID

class InMemoryReplayProtectionValidator(
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ReplayProtectionValidator {

    companion object {
        const val EXPIRY_MS = 600_000L
        const val MAX_CACHE_SIZE = 1_000
    }

    private val cache = LinkedHashMap<String, Long>(16, 0.75f, true)

    @Synchronized
    override fun validateAndRecord(messageId: String, timestamp: Long): Boolean {
        if (!isValidUuidV4(messageId)) return false

        val now = nowMillis()

        val difference = try {
            Math.subtractExact(timestamp, now)
        } catch (e: ArithmeticException) {
            return false
        }

        if (difference < -EXPIRY_MS || difference > EXPIRY_MS) {
            return false
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

        if (cache.containsKey(messageId)) return false

        cache[messageId] = now

        while (cache.size > MAX_CACHE_SIZE) {
            val eldest = cache.entries.iterator()
            eldest.next()
            eldest.remove()
        }

        return true
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
