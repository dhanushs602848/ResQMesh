package com.resqmesh.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeduplicationCacheTest {

    private lateinit var cache: DeduplicationCache

    @Before
    fun setUp() {
        cache = DeduplicationCache(capacity = 5, expirationWindowMs = 1000L)
    }

    @Test
    fun testRecordNewPacketReturnsTrue() {
        val isNew = cache.record("pkt-1")
        assertTrue("First insertion of packet ID should be recognized as new", isNew)
        assertTrue(cache.hasSeen("pkt-1"))
        assertEquals(1, cache.size())
    }

    @Test
    fun testDuplicatePacketReturnsFalse() {
        cache.record("pkt-1")
        val isDuplicateNew = cache.record("pkt-1")
        assertFalse("Second insertion of same packet ID should be recognized as duplicate", isDuplicateNew)
        assertEquals(1, cache.size())
    }

    @Test
    fun testLruEviction() {
        val smallCache = DeduplicationCache(capacity = 3, expirationWindowMs = 60000L)

        smallCache.record("pkt-1")
        smallCache.record("pkt-2")
        smallCache.record("pkt-3")
        assertEquals(3, smallCache.size())

        // Adding 4th should evict eldest (pkt-1)
        smallCache.record("pkt-4")
        assertEquals(3, smallCache.size())
        assertFalse(smallCache.hasSeen("pkt-1"))
        assertTrue(smallCache.hasSeen("pkt-2"))
        assertTrue(smallCache.hasSeen("pkt-3"))
        assertTrue(smallCache.hasSeen("pkt-4"))
    }

    @Test
    fun testClear() {
        cache.record("pkt-1")
        cache.record("pkt-2")
        assertEquals(2, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
        assertFalse(cache.hasSeen("pkt-1"))
    }
}
