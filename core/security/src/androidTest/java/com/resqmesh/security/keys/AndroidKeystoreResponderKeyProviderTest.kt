package com.resqmesh.security.keys

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * On-device Android Keystore verification for [AndroidKeystoreResponderKeyProvider].
 *
 * Verifies wrapping key generation, AES-256 wrapping/unwrapping, persistence across provider
 * recreations, tamper rejection, key fingerprint consistency, and hardware-backed storage inspection.
 *
 * Note: Only temporary random test keys are used and all in-memory buffers are safely zeroized in teardown.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreResponderKeyProviderTest {

    private val testPrefsName = "test_resqmesh_responder_keys"
    private val testKeyId = "test-group-key-1"
    private val testKeyMaterial = ByteArray(32)

    private lateinit var context: Context
    private lateinit var provider: AndroidKeystoreResponderKeyProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecureRandom().nextBytes(testKeyMaterial)
        provider = AndroidKeystoreResponderKeyProvider(context, testPrefsName)
    }

    @After
    fun tearDown() {
        try {
            provider.removeResponderKey(testKeyId)
        } catch (_: Exception) {}

        context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
        testKeyMaterial.fill(0)
    }

    @Test
    fun testKeystoreWrappingKeyCreationAndHardwareBackedStatus() {
        // Trigger wrapping key creation by provisioning a temporary test key
        provider.provisionResponderKey(testKeyId, testKeyMaterial)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(
            "AndroidKeyStore must contain the ResQMesh wrapping key alias",
            keyStore.containsAlias("resqmesh.responder.wrap.v1")
        )

        val wrappingKey = keyStore.getKey("resqmesh.responder.wrap.v1", null) as? SecretKey
        assertNotNull("Wrapping key retrieved from AndroidKeyStore must not be null", wrappingKey)

        // Inspect hardware-backed status via KeyInfo
        val factory = SecretKeyFactory.getInstance(wrappingKey!!.algorithm, "AndroidKeyStore")
        val keyInfo = factory.getKeySpec(wrappingKey, KeyInfo::class.java) as KeyInfo

        val isInsideSecureHardware = keyInfo.isInsideSecureHardware
        // Print hardware-backed status for device diagnostic verification (safe, contains no key material)
        println("ResQMesh Keystore Diagnostic: isInsideSecureHardware = $isInsideSecureHardware")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            println("ResQMesh Keystore Diagnostic: securityLevel = ${keyInfo.securityLevel}")
        }
    }

    @Test
    fun testWrapAndUnwrapTemporary256BitKey() {
        provider.provisionResponderKey(testKeyId, testKeyMaterial)

        val retrievedKey = provider.getResponderKey(testKeyId)
        assertNotNull("Retrieved responder key must not be null", retrievedKey)

        val retrievedEncoded = retrievedKey!!.encoded
        assertNotNull("Unwrapped key material must be exportable as SecretKeySpec", retrievedEncoded)
        try {
            assertEquals("Unwrapped key must be 32 bytes (AES-256)", 32, retrievedEncoded.size)
            assertArrayEquals("Unwrapped key material must match original test key", testKeyMaterial, retrievedEncoded)
        } finally {
            retrievedEncoded.fill(0)
        }
    }

    @Test
    fun testRetrievalAfterProviderRecreation() {
        provider.provisionResponderKey(testKeyId, testKeyMaterial)

        // Simulate new application launch / provider recreation with same SharedPreferences
        val recreatedProvider = AndroidKeystoreResponderKeyProvider(context, testPrefsName)
        val retrievedKey = recreatedProvider.getResponderKey(testKeyId)
        assertNotNull("Retrieved key from recreated provider must not be null", retrievedKey)

        val retrievedEncoded = retrievedKey!!.encoded
        try {
            assertArrayEquals(
                "Key unwrapped by recreated provider instance must match original material",
                testKeyMaterial,
                retrievedEncoded
            )
        } finally {
            retrievedEncoded.fill(0)
        }
    }

    @Test
    fun testRejectionOfCorruptedWrappedData() {
        provider.provisionResponderKey(testKeyId, testKeyMaterial)

        val prefs = context.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE)
        val storageKey = "responder.$testKeyId"
        val validBase64 = prefs.getString(storageKey, null)
        assertNotNull("Encrypted key must be stored in SharedPreferences", validBase64)

        val rawBytes = Base64.decode(validBase64, Base64.NO_WRAP)
        // Corrupt the ciphertext / GCM authentication tag
        rawBytes[rawBytes.lastIndex] = (rawBytes[rawBytes.lastIndex].toInt() xor 0xFF).toByte()
        val corruptedBase64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)

        prefs.edit().putString(storageKey, corruptedBase64).commit()

        // Decryption with corrupted ciphertext/tag must fail AEAD authentication
        assertThrows(GeneralSecurityException::class.java) {
            provider.getResponderKey(testKeyId)
        }
    }

    @Test
    fun testNonExistentKeyReturnsNull() {
        val nonExistent = provider.getResponderKey("unknown-key-id")
        assertNull("Querying unprovisioned keyId must return null", nonExistent)
    }

    @Test
    fun testKeyFingerprintConsistencyAcrossWrapCycle() {
        val originalFingerprint = KeyFingerprint.sha256(testKeyMaterial)

        provider.provisionResponderKey(testKeyId, testKeyMaterial)
        val retrievedKey = provider.getResponderKey(testKeyId)
        assertNotNull(retrievedKey)

        val retrievedEncoded = retrievedKey!!.encoded
        val retrievedFingerprint = try {
            KeyFingerprint.sha256(retrievedEncoded)
        } finally {
            retrievedEncoded.fill(0)
        }

        assertEquals(
            "SHA-256 fingerprint must be identical before wrapping and after unwrapping",
            originalFingerprint,
            retrievedFingerprint
        )
    }
}
