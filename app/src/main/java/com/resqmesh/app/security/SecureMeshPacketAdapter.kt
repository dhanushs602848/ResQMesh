package com.resqmesh.app.security

import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.security.crypto.InvalidEncryptedPayloadException
import com.resqmesh.security.envelope.AuthenticatedEnvelopeHeader
import com.resqmesh.security.envelope.DecryptedMeshEnvelope
import com.resqmesh.security.envelope.EncryptedMeshEnvelope
import com.resqmesh.security.envelope.MutableRelayHeader
import com.resqmesh.security.envelope.SecureMeshEnvelopeCodec
import com.resqmesh.security.keys.ResponderKeyProvider
import com.resqmesh.security.model.EnvelopeProcessingResult
import com.resqmesh.security.model.SecurityFailureReason
import com.resqmesh.security.relay.RelayAuthenticator
import com.resqmesh.security.replay.ReplayProtectionValidator
import com.resqmesh.security.replay.ReplayValidationResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Adapter boundary bridging domain [MeshPacket] instances and cryptographic [EncryptedMeshEnvelope] models.
 *
 * This boundary is intentionally isolated and NOT connected to live BLE transmission.
 * It establishes the translation contract for when live encryption is approved in a future phase.
 */
class SecureMeshPacketAdapter(
    private val envelopeCodec: SecureMeshEnvelopeCodec,
    private val keyProvider: ResponderKeyProvider,
    private val replayValidator: ReplayProtectionValidator? = null,
    private val relayAuthenticator: RelayAuthenticator? = null,
    private val requireRelayAuth: Boolean = false
) {

    /**
     * Seals a domain [MeshPacket] into an encrypted, authenticated [EncryptedMeshEnvelope].
     *
     * Sensitive fields (title, content, coordinates) are encrypted inside the AES-GCM payload.
     * Routing and identity metadata are bound into the Additional Authenticated Data (AAD).
     */
    fun packToEnvelope(packet: MeshPacket, keyId: String): EncryptedMeshEnvelope {
        val authHeader = AuthenticatedEnvelopeHeader(
            version = AuthenticatedEnvelopeHeader.CURRENT_VERSION,
            messageId = packet.messageId,
            originNodeId = packet.originNodeId,
            keyId = keyId,
            timestamp = packet.timestamp,
            priority = packet.priority.ordinal,
            payloadType = packet.packetType.code.toInt()
        )

        val relayHeader = MutableRelayHeader(
            senderNodeId = packet.senderNodeId,
            hopCount = packet.hopCount,
            maxHops = packet.maxHops
        )

        val plaintextBytes = serializeEmergencyContent(
            title = packet.title,
            content = packet.content,
            latitude = packet.latitude,
            longitude = packet.longitude
        )

        return envelopeCodec.seal(authHeader, relayHeader, plaintextBytes)
    }

    /**
     * Evaluates an [EncryptedMeshEnvelope].
     *
     * Enforces strict verification in sequence:
     * 1. Version compatibility check ([SecurityFailureReason.UNSUPPORTED_VERSION]).
     * 2. Hop count validity and overflow/underflow protection ([SecurityFailureReason.INVALID_HOP_COUNT]).
     * 3. Hop-by-hop relay authentication verification ([SecurityFailureReason.INVALID_RELAY_AUTHENTICATION]).
     * 4. Replay protection and freshness window check ([SecurityFailureReason.EXPIRED_MESSAGE] or [SecurityFailureReason.DUPLICATE_MESSAGE]).
     * 5. Key possession:
     *    - If [allowRelayWithoutKey] is true and key is absent, returns [EnvelopeProcessingResult.RelayedWithoutDecryption],
     *      allowing intermediate mesh nodes to forward the envelope without decrypting.
     *    - If [allowRelayWithoutKey] is false and key is absent, returns [EnvelopeProcessingResult.Rejected] with [SecurityFailureReason.UNKNOWN_KEY].
     * 6. Cryptographic AES-GCM AEAD decryption and tag verification ([SecurityFailureReason.INVALID_AUTHENTICATION_TAG]).
     */
    fun unpackEnvelope(
        envelope: EncryptedMeshEnvelope,
        allowRelayWithoutKey: Boolean = true
    ): EnvelopeProcessingResult {
        // 1. Envelope version compatibility check
        if (envelope.authenticatedHeader.version != AuthenticatedEnvelopeHeader.CURRENT_VERSION) {
            return EnvelopeProcessingResult.Rejected(
                SecurityFailureReason.UNSUPPORTED_VERSION,
                "Unsupported envelope version: ${envelope.authenticatedHeader.version} (expected ${AuthenticatedEnvelopeHeader.CURRENT_VERSION})."
            )
        }

        // 2. Hop count sanity and overflow validation
        val hopCount = envelope.relayHeader.hopCount
        val maxHops = envelope.relayHeader.maxHops
        if (hopCount < 0 || hopCount > maxHops || maxHops <= 0 || maxHops > MutableRelayHeader.MAX_HOPS_LIMIT) {
            return EnvelopeProcessingResult.Rejected(
                SecurityFailureReason.INVALID_HOP_COUNT,
                "Hop count $hopCount is invalid (allowable range: [0, $maxHops], max limit: ${MutableRelayHeader.MAX_HOPS_LIMIT})."
            )
        }

        // 3. Relay authentication verification
        if (relayAuthenticator != null) {
            val token = envelope.relayHeader.relayAuthToken
            if (token != null) {
                val isValid = relayAuthenticator.verifyRelayHop(
                    senderNodeId = envelope.relayHeader.senderNodeId,
                    messageId = envelope.authenticatedHeader.messageId,
                    hopCount = envelope.relayHeader.hopCount,
                    token = token
                )
                if (!isValid) {
                    return EnvelopeProcessingResult.Rejected(
                        SecurityFailureReason.INVALID_RELAY_AUTHENTICATION,
                        "Relay authentication failed for sender '${envelope.relayHeader.senderNodeId}' at hop $hopCount."
                    )
                }
            } else if (envelope.relayHeader.hopCount > 0 && requireRelayAuth) {
                return EnvelopeProcessingResult.Rejected(
                    SecurityFailureReason.INVALID_RELAY_AUTHENTICATION,
                    "Missing required relay authentication token for forwarded envelope at hop $hopCount."
                )
            }
        }

        // 4. Replay and freshness check
        if (replayValidator != null) {
            val validation = replayValidator.checkAndRecord(
                envelope.authenticatedHeader.messageId,
                envelope.authenticatedHeader.timestamp
            )
            when (validation) {
                ReplayValidationResult.VALID -> { /* Fresh message, continue processing */ }
                ReplayValidationResult.EXPIRED -> {
                    return EnvelopeProcessingResult.Rejected(
                        SecurityFailureReason.EXPIRED_MESSAGE,
                        "Envelope rejected: message timestamp is outside freshness window."
                    )
                }
                ReplayValidationResult.DUPLICATE -> {
                    return EnvelopeProcessingResult.Rejected(
                        SecurityFailureReason.DUPLICATE_MESSAGE,
                        "Envelope rejected: messageId has already been processed (replay detected)."
                    )
                }
                ReplayValidationResult.INVALID_FORMAT -> {
                    return EnvelopeProcessingResult.Rejected(
                        SecurityFailureReason.MALFORMED_ENVELOPE,
                        "Envelope rejected: invalid messageId format."
                    )
                }
            }
        }

        // 5. Check if group key is provisioned on this device
        val key = try {
            keyProvider.getResponderKey(envelope.authenticatedHeader.keyId)
        } catch (_: Exception) {
            null
        }

        if (key == null) {
            return if (allowRelayWithoutKey) {
                // Node does not possess the key -> eligible for relay forwarding without decrypting
                EnvelopeProcessingResult.RelayedWithoutDecryption(envelope)
            } else {
                EnvelopeProcessingResult.Rejected(
                    SecurityFailureReason.UNKNOWN_KEY,
                    "No responder key provisioned for keyId '${envelope.authenticatedHeader.keyId}'."
                )
            }
        }

        // 6. Attempt AEAD decryption
        return try {
            val decrypted = envelopeCodec.open(envelope)
            EnvelopeProcessingResult.Success(decrypted)
        } catch (e: InvalidEncryptedPayloadException) {
            EnvelopeProcessingResult.Rejected(
                SecurityFailureReason.INVALID_AUTHENTICATION_TAG,
                e.message ?: "Authentication tag verification failed."
            )
        } catch (e: Exception) {
            EnvelopeProcessingResult.Rejected(
                SecurityFailureReason.MALFORMED_ENVELOPE,
                e.message ?: "Malformed envelope."
            )
        }
    }

    /**
     * Explicitly decrypts an [EncryptedMeshEnvelope], rejecting with [SecurityFailureReason.UNKNOWN_KEY]
     * if the required group key is missing.
     */
    fun decryptEnvelope(envelope: EncryptedMeshEnvelope): EnvelopeProcessingResult {
        return unpackEnvelope(envelope, allowRelayWithoutKey = false)
    }

    /**
     * Deserializes decrypted plaintext payload back into a domain [MeshPacket].
     */
    fun parseDecryptedPacket(decrypted: DecryptedMeshEnvelope): MeshPacket {
        val (title, content, lat, lon) = deserializeEmergencyContent(decrypted.plaintext)
        val priorities = IncidentPriority.entries
        val priority = if (decrypted.authenticatedHeader.priority in priorities.indices) {
            priorities[decrypted.authenticatedHeader.priority]
        } else {
            IncidentPriority.HIGH
        }
        val packetType = MeshPacketType.fromCode(decrypted.authenticatedHeader.payloadType.toByte())

        return MeshPacket(
            messageId = decrypted.authenticatedHeader.messageId,
            originNodeId = decrypted.authenticatedHeader.originNodeId,
            senderNodeId = decrypted.relayHeader.senderNodeId,
            packetType = packetType,
            priority = priority,
            title = title,
            content = content,
            latitude = lat,
            longitude = lon,
            timestamp = decrypted.authenticatedHeader.timestamp,
            hopCount = decrypted.relayHeader.hopCount,
            maxHops = decrypted.relayHeader.maxHops
        )
    }

    /**
     * Performs a single relay hop forwarding operation on an [EncryptedMeshEnvelope] without decrypting.
     * Increments [hopCount], sets [senderNodeId] to [localNodeId], and computes a [relayAuthToken]
     * if a [relayAuthenticator] is configured.
     * Returns null if [maxHops] is reached or invalid.
     */
    fun relayForward(envelope: EncryptedMeshEnvelope, localNodeId: String): EncryptedMeshEnvelope? {
        val currentHop = envelope.relayHeader.hopCount
        val maxHops = envelope.relayHeader.maxHops
        if (currentHop >= maxHops || currentHop < 0 || maxHops <= 0 || maxHops > MutableRelayHeader.MAX_HOPS_LIMIT) {
            return null
        }
        val nextHop = currentHop + 1
        val relayAuthToken = relayAuthenticator?.signRelayHop(
            senderNodeId = localNodeId,
            messageId = envelope.authenticatedHeader.messageId,
            hopCount = nextHop
        )
        return envelope.copy(
            relayHeader = envelope.relayHeader.copy(
                senderNodeId = localNodeId,
                hopCount = nextHop,
                relayAuthToken = relayAuthToken
            )
        )
    }

    /**
     * Checks if a raw wire byte array represents an encrypted envelope ('RQE').
     */
    fun isEncryptedEnvelopeBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
            bytes[0] == SecureMeshEnvelopeCodec.MAGIC[0] &&
            bytes[1] == SecureMeshEnvelopeCodec.MAGIC[1] &&
            bytes[2] == SecureMeshEnvelopeCodec.MAGIC[2]
    }

    private fun serializeEmergencyContent(
        title: String,
        content: String,
        latitude: Double?,
        longitude: Double?
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            val titleBytes = title.toByteArray(StandardCharsets.UTF_8)
            dos.writeShort(titleBytes.size)
            dos.write(titleBytes)

            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            dos.writeInt(contentBytes.size)
            dos.write(contentBytes)

            if (latitude != null && longitude != null) {
                dos.writeByte(1)
                dos.writeDouble(latitude)
                dos.writeDouble(longitude)
            } else {
                dos.writeByte(0)
            }
        }
        return out.toByteArray()
    }

    private fun deserializeEmergencyContent(bytes: ByteArray): ParsedContent {
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        val titleLen = dis.readShort().toInt() and 0xFFFF
        val titleBytes = ByteArray(titleLen)
        dis.readFully(titleBytes)
        val title = String(titleBytes, StandardCharsets.UTF_8)

        val contentLen = dis.readInt()
        val contentBytes = ByteArray(contentLen)
        dis.readFully(contentBytes)
        val content = String(contentBytes, StandardCharsets.UTF_8)

        val hasLocation = dis.readByte().toInt() == 1
        val lat = if (hasLocation) dis.readDouble() else null
        val lon = if (hasLocation) dis.readDouble() else null

        return ParsedContent(title, content, lat, lon)
    }

    private data class ParsedContent(
        val title: String,
        val content: String,
        val latitude: Double?,
        val longitude: Double?
    )
}
