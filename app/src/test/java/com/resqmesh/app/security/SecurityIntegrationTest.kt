package com.resqmesh.app.security

import com.resqmesh.security.crypto.EncryptedPayloadCodec
import com.resqmesh.security.crypto.InvalidEncryptedPayloadException
import com.resqmesh.security.crypto.ResponderMessageType
import com.resqmesh.security.keys.KeyFingerprint
import com.resqmesh.security.keys.ResponderKeyProvider
import com.resqmesh.security.replay.InMemoryReplayProtectionValidator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Verification of the core:security library integration within the ResQMesh application.
 * Tests payload codec, authentication boundaries, fingerprinting, and replay compatibility.
 */
class SecurityIntegrationTest {

    private val testKeyId = "responder-alpha-1"
    private val testKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val testSecretKey = SecretKeySpec(testKeyBytes, "AES")

    private val mockKeyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? {
            return if (keyId == testKeyId) testSecretKey else null
        }
    }

    private val codec = EncryptedPayloadCodec(mockKeyProvider)
    private val replayValidator = InMemoryReplayProtectionValidator()

    @Test
    fun `encrypt and decrypt emergency SOS payload succeeds with valid responder key`() {
        val sosPlaintext = "CRITICAL: Patient needs extraction at coordinates 12.9716, 77.5946".toByteArray(Charsets.UTF_8)

        val encryptedPacket = codec.encrypt(testKeyId, ResponderMessageType.SOS, sosPlaintext)
        val decrypted = codec.decrypt(encryptedPacket)

        assertEquals(ResponderMessageType.SOS, decrypted.type)
        assertEquals(testKeyId, decrypted.keyId)
        assertArrayEquals(sosPlaintext, decrypted.plaintext)
        assertEquals(
            "CRITICAL: Patient needs extraction at coordinates 12.9716, 77.5946",
            String(decrypted.plaintext, Charsets.UTF_8)
        )
    }

    @Test
    fun `decrypt with unknown keyId fails with InvalidEncryptedPayloadException`() {
        val payload = "Medical triage info".toByteArray(Charsets.UTF_8)
        val encryptedPacket = codec.encrypt(testKeyId, ResponderMessageType.NORMAL, payload)

        // Codec with empty provider that has no provisioned key
        val unprovisionedCodec = EncryptedPayloadCodec(object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? = null
        })

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            unprovisionedCodec.decrypt(encryptedPacket)
        }
    }

    @Test
    fun `tampered ciphertext fails AES-GCM authentication tag check`() {
        val payload = "Safe evacuation route confirmed".toByteArray(Charsets.UTF_8)
        val packet = codec.encrypt(testKeyId, ResponderMessageType.NORMAL, payload)

        // Flip bit in authentication tag / ciphertext payload
        packet[packet.lastIndex] = (packet[packet.lastIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decrypt(packet)
        }
    }

    @Test
    fun `tampered header metadata fails AAD authentication`() {
        val payload = "Emergency SOS".toByteArray(Charsets.UTF_8)
        val packet = codec.encrypt(testKeyId, ResponderMessageType.SOS, payload)

        // Tamper with wire message type (byte index 4 is type: SOS=1 -> NORMAL=2)
        packet[4] = ResponderMessageType.NORMAL.wireValue.toByte()

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decrypt(packet)
        }
    }

    @Test
    fun `KeyFingerprint sha256 produces valid 64-char hex fingerprint`() {
        val key = ByteArray(32) { 0x42.toByte() }
        val fingerprint = KeyFingerprint.sha256(key)

        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.matches(Regex("^[0-9a-f]{64}$")))

        // Different keys must produce different fingerprints
        val differentKey = ByteArray(32) { 0x43.toByte() }
        val differentFingerprint = KeyFingerprint.sha256(differentKey)
        assertNotEquals(fingerprint, differentFingerprint)
    }

    @Test
    fun `replay validator accepts UUID v4 but demonstrates incompatibility with legacy SOS IDs`() {
        val validUuidV4 = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Valid UUID v4 is accepted on first observation
        assertTrue(replayValidator.validateAndRecord(validUuidV4, now))
        // Duplicate is rejected
        assertFalse(replayValidator.validateAndRecord(validUuidV4, now))

        // Documented compatibility limitation: existing ResQMesh short IDs are rejected by the v4 validator
        val legacySosId = "SOS-4721"
        assertFalse(
            "Legacy non-UUID SOS ID is rejected by replay validator - requires UUID migration in a future phase",
            replayValidator.validateAndRecord(legacySosId, now)
        )

        val legacyAckId = "ACK-SOS-4721-RQM-A1B2C3"
        assertFalse(
            "Legacy non-UUID ACK ID is rejected by replay validator",
            replayValidator.validateAndRecord(legacyAckId, now)
        )
    }

    @Test
    fun `provisioned key through ResponderKeyProvisioner encrypts and decrypts payload`() {
        val storage = mutableMapOf<String, ByteArray>()
        val provider = object : ResponderKeyProvider {
            override fun provisionResponderKey(keyId: String, keyMaterial: ByteArray) {
                storage[keyId] = keyMaterial.clone()
            }
            override fun getResponderKey(keyId: String): SecretKey? {
                val b = storage[keyId] ?: return null
                return SecretKeySpec(b, "AES")
            }
            override fun hasResponderKey(keyId: String): Boolean = storage.containsKey(keyId)
            override fun getProvisionedKeyIds(): Set<String> = storage.keys.toSet()
        }

        val provisioner: com.resqmesh.security.keys.ResponderKeyProvisioner =
            com.resqmesh.security.keys.DefaultResponderKeyProvisioner(provider)
        val dynamicCodec = EncryptedPayloadCodec(provider)

        val freshKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val provResult = provisioner.provisionKey("field-rescue-team-1", freshKey, clearSourceBytes = false)
        assertTrue(provResult.isSuccess)

        val expectedFingerprint = KeyFingerprint.sha256(freshKey)
        assertEquals(expectedFingerprint, provResult.getOrThrow().fingerprint)

        val plaintext = "Field Team 1 responding to landslide evacuation".toByteArray(Charsets.UTF_8)
        val encrypted = dynamicCodec.encrypt("field-rescue-team-1", ResponderMessageType.SOS, plaintext)
        val decrypted = dynamicCodec.decrypt(encrypted)

        assertEquals("field-rescue-team-1", decrypted.keyId)
        assertArrayEquals(plaintext, decrypted.plaintext)
    }
}
