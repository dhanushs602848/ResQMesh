package com.resqmesh.security.envelope

/**
 * End-to-end immutable metadata authenticated via AES-256-GCM Additional Authenticated Data (AAD).
 *
 * CRITICAL SECURITY INVARIANT:
 * Every field in this header is cryptographically bound into the AES-GCM AEAD authentication tag.
 * Any modification of these fields in transit (by an attacker or intermediate relay) causes immediate
 * authentication failure and rejection upon arrival at the destination node.
 *
 * GROUP KEY LIMITATION NOTICE:
 * Because AES-GCM uses a symmetric group key, any authorized node holding the key can produce valid tags.
 * Therefore, AES-GCM authentication proves that the message was created by a legitimate member of the group,
 * but it DOES NOT authenticate the individual identity of [originNodeId] against impersonation by another
 * group member (which requires asymmetric digital signatures such as Ed25519).
 */
data class AuthenticatedEnvelopeHeader(
    val version: Int = CURRENT_VERSION,
    val messageId: String,       // Unique message identifier (UUID v4 format)
    val originNodeId: String,    // Node identifier of original emergency author (e.g. "RQM-A1B2")
    val keyId: String,           // Key identifier of group secret in Keystore (e.g. "responder-group-alpha")
    val timestamp: Long,         // Original creation timestamp in epoch milliseconds
    val priority: Int,           // Incident priority level (e.g., 1=LOW, 2=MEDIUM, 3=HIGH, 4=CRITICAL)
    val payloadType: Int         // Envelope payload classification (e.g., 1=SOS, 2=ACK, 3=TELEMETRY)
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_ID_LENGTH = 64
    }
}

/**
 * Hop-by-hop mutable relay metadata.
 *
 * CRITICAL ARCHITECTURAL DISTINCTION:
 * These fields change at each relay hop as the packet floods across the BLE mesh.
 * They MUST NOT be included in the end-to-end AAD.
 *
 * This separation allows intermediate relay nodes (which may not possess the responder group key)
 * to increment [hopCount] and set [senderNodeId] without invalidating the end-to-end cryptographic tag.
 */
data class MutableRelayHeader(
    val senderNodeId: String,    // Node ID of the immediate hop transmitter (e.g. "RQM-C3D4")
    val hopCount: Int,           // Number of hops traversed so far (incremented at each relay)
    val maxHops: Int,            // Maximum allowable hop limit (e.g. 7)
    val relayAuthToken: String? = null // Optional hop authentication token verifying relay transition
) {
    companion object {
        const val MAX_HOPS_LIMIT = 32
        const val MAX_ID_LENGTH = 64
        const val MAX_TOKEN_LENGTH = 128
    }
}

/**
 * Complete encrypted message envelope holding mutable relay metadata, authenticated end-to-end
 * metadata, a unique 12-byte initialization vector, and the AES-256-GCM ciphertext.
 */
data class EncryptedMeshEnvelope(
    val relayHeader: MutableRelayHeader,
    val authenticatedHeader: AuthenticatedEnvelopeHeader,
    val nonce: ByteArray,        // 12-byte AES-GCM IV (must be cryptographically random per message)
    val ciphertext: ByteArray    // AEAD ciphertext + 16-byte authentication tag
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedMeshEnvelope) return false
        return relayHeader == other.relayHeader &&
            authenticatedHeader == other.authenticatedHeader &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = relayHeader.hashCode()
        result = 31 * result + authenticatedHeader.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Decrypted message envelope returned upon successful AES-GCM verification.
 */
data class DecryptedMeshEnvelope(
    val relayHeader: MutableRelayHeader,
    val authenticatedHeader: AuthenticatedEnvelopeHeader,
    val plaintext: ByteArray     // Plaintext payload (e.g., serialized SOS details)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecryptedMeshEnvelope) return false
        return relayHeader == other.relayHeader &&
            authenticatedHeader == other.authenticatedHeader &&
            plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int {
        var result = relayHeader.hashCode()
        result = 31 * result + authenticatedHeader.hashCode()
        result = 31 * result + plaintext.contentHashCode()
        return result
    }
}
