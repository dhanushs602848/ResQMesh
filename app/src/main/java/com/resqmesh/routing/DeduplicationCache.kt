package com.resqmesh.routing

/**
 * Thread-safe, bounded Least-Recently-Used (LRU) and time-windowed deduplication cache.
 * Prevents message forwarding storms, echo loops, and redundant database operations.
 *
 * @param capacity Maximum number of message IDs to remember simultaneously.
 * @param expirationWindowMs Time in milliseconds before an entry is considered expired.
 */
class DeduplicationCache(
    private val capacity: Int = 1000,
    private val expirationWindowMs: Long = 10 * 60 * 1000L // 10 minutes default
) {
    private val lock = Any()

    // Maps packetId -> timestamp when seen
    private val cache = object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > capacity
        }
    }

    /**
     * Checks if a packet ID has been seen within the valid expiration window.
     *
     * @param packetId The unique message identifier to inspect.
     * @return True if the packet was already processed, false otherwise.
     */
    fun hasSeen(packetId: String): Boolean {
        synchronized(lock) {
            val timestamp = cache[packetId] ?: return false
            val now = System.currentTimeMillis()
            if (now - timestamp > expirationWindowMs) {
                cache.remove(packetId)
                return false
            }
            return true
        }
    }

    /**
     * Records a packet ID in the cache.
     *
     * @param packetId The unique message identifier.
     * @return True if this is a newly recorded packet, false if it was already in cache.
     */
    fun record(packetId: String): Boolean {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val existing = cache[packetId]
            if (existing != null && (now - existing) <= expirationWindowMs) {
                return false // Duplicate
            }
            cache[packetId] = now
            return true
        }
    }

    /**
     * Returns the current number of cached message IDs.
     */
    fun size(): Int {
        synchronized(lock) {
            cleanExpiredEntries()
            return cache.size
        }
    }

    /**
     * Clears all cached IDs (useful during node reset or unit tests).
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > expirationWindowMs) {
                iterator.remove()
            }
        }
    }
}
