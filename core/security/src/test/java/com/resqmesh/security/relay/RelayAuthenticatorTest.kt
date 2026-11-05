package com.resqmesh.security.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class RelayAuthenticatorTest {

    private val relayKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private lateinit var authenticator: HmacRelayAuthenticator

    @Before
    fun setUp() {
        authenticator = HmacRelayAuthenticator(relayKeyBytes)
    }

    @Test
    fun `valid relay token generated and verified successfully`() {
        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 1

        val token = authenticator.signRelayHop(senderNodeId, messageId, hopCount)
        assertTrue(token.isNotEmpty())
        assertEquals(32, token.length) // 16 bytes = 32 hex chars

        val isValid = authenticator.verifyRelayHop(senderNodeId, messageId, hopCount, token)
        assertTrue("Valid token must be verified", isValid)
    }

    @Test
    fun `tampered senderNodeId is rejected by relay authenticator`() {
        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 1

        val token = authenticator.signRelayHop(senderNodeId, messageId, hopCount)

        // Attacker claims transmission came from RQM-NODE-ROGUE
        val isValid = authenticator.verifyRelayHop("RQM-NODE-ROGUE", messageId, hopCount, token)
        assertFalse("Tampered senderNodeId must be rejected", isValid)
    }

    @Test
    fun `tampered hopCount is rejected by relay authenticator`() {
        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 3

        val token = authenticator.signRelayHop(senderNodeId, messageId, hopCount)

        // Attacker reduces hopCount to 1 to bypass TTL / maxHops
        val isValid = authenticator.verifyRelayHop(senderNodeId, messageId, 1, token)
        assertFalse("Tampered hopCount must be rejected", isValid)
    }

    @Test
    fun `tampered messageId is rejected by relay authenticator`() {
        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 2

        val token = authenticator.signRelayHop(senderNodeId, messageId, hopCount)

        val isValid = authenticator.verifyRelayHop(senderNodeId, UUID.randomUUID().toString(), hopCount, token)
        assertFalse("Mismatched messageId must be rejected", isValid)
    }

    @Test
    fun `blank or corrupted token is rejected`() {
        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 1

        assertFalse(authenticator.verifyRelayHop(senderNodeId, messageId, hopCount, ""))
        assertFalse(authenticator.verifyRelayHop(senderNodeId, messageId, hopCount, "   "))
        assertFalse(authenticator.verifyRelayHop(senderNodeId, messageId, hopCount, "invalid-hex-token"))
    }

    @Test
    fun `different relay key produces non-matching token`() {
        val otherKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val otherAuthenticator = HmacRelayAuthenticator(otherKeyBytes)

        val senderNodeId = "RQM-NODE-B"
        val messageId = UUID.randomUUID().toString()
        val hopCount = 1

        val token = authenticator.signRelayHop(senderNodeId, messageId, hopCount)
        val isValid = otherAuthenticator.verifyRelayHop(senderNodeId, messageId, hopCount, token)
        assertFalse("Different secret key must fail verification", isValid)
    }
}
