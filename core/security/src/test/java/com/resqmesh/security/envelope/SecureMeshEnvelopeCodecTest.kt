package com.resqmesh.security.envelope

import com.resqmesh.security.crypto.InvalidEncryptedPayloadException
import com.resqmesh.security.keys.ResponderKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SecureMeshEnvelopeCodecTest {

    private val testKeyId = "responder-alpha-key"
    private val testKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val testSecretKey = SecretKeySpec(testKeyBytes, "AES")

    private val otherKeyId = "responder-beta-key"
    private val otherKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val otherSecretKey = SecretKeySpec(otherKeyBytes, "AES")

    private val keyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? {
            return when (keyId) {
                testKeyId -> testSecretKey
                otherKeyId -> otherSecretKey
                else -> null
            }
        }
    }

    private lateinit var codec: SecureMeshEnvelopeCodec

    @Before
    fun setUp() {
        codec = SecureMeshEnvelopeCodec(keyProvider)
    }

    private fun sampleAuthHeader(
        version: Int = AuthenticatedEnvelopeHeader.CURRENT_VERSION,
        messageId: String = UUID.randomUUID().toString(),
        originNodeId: String = "RQM-A1B2",
        keyId: String = testKeyId,
        timestamp: Long = System.currentTimeMillis(),
        priority: Int = 4, // CRITICAL
        payloadType: Int = 1 // SOS
    ) = AuthenticatedEnvelopeHeader(
        version = version,
        messageId = messageId,
        originNodeId = originNodeId,
        keyId = keyId,
        timestamp = timestamp,
        priority = priority,
        payloadType = payloadType
    )

    private fun sampleRelayHeader(
        senderNodeId: String = "RQM-A1B2",
        hopCount: Int = 0,
        maxHops: Int = 7
    ) = MutableRelayHeader(
        senderNodeId = senderNodeId,
        hopCount = hopCount,
        maxHops = maxHops
    )

    @Test
    fun `successful encryption and decryption round trip`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val plaintext = "EMERGENCY: Flood evacuation needed at sector 4".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)
        val decrypted = codec.open(envelope)

        assertEquals(authHeader, decrypted.authenticatedHeader)
        assertEquals(relayHeader, decrypted.relayHeader)
        assertArrayEquals(plaintext, decrypted.plaintext)
    }

    @Test
    fun `wrong key fails decryption with InvalidEncryptedPayloadException`() {
        val authHeader = sampleAuthHeader(keyId = testKeyId)
        val relayHeader = sampleRelayHeader()
        val plaintext = "Medical assistance required".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)

        // Attempt decrypting with a codec that only has otherKeyId or different key
        val unprovisionedCodec = SecureMeshEnvelopeCodec(object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? = null
        })

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            unprovisionedCodec.open(envelope)
        }

        // Attempt decrypting with a provider returning the wrong secret key for testKeyId
        val wrongKeyCodec = SecureMeshEnvelopeCodec(object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? = otherSecretKey
        })

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            wrongKeyCodec.open(envelope)
        }
    }

    @Test
    fun `ciphertext tampering is detected and rejected`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val plaintext = "Patient triage coordinates".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)

        // Flip a bit in the ciphertext / GCM auth tag
        val tamperedCiphertext = envelope.ciphertext.copyOf()
        tamperedCiphertext[tamperedCiphertext.lastIndex] = (tamperedCiphertext[tamperedCiphertext.lastIndex].toInt() xor 0x01).toByte()

        val tamperedEnvelope = envelope.copy(ciphertext = tamperedCiphertext)

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(tamperedEnvelope)
        }
    }

    @Test
    fun `tampering with immutable authenticated metadata causes AAD failure`() {
        val authHeader = sampleAuthHeader(originNodeId = "RQM-A1B2", priority = 4)
        val relayHeader = sampleRelayHeader()
        val plaintext = "SOS: Building collapse".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)

        // 1. Rogue node modifies originNodeId to impersonate another node
        val tamperedOrigin = envelope.copy(
            authenticatedHeader = envelope.authenticatedHeader.copy(originNodeId = "RQM-FAKE")
        )
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(tamperedOrigin)
        }

        // 2. Rogue node modifies priority (e.g. downgrades CRITICAL 4 to LOW 1)
        val tamperedPriority = envelope.copy(
            authenticatedHeader = envelope.authenticatedHeader.copy(priority = 1)
        )
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(tamperedPriority)
        }

        // 3. Rogue node modifies messageId
        val tamperedMessageId = envelope.copy(
            authenticatedHeader = envelope.authenticatedHeader.copy(messageId = UUID.randomUUID().toString())
        )
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(tamperedMessageId)
        }
    }

    @Test
    fun `relay metadata is mutable and does not invalidate end-to-end AAD`() {
        val authHeader = sampleAuthHeader(originNodeId = "RQM-ORIGIN")
        val originRelayHeader = sampleRelayHeader(senderNodeId = "RQM-ORIGIN", hopCount = 0, maxHops = 7)
        val plaintext = "SOS beacon".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, originRelayHeader, plaintext)

        // Intermediate relay node hops: hop 1
        val hop1Envelope = envelope.copy(
            relayHeader = MutableRelayHeader(senderNodeId = "RQM-RELAY-1", hopCount = 1, maxHops = 7)
        )
        val decryptedHop1 = codec.open(hop1Envelope)
        assertEquals("RQM-RELAY-1", decryptedHop1.relayHeader.senderNodeId)
        assertEquals(1, decryptedHop1.relayHeader.hopCount)
        assertArrayEquals(plaintext, decryptedHop1.plaintext)

        // Intermediate relay node hops: hop 2
        val hop2Envelope = envelope.copy(
            relayHeader = MutableRelayHeader(senderNodeId = "RQM-RELAY-2", hopCount = 2, maxHops = 7)
        )
        val decryptedHop2 = codec.open(hop2Envelope)
        assertEquals("RQM-RELAY-2", decryptedHop2.relayHeader.senderNodeId)
        assertEquals(2, decryptedHop2.relayHeader.hopCount)
        assertArrayEquals(plaintext, decryptedHop2.plaintext)
    }

    @Test
    fun `invalid or unsupported envelope version is rejected`() {
        val badVersionHeader = sampleAuthHeader(version = 99)
        val relayHeader = sampleRelayHeader()

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.seal(badVersionHeader, relayHeader, "data".toByteArray())
        }
    }

    @Test
    fun `malformed and truncated envelopes are rejected`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val envelope = codec.seal(authHeader, relayHeader, "payload".toByteArray())

        // Nonce length != 12
        val badNonce = envelope.copy(nonce = ByteArray(11))
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(badNonce)
        }

        // Ciphertext shorter than tag length (16 bytes)
        val shortCiphertext = envelope.copy(ciphertext = ByteArray(15))
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.open(shortCiphertext)
        }
    }

    @Test
    fun `empty payload is handled safely`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val emptyPlaintext = ByteArray(0)

        val envelope = codec.seal(authHeader, relayHeader, emptyPlaintext)
        val decrypted = codec.open(envelope)

        assertEquals(0, decrypted.plaintext.size)
        assertArrayEquals(emptyPlaintext, decrypted.plaintext)
    }

    @Test
    fun `oversized payload exceeds limit and is rejected`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val oversizedPlaintext = ByteArray(SecureMeshEnvelopeCodec.MAX_PLAINTEXT_BYTES + 1)

        assertThrows(IllegalArgumentException::class.java) {
            codec.seal(authHeader, relayHeader, oversizedPlaintext)
        }
    }

    @Test
    fun `wire serialization encode and decode round trip`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader(senderNodeId = "RQM-NODE-1", hopCount = 2, maxHops = 5)
        val plaintext = "Wire format test payload".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)
        val wireBytes = codec.encode(envelope)

        val decodedEnvelope = codec.decode(wireBytes)
        assertEquals(envelope, decodedEnvelope)

        val decrypted = codec.open(decodedEnvelope)
        assertArrayEquals(plaintext, decrypted.plaintext)
    }

    @Test
    fun `wire decoding rejects truncated or corrupt magic data`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val envelope = codec.seal(authHeader, relayHeader, "data".toByteArray())
        val wireBytes = codec.encode(envelope)

        // Truncated wire bytes
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decode(wireBytes.copyOf(wireBytes.size - 5))
        }

        // Corrupt magic header
        val corruptMagic = wireBytes.copyOf()
        corruptMagic[0] = 0x00
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decode(corruptMagic)
        }
    }

    @Test
    fun `fresh nonces generated for each encryption`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = sampleRelayHeader()
        val plaintext = "Identical message".toByteArray(Charsets.UTF_8)

        val env1 = codec.seal(authHeader, relayHeader, plaintext)
        val env2 = codec.seal(authHeader, relayHeader, plaintext)

        assertFalse("Nonces must be distinct across calls", env1.nonce.contentEquals(env2.nonce))
        assertFalse("Ciphertexts must be distinct due to distinct IVs", env1.ciphertext.contentEquals(env2.ciphertext))
    }

    @Test
    fun `wire serialization preserves optional relayAuthToken`() {
        val authHeader = sampleAuthHeader()
        val relayHeader = MutableRelayHeader(
            senderNodeId = "RQM-RELAY-1",
            hopCount = 3,
            maxHops = 7,
            relayAuthToken = "a1b2c3d4e5f67890abcdef1234567890"
        )
        val plaintext = "Authenticated hop payload".toByteArray(Charsets.UTF_8)

        val envelope = codec.seal(authHeader, relayHeader, plaintext)
        val wireBytes = codec.encode(envelope)

        val decodedEnvelope = codec.decode(wireBytes)
        assertEquals("a1b2c3d4e5f67890abcdef1234567890", decodedEnvelope.relayHeader.relayAuthToken)
        assertEquals(envelope, decodedEnvelope)

        val decrypted = codec.open(decodedEnvelope)
        assertArrayEquals(plaintext, decrypted.plaintext)
    }
}
