package com.resqmesh.security.relay

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Interface for authenticating hop-by-hop relay forwarding across mesh nodes.
 *
 * Provides cryptographic binding for mutable relay headers (senderNodeId, hopCount, messageId)
 * so that intermediate nodes forwarding an envelope without decrypting it can have their hop
 * transitions verified by downstream receivers.
 */
interface RelayAuthenticator {
    /**
     * Computes a relay authentication token for a hop transition.
     *
     * @param senderNodeId The node ID transmitting this hop.
     * @param messageId The unique message ID from the authenticated header.
     * @param hopCount The incremented hop count for this hop.
     * @return A token authenticating this hop transition.
     */
    fun signRelayHop(senderNodeId: String, messageId: String, hopCount: Int): String

    /**
     * Verifies that the relay authentication token matches the claimed hop transition.
     *
     * @param senderNodeId The node ID transmitting this hop.
     * @param messageId The unique message ID from the authenticated header.
     * @param hopCount The claimed hop count.
     * @param token The token received in the relay header.
     * @return true if valid, false if tampered or forged.
     */
    fun verifyRelayHop(senderNodeId: String, messageId: String, hopCount: Int, token: String): Boolean
}

/**
 * HMAC-SHA256 implementation of [RelayAuthenticator].
 *
 * Computes a truncated HMAC-SHA256 over:
 * `"$senderNodeId:$messageId:$hopCount"`
 *
 * This allows nodes possessing the relay/mesh network key to authenticate hop transitions
 * without needing access to the end-to-end emergency payload or responder group key.
 */
class HmacRelayAuthenticator(
    private val relaySecretKey: SecretKey
) : RelayAuthenticator {

    constructor(secretKeyBytes: ByteArray) : this(SecretKeySpec(secretKeyBytes, ALGORITHM))

    override fun signRelayHop(senderNodeId: String, messageId: String, hopCount: Int): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(relaySecretKey)
        val data = "$senderNodeId:$messageId:$hopCount".toByteArray(StandardCharsets.UTF_8)
        val rawHmac = mac.doFinal(data)
        return rawHmac.take(TOKEN_BYTES).joinToString("") { "%02x".format(it) }
    }

    override fun verifyRelayHop(senderNodeId: String, messageId: String, hopCount: Int, token: String): Boolean {
        if (token.isBlank()) return false
        val expectedToken = signRelayHop(senderNodeId, messageId, hopCount)
        return MessageDigest.isEqual(
            token.toByteArray(StandardCharsets.UTF_8),
            expectedToken.toByteArray(StandardCharsets.UTF_8)
        )
    }

    companion object {
        const val ALGORITHM = "HmacSHA256"
        const val TOKEN_BYTES = 16
    }
}
