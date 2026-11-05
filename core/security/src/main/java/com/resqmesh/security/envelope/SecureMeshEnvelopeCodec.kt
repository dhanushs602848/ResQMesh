package com.resqmesh.security.envelope

import com.resqmesh.security.crypto.InvalidEncryptedPayloadException
import com.resqmesh.security.keys.ResponderKeyProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Codec responsible for sealing (encrypting & authenticating) and opening (decrypting & verifying)
 * [EncryptedMeshEnvelope] instances, as well as binary wire serialization.
 *
 * Implements strict security invariants:
 * - AES-256-GCM AEAD encryption with a fresh 12-byte cryptographically secure random nonce per message.
 * - End-to-end immutable metadata authenticated as Additional Authenticated Data (AAD).
 * - Mutable relay fields kept strictly separate from the AAD.
 * - Rejection of tampered ciphertext, mismatched keys, or altered authenticated headers.
 * - Wire bounds checking to prevent denial-of-service / buffer overflow attacks.
 */
class SecureMeshEnvelopeCodec(
    private val keyProvider: ResponderKeyProvider,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    /**
     * Seals [plaintext] into an [EncryptedMeshEnvelope] using the group key provisioned for [authenticatedHeader.keyId].
     *
     * @param authenticatedHeader End-to-end immutable header to be cryptographically authenticated via AAD.
     * @param relayHeader Mutable hop-by-hop relay header (not included in AAD).
     * @param plaintext Raw payload bytes to be encrypted.
     * @return [EncryptedMeshEnvelope] holding the authenticated ciphertext.
     * @throws InvalidEncryptedPayloadException if key is missing or encryption fails.
     */
    fun seal(
        authenticatedHeader: AuthenticatedEnvelopeHeader,
        relayHeader: MutableRelayHeader,
        plaintext: ByteArray
    ): EncryptedMeshEnvelope {
        validateAuthenticatedHeader(authenticatedHeader)
        validateRelayHeader(relayHeader)

        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            throw IllegalArgumentException("Plaintext exceeds maximum permitted size of $MAX_PLAINTEXT_BYTES bytes.")
        }

        val key = keyProvider.getResponderKey(authenticatedHeader.keyId)
            ?: throw InvalidEncryptedPayloadException("No responder key provisioned for keyId '${authenticatedHeader.keyId}'.")
        requireAes256(key)

        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val aad = buildAad(authenticatedHeader)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedMeshEnvelope(
                relayHeader = relayHeader,
                authenticatedHeader = authenticatedHeader,
                nonce = nonce,
                ciphertext = ciphertext
            )
        } catch (e: GeneralSecurityException) {
            throw InvalidEncryptedPayloadException("Failed to seal mesh envelope: ${e.message}", e)
        }
    }

    /**
     * Opens an [EncryptedMeshEnvelope], validating AEAD integrity against [authenticatedHeader] AAD.
     *
     * @param envelope The envelope to decrypt.
     * @return [DecryptedMeshEnvelope] holding the verified plaintext and original headers.
     * @throws InvalidEncryptedPayloadException if authentication fails, key is missing, or data is corrupted.
     */
    fun open(envelope: EncryptedMeshEnvelope): DecryptedMeshEnvelope {
        validateAuthenticatedHeader(envelope.authenticatedHeader)
        validateRelayHeader(envelope.relayHeader)

        if (envelope.nonce.size != NONCE_BYTES) {
            throw InvalidEncryptedPayloadException("Invalid nonce length: ${envelope.nonce.size} (expected $NONCE_BYTES).")
        }
        if (envelope.ciphertext.size < TAG_BYTES) {
            throw InvalidEncryptedPayloadException("Ciphertext is truncated; must include at least $TAG_BYTES bytes for the AEAD tag.")
        }
        if (envelope.ciphertext.size > MAX_CIPHERTEXT_BYTES) {
            throw InvalidEncryptedPayloadException("Ciphertext exceeds maximum allowed length of $MAX_CIPHERTEXT_BYTES bytes.")
        }

        val key = keyProvider.getResponderKey(envelope.authenticatedHeader.keyId)
            ?: throw InvalidEncryptedPayloadException("No responder key provisioned for keyId '${envelope.authenticatedHeader.keyId}'.")
        requireAes256(key)

        val aad = buildAad(envelope.authenticatedHeader)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, envelope.nonce))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(envelope.ciphertext)
            DecryptedMeshEnvelope(
                relayHeader = envelope.relayHeader,
                authenticatedHeader = envelope.authenticatedHeader,
                plaintext = plaintext
            )
        } catch (e: GeneralSecurityException) {
            throw InvalidEncryptedPayloadException("Envelope authentication failed: ciphertext or authenticated metadata was tampered with.", e)
        }
    }

    /**
     * Deterministically serializes [AuthenticatedEnvelopeHeader] into binary AAD bytes.
     */
    fun buildAad(header: AuthenticatedEnvelopeHeader): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeByte(header.version)
            writeString(dos, header.messageId)
            writeString(dos, header.originNodeId)
            writeString(dos, header.keyId)
            dos.writeLong(header.timestamp)
            dos.writeByte(header.priority)
            dos.writeByte(header.payloadType)
        }
        return out.toByteArray()
    }

    /**
     * Serializes an [EncryptedMeshEnvelope] into compact binary wire representation.
     */
    fun encode(envelope: EncryptedMeshEnvelope): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            // Magic bytes
            dos.write(MAGIC)
            dos.writeByte(envelope.authenticatedHeader.version)

            // Mutable relay block
            writeString(dos, envelope.relayHeader.senderNodeId)
            dos.writeShort(envelope.relayHeader.hopCount)
            dos.writeShort(envelope.relayHeader.maxHops)

            // Authenticated metadata block
            writeString(dos, envelope.authenticatedHeader.messageId)
            writeString(dos, envelope.authenticatedHeader.originNodeId)
            writeString(dos, envelope.authenticatedHeader.keyId)
            dos.writeLong(envelope.authenticatedHeader.timestamp)
            dos.writeByte(envelope.authenticatedHeader.priority)
            dos.writeByte(envelope.authenticatedHeader.payloadType)

            // Crypto payload block
            dos.write(envelope.nonce)
            dos.writeInt(envelope.ciphertext.size)
            dos.write(envelope.ciphertext)

            // Optional trailing mutable relay auth token
            if (envelope.relayHeader.relayAuthToken != null) {
                writeString(dos, envelope.relayHeader.relayAuthToken)
            }
        }
        return out.toByteArray()
    }

    /**
     * Deserializes binary wire representation into an [EncryptedMeshEnvelope].
     * Enforces strict validation of magic, version, bounds, and string formats.
     */
    fun decode(bytes: ByteArray): EncryptedMeshEnvelope {
        if (bytes.size < MIN_WIRE_BYTES) {
            throw InvalidEncryptedPayloadException("Envelope wire data is truncated (size: ${bytes.size} bytes).")
        }
        if (bytes.size > MAX_TOTAL_WIRE_BYTES) {
            throw InvalidEncryptedPayloadException("Envelope wire data exceeds maximum allowed size.")
        }

        val bais = ByteArrayInputStream(bytes)
        val dis = DataInputStream(bais)

        val magic = ByteArray(MAGIC.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw InvalidEncryptedPayloadException("Invalid envelope magic header.")
        }

        val version = dis.readByte().toInt() and 0xFF
        if (version != AuthenticatedEnvelopeHeader.CURRENT_VERSION) {
            throw InvalidEncryptedPayloadException("Unsupported envelope version: $version (expected ${AuthenticatedEnvelopeHeader.CURRENT_VERSION}).")
        }

        // Mutable relay block
        val senderNodeId = readString(dis, MutableRelayHeader.MAX_ID_LENGTH)
        val hopCount = dis.readShort().toInt() and 0xFFFF
        val maxHops = dis.readShort().toInt() and 0xFFFF
        val relayHeader = MutableRelayHeader(senderNodeId, hopCount, maxHops)

        // Authenticated metadata block
        val messageId = readString(dis, AuthenticatedEnvelopeHeader.MAX_ID_LENGTH)
        val originNodeId = readString(dis, AuthenticatedEnvelopeHeader.MAX_ID_LENGTH)
        val keyId = readString(dis, AuthenticatedEnvelopeHeader.MAX_ID_LENGTH)
        val timestamp = dis.readLong()
        val priority = dis.readByte().toInt() and 0xFF
        val payloadType = dis.readByte().toInt() and 0xFF
        val authenticatedHeader = AuthenticatedEnvelopeHeader(
            version = version,
            messageId = messageId,
            originNodeId = originNodeId,
            keyId = keyId,
            timestamp = timestamp,
            priority = priority,
            payloadType = payloadType
        )

        // Crypto payload block
        val nonce = ByteArray(NONCE_BYTES)
        dis.readFully(nonce)

        val ciphertextLength = dis.readInt()
        if (ciphertextLength < TAG_BYTES || ciphertextLength > MAX_CIPHERTEXT_BYTES) {
            throw InvalidEncryptedPayloadException("Invalid ciphertext length in envelope: $ciphertextLength.")
        }
        if (dis.available() < ciphertextLength) {
            throw InvalidEncryptedPayloadException("Envelope truncated: expected $ciphertextLength ciphertext bytes, but only ${dis.available()} remain.")
        }

        val ciphertext = ByteArray(ciphertextLength)
        dis.readFully(ciphertext)

        val relayAuthToken = if (dis.available() > 0) {
            readString(dis, MutableRelayHeader.MAX_TOKEN_LENGTH)
        } else {
            null
        }

        return EncryptedMeshEnvelope(
            relayHeader = relayHeader.copy(relayAuthToken = relayAuthToken),
            authenticatedHeader = authenticatedHeader,
            nonce = nonce,
            ciphertext = ciphertext
        )
    }

    private fun validateAuthenticatedHeader(header: AuthenticatedEnvelopeHeader) {
        if (header.version != AuthenticatedEnvelopeHeader.CURRENT_VERSION) {
            throw InvalidEncryptedPayloadException("Unsupported envelope version: ${header.version}.")
        }
        if (header.messageId.isBlank() || header.messageId.length > AuthenticatedEnvelopeHeader.MAX_ID_LENGTH) {
            throw InvalidEncryptedPayloadException("Invalid messageId in envelope header.")
        }
        if (header.originNodeId.isBlank() || header.originNodeId.length > AuthenticatedEnvelopeHeader.MAX_ID_LENGTH) {
            throw InvalidEncryptedPayloadException("Invalid originNodeId in envelope header.")
        }
        if (!KEY_ID_REGEX.matches(header.keyId)) {
            throw InvalidEncryptedPayloadException("Invalid keyId in envelope header: '${header.keyId}'.")
        }
        if (header.timestamp <= 0L) {
            throw InvalidEncryptedPayloadException("Invalid timestamp in envelope header: ${header.timestamp}.")
        }
    }

    private fun validateRelayHeader(header: MutableRelayHeader) {
        if (header.senderNodeId.isBlank() || header.senderNodeId.length > MutableRelayHeader.MAX_ID_LENGTH) {
            throw InvalidEncryptedPayloadException("Invalid senderNodeId in relay header.")
        }
        if (header.hopCount < 0) {
            throw InvalidEncryptedPayloadException("hopCount cannot be negative.")
        }
        if (header.maxHops <= 0 || header.maxHops > MutableRelayHeader.MAX_HOPS_LIMIT) {
            throw InvalidEncryptedPayloadException("maxHops must be between 1 and ${MutableRelayHeader.MAX_HOPS_LIMIT}.")
        }
        if (header.relayAuthToken != null && header.relayAuthToken.length > MutableRelayHeader.MAX_TOKEN_LENGTH) {
            throw InvalidEncryptedPayloadException("relayAuthToken exceeds maximum length.")
        }
    }

    private fun requireAes256(key: SecretKey) {
        val encoded = key.encoded ?: throw GeneralSecurityException("Key material is not exportable for size validation.")
        try {
            if (encoded.size != AES_256_BYTES) {
                throw GeneralSecurityException("Responder key must be AES-256 (32 bytes), found ${encoded.size} bytes.")
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun writeString(dos: DataOutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > 255) {
            throw IllegalArgumentException("String length exceeds 255 bytes limit.")
        }
        dos.writeByte(bytes.size)
        dos.write(bytes)
    }

    private fun readString(dis: DataInputStream, maxLength: Int): String {
        val length = dis.readByte().toInt() and 0xFF
        if (length > maxLength) {
            throw InvalidEncryptedPayloadException("String length $length exceeds maximum allowed $maxLength.")
        }
        val bytes = ByteArray(length)
        dis.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val AES_256_BYTES = 32

        const val MAX_PLAINTEXT_BYTES = 64 * 1024 // 64 KB
        const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + TAG_BYTES
        const val MIN_WIRE_BYTES = 3 + 1 + 1 + 1 + 2 + 2 + 1 + 1 + 1 + 1 + 8 + 1 + 1 + NONCE_BYTES + 4 + TAG_BYTES
        const val MAX_TOTAL_WIRE_BYTES = MAX_CIPHERTEXT_BYTES + 1024

        val MAGIC = byteArrayOf('R'.code.toByte(), 'Q'.code.toByte(), 'E'.code.toByte())
        val KEY_ID_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
