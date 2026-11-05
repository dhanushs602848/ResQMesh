package com.resqmesh.security.keys

import com.resqmesh.security.model.KeyProvisioningResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class ResponderKeyProvisionerTest {

    private lateinit var fakeKeyProvider: FakeResponderKeyProvider
    private lateinit var provisioner: ResponderKeyProvisioner

    @Before
    fun setUp() {
        fakeKeyProvider = FakeResponderKeyProvider()
        provisioner = DefaultResponderKeyProvisioner(fakeKeyProvider)
    }

    @Test
    fun `valid 32-byte AES key is accepted and provisioned successfully`() {
        val keyBytes = ByteArray(32) { 0x11.toByte() }
        val expectedFingerprint = KeyFingerprint.sha256(keyBytes)

        val result = provisioner.provisionKey("group-alpha-1", keyBytes, clearSourceBytes = false)

        assertTrue("Provisioning must succeed for valid 32-byte key", result.isSuccess)
        val info: KeyProvisioningResult = result.getOrThrow()
        assertEquals("group-alpha-1", info.keyId)
        assertEquals(expectedFingerprint, info.fingerprint)

        assertTrue(provisioner.isKeyProvisioned("group-alpha-1"))
        assertEquals(expectedFingerprint, provisioner.getKeyFingerprint("group-alpha-1"))
    }

    @Test
    fun `invalid key lengths are rejected with IllegalArgumentException`() {
        val invalidSizes = listOf(0, 8, 16, 24, 31, 33, 64)

        for (size in invalidSizes) {
            val keyBytes = ByteArray(size) { 0x0A.toByte() }
            val result = provisioner.provisionKey("test-key", keyBytes)

            assertTrue(
                "Key size $size must be rejected",
                result.isFailure
            )
            assertTrue(
                "Exception must be IllegalArgumentException",
                result.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun `invalid key identifiers are rejected`() {
        val invalidKeyIds = listOf(
            "",
            "   ",
            "group with space",
            "group/slash",
            "group@symbol",
            "group#hash",
            "a".repeat(65) // Exceeds 64 chars
        )

        for (invalidId in invalidKeyIds) {
            val keyBytes = ByteArray(32) { 0x22.toByte() }
            val result = provisioner.provisionKey(invalidId, keyBytes)

            assertTrue(
                "KeyId '$invalidId' must be rejected",
                result.isFailure
            )
            assertTrue(
                "Exception must be IllegalArgumentException",
                result.exceptionOrNull() is IllegalArgumentException
            )
        }
    }

    @Test
    fun `valid key identifiers with dots dashes and underscores are accepted`() {
        val validKeyIds = listOf(
            "group-1",
            "group_2",
            "group.3",
            "RQM-ALPHA.01_prod"
        )

        for (validId in validKeyIds) {
            val keyBytes = ByteArray(32) { 0x33.toByte() }
            val result = provisioner.provisionKey(validId, keyBytes)
            assertTrue("KeyId '$validId' must be accepted", result.isSuccess)
        }
    }

    @Test
    fun `duplicate provisioning overwrites existing key cleanly`() {
        val key1 = ByteArray(32) { 0x01.toByte() }
        val fp1 = KeyFingerprint.sha256(key1)
        provisioner.provisionKey("active-group", key1)

        assertEquals(fp1, provisioner.getKeyFingerprint("active-group"))

        // Provision updated key under same keyId
        val key2 = ByteArray(32) { 0x02.toByte() }
        val fp2 = KeyFingerprint.sha256(key2)
        val updateResult = provisioner.provisionKey("active-group", key2)

        assertTrue(updateResult.isSuccess)
        assertEquals(fp2, updateResult.getOrThrow().fingerprint)
        assertEquals(fp2, provisioner.getKeyFingerprint("active-group"))
    }

    @Test
    fun `storage errors in key provider return Result failure`() {
        val failingProvider = object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? = null
            override fun provisionResponderKey(keyId: String, keyMaterial: ByteArray) {
                throw GeneralSecurityException("Hardware KeyStore communication failure")
            }
        }
        val failingProvisioner = DefaultResponderKeyProvisioner(failingProvider)

        val keyBytes = ByteArray(32) { 0x77.toByte() }
        val result = failingProvisioner.provisionKey("fail-test", keyBytes)

        assertTrue("Storage error must result in failure", result.isFailure)
        assertTrue(
            "Exception must be GeneralSecurityException",
            result.exceptionOrNull() is GeneralSecurityException
        )
    }

    @Test
    fun `source key array is zeroized in memory when clearSourceBytes is true`() {
        val keyBytes = ByteArray(32) { 0x99.toByte() }

        val result = provisioner.provisionKey("zero-test", keyBytes, clearSourceBytes = true)
        assertTrue(result.isSuccess)

        // Verify all bytes in caller's source array are wiped with 0
        assertTrue(
            "Source key array must be zeroized in memory",
            keyBytes.all { it == 0.toByte() }
        )
    }

    @Test
    fun `source key array is preserved when clearSourceBytes is false`() {
        val keyBytes = ByteArray(32) { 0x88.toByte() }

        val result = provisioner.provisionKey("preserve-test", keyBytes, clearSourceBytes = false)
        assertTrue(result.isSuccess)

        assertTrue(
            "Source key array must be preserved",
            keyBytes.all { it == 0x88.toByte() }
        )
    }

    @Test
    fun `no secret key material exposed in metadata or UI state`() {
        val keyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val rawHex = keyBytes.joinToString("") { "%02x".format(it) }

        provisioner.provisionKey("secure-group", keyBytes, clearSourceBytes = false)

        val metadata = provisioner.getKeyMetadata("secure-group")
        assertNotNull(metadata)
        assertFalse(
            "Metadata must not contain raw key hex",
            metadata!!.toString().contains(rawHex)
        )

        val state = provisioner.getProvisioningState("secure-group")
        assertTrue(state.isKeyProvisioned)
        assertEquals("ACTIVE", state.statusMessage)
        assertFalse(
            "UI state must not contain raw key hex",
            state.toString().contains(rawHex)
        )
    }

    @Test
    fun `removeKey deletes key and resets provisioning state`() {
        val keyBytes = ByteArray(32) { 0x44.toByte() }
        provisioner.provisionKey("removable-group", keyBytes)

        assertTrue(provisioner.isKeyProvisioned("removable-group"))

        val removed = provisioner.removeKey("removable-group")
        assertTrue("removeKey must return true", removed)
        assertFalse(provisioner.isKeyProvisioned("removable-group"))
        assertNull(provisioner.getKeyFingerprint("removable-group"))

        val state = provisioner.getProvisioningState("removable-group")
        assertFalse(state.isKeyProvisioned)
        assertEquals("NOT PROVISIONED", state.statusMessage)
    }

    private class FakeResponderKeyProvider : ResponderKeyProvider {
        private val keys = mutableMapOf<String, ByteArray>()

        override fun provisionResponderKey(keyId: String, keyMaterial: ByteArray) {
            keys[keyId] = keyMaterial.clone()
        }

        override fun getResponderKey(keyId: String): SecretKey? {
            val bytes = keys[keyId] ?: return null
            return SecretKeySpec(bytes, "AES")
        }

        override fun removeResponderKey(keyId: String) {
            keys.remove(keyId)
        }

        override fun hasResponderKey(keyId: String): Boolean {
            return keys.containsKey(keyId)
        }

        override fun getProvisionedKeyIds(): Set<String> {
            return keys.keys.toSet()
        }

        override fun isHardwareBacked(): Boolean {
            return false
        }
    }
}
