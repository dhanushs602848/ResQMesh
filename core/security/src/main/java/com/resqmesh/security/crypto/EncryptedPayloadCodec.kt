package com.resqmesh.security.crypto

import com.resqmesh.security.keys.ResponderKeyProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

enum class ResponderMessageType(val wireValue: Int) {
    SOS(1), NORMAL(2);

    companion object {
        fun fromWireValue(value: Int): ResponderMessageType = entries.firstOrNull { it.wireValue == value }
            ?: throw InvalidEncryptedPayloadException("Unsupported message type.")
    }
}

data class DecryptedPayload(val type: ResponderMessageType, val keyId: String, val plaintext: ByteArray)

class InvalidEncryptedPayloadException(message: String, cause: Throwable? = null) : GeneralSecurityException(message, cause)

/** Versioned binary envelope; the entire header is authenticated as AES-GCM additional data. */
class EncryptedPayloadCodec(
    private val keyProvider: ResponderKeyProvider,
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun encrypt(keyId: String, type: ResponderMessageType, plaintext: ByteArray): ByteArray {
        validateKeyId(keyId)
        val key = keyProvider.getResponderKey(keyId)
            ?: throw InvalidEncryptedPayloadException("No responder key is provisioned for this key ID.")
        requireAes256(key)
        val keyIdBytes = keyId.toByteArray(StandardCharsets.US_ASCII)
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val ciphertextLength = Math.addExact(plaintext.size, TAG_BYTES)
        val header = makeHeader(type, keyIdBytes, nonce, ciphertextLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return header + ciphertext
    }

    fun decrypt(packet: ByteArray): DecryptedPayload {
        val parsed = parseHeader(packet)
        val key = keyProvider.getResponderKey(parsed.keyId)
            ?: throw InvalidEncryptedPayloadException("No responder key is provisioned for this key ID.")
        requireAes256(key)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, parsed.nonce))
            cipher.updateAAD(packet.copyOfRange(0, parsed.headerLength))
            DecryptedPayload(parsed.type, parsed.keyId, cipher.doFinal(packet, parsed.headerLength, parsed.ciphertextLength))
        } catch (error: GeneralSecurityException) {
            throw InvalidEncryptedPayloadException("Encrypted payload authentication failed.", error)
        } finally {
            parsed.nonce.fill(0)
        }
    }

    private fun parseHeader(packet: ByteArray): ParsedHeader {
        if (packet.size < MIN_PACKET_BYTES) throw InvalidEncryptedPayloadException("Encrypted payload is truncated.")
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get() != MAGIC[0] || buffer.get() != MAGIC[1] || buffer.get() != MAGIC[2]) {
            throw InvalidEncryptedPayloadException("Encrypted payload magic is invalid.")
        }
        val version = buffer.get().toInt() and 0xff
        if (version != VERSION) throw InvalidEncryptedPayloadException("Unsupported encrypted payload version.")
        val type = ResponderMessageType.fromWireValue(buffer.get().toInt() and 0xff)
        val keyIdLength = buffer.get().toInt() and 0xff
        if (keyIdLength !in 1..MAX_KEY_ID_BYTES) throw InvalidEncryptedPayloadException("Encrypted payload key ID length is invalid.")
        val headerLength = FIXED_HEADER_BYTES + keyIdLength + NONCE_BYTES + LENGTH_BYTES
        if (packet.size < headerLength + TAG_BYTES) throw InvalidEncryptedPayloadException("Encrypted payload is truncated.")
        val keyIdBytes = ByteArray(keyIdLength).also { buffer.get(it) }
        if (keyIdBytes.any { (it.toInt() and 0xff) !in ALLOWED_KEY_ID_BYTES }) {
            throw InvalidEncryptedPayloadException("Encrypted payload key ID is malformed.")
        }
        val keyId = String(keyIdBytes, StandardCharsets.US_ASCII)
        val nonce = ByteArray(NONCE_BYTES).also { buffer.get(it) }
        val ciphertextLength = buffer.int
        if (ciphertextLength < TAG_BYTES || ciphertextLength != packet.size - headerLength) {
            throw InvalidEncryptedPayloadException("Encrypted payload length is invalid.")
        }
        return ParsedHeader(type, keyId, nonce, headerLength, ciphertextLength)
    }

    private fun makeHeader(type: ResponderMessageType, keyId: ByteArray, nonce: ByteArray, ciphertextLength: Int): ByteArray =
        ByteBuffer.allocate(FIXED_HEADER_BYTES + keyId.size + NONCE_BYTES + LENGTH_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(type.wireValue.toByte())
            .put(keyId.size.toByte())
            .put(keyId)
            .put(nonce)
            .putInt(ciphertextLength)
            .array()

    private fun validateKeyId(keyId: String) {
        if (!KEY_ID.matches(keyId)) throw IllegalArgumentException("keyId must contain 1–64 ASCII letters, digits, dots, underscores, or hyphens.")
    }

    private fun requireAes256(key: javax.crypto.SecretKey) {
        val encoded = key.encoded ?: throw GeneralSecurityException("Responder key material is not exportable for AES-256 validation.")
        try {
            if (encoded.size != AES_256_BYTES) throw GeneralSecurityException("Responder key must be AES-256.")
        } finally {
            encoded.fill(0)
        }
    }

    private data class ParsedHeader(
        val type: ResponderMessageType,
        val keyId: String,
        val nonce: ByteArray,
        val headerLength: Int,
        val ciphertextLength: Int
    )

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = 1
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val AES_256_BYTES = 32
        const val FIXED_HEADER_BYTES = 6
        const val LENGTH_BYTES = 4
        const val MAX_KEY_ID_BYTES = 64
        val MAGIC = byteArrayOf('R'.code.toByte(), 'Q'.code.toByte(), 'M'.code.toByte())
        val KEY_ID = Regex("[A-Za-z0-9._-]{1,64}")
        val ALLOWED_KEY_ID_BYTES = (('A'.code..'Z'.code) + ('a'.code..'z'.code) + ('0'.code..'9'.code) + listOf('.'.code, '_'.code, '-'.code)).toSet()
        const val MIN_PACKET_BYTES = FIXED_HEADER_BYTES + 1 + NONCE_BYTES + LENGTH_BYTES + TAG_BYTES
    }
}
