package com.resqmesh.security.replay

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class InMemoryReplayProtectionValidatorTest {

    private var now = 1_800_000_000_000L

    private fun newValidator() =
        InMemoryReplayProtectionValidator { now }

    @Test
    fun firstMessageIsAccepted() {
        val validator = newValidator()
        val id = UUID.randomUUID().toString()

        assertTrue(validator.validateAndRecord(id, now))
    }

    @Test
    fun duplicateMessageIsRejected() {
        val validator = newValidator()
        val id = UUID.randomUUID().toString()

        assertTrue(validator.validateAndRecord(id, now))
        assertFalse(validator.validateAndRecord(id, now))
    }

    @Test
    fun invalidUuidIsRejected() {
        val validator = newValidator()

        assertFalse(validator.validateAndRecord("not-a-uuid", now))
        assertFalse(validator.validateAndRecord(
            "550e8400-e29b-41d4-a716-446655440000", now
        ))
    }

    @Test
    fun timestampsOutsideTenMinuteWindowAreRejected() {
        val validator = newValidator()

        assertFalse(validator.validateAndRecord(
            UUID.randomUUID().toString(), now - 600_001L
        ))
        assertFalse(validator.validateAndRecord(
            UUID.randomUUID().toString(), now + 600_001L
        ))
    }

    @Test
    fun exactTenMinuteBoundariesAreAccepted() {
        val validator = newValidator()

        assertTrue(validator.validateAndRecord(
            UUID.randomUUID().toString(), now - 600_000L
        ))
        assertTrue(validator.validateAndRecord(
            UUID.randomUUID().toString(), now + 600_000L
        ))
    }

    @Test
    fun expiredMessageCanBeAcceptedAgain() {
        val validator = newValidator()
        val id = UUID.randomUUID().toString()

        assertTrue(validator.validateAndRecord(id, now))

        now += 600_000L

        assertTrue(validator.validateAndRecord(id, now))
    }

    @Test
    fun clearResetsReplayCache() {
        val validator = newValidator()
        val id = UUID.randomUUID().toString()

        assertTrue(validator.validateAndRecord(id, now))
        validator.clear()

        assertTrue(validator.validateAndRecord(id, now))
    }

    @Test
    fun cacheNeverExceedsMaximumSize() {
        val validator = newValidator()
        val ids = (1..1001).map {
            UUID.randomUUID().toString()
        }

        ids.take(1000).forEach {
            assertTrue(validator.validateAndRecord(it, now))
        }

        assertTrue(validator.validateAndRecord(ids.last(), now))

        // The oldest entry should have been evicted.
        assertTrue(validator.validateAndRecord(ids.first(), now))
    }
}
