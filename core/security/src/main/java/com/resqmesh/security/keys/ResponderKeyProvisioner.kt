package com.resqmesh.security.keys

import com.resqmesh.security.model.KeyProvisioningResult
import com.resqmesh.security.model.ProvisionedKeyMetadata
import com.resqmesh.security.model.SecurityProvisioningState
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/**
 * Foundation interface for secure responder-group key provisioning.
 *
 * Implements strict security invariants:
 * - Validates key length (must be exactly 32 bytes for AES-256).
 * - Validates key identifiers (1–64 ASCII alphanumeric, '.', '_', '-').
 * - Protects key material using [AndroidKeystoreResponderKeyProvider].
 * - Never returns or leaks raw key bytes in UI models or logs.
 * - Zeroizes temporary key arrays in memory.
 */
interface ResponderKeyProvisioner {

    /**
     * Securely imports and persists a 32-byte AES-256 group key for [keyId].
     *
     * @param keyId Unique alphanumeric key identifier.
     * @param keyMaterial Exactly 32 bytes of raw AES-256 key material.
     * @param clearSourceBytes If true, [keyMaterial] will be safely wiped with zeros after import.
     * @return [Result] containing [KeyProvisioningResult] on success, or an exception on failure.
     */
    fun provisionKey(
        keyId: String,
        keyMaterial: ByteArray,
        clearSourceBytes: Boolean = true
    ): Result<KeyProvisioningResult>

    /**
     * Checks if a key is currently provisioned for [keyId].
     */
    fun isKeyProvisioned(keyId: String): Boolean

    /**
     * Checks if any responder key is currently provisioned in storage.
     */
    fun hasAnyProvisionedKey(): Boolean

    /**
     * Retrieves the provisioned [SecretKey] for [keyId], or null if not found.
     */
    fun getSecretKey(keyId: String): SecretKey?

    /**
     * Returns the non-sensitive SHA-256 hex fingerprint for [keyId], or null if not provisioned.
     */
    fun getKeyFingerprint(keyId: String): String?

    /**
     * Removes the key identified by [keyId] from secure storage.
     *
     * @return true if key was removed or did not exist.
     */
    fun removeKey(keyId: String): Boolean

    /**
     * Returns non-sensitive metadata for [keyId], or null if not found.
     */
    fun getKeyMetadata(keyId: String): ProvisionedKeyMetadata?

    /**
     * Returns the non-sensitive UI state describing the current key provisioning status.
     */
    fun getProvisioningState(preferredKeyId: String? = null): SecurityProvisioningState
}

/**
 * Default implementation of [ResponderKeyProvisioner] delegating to [ResponderKeyProvider].
 */
class DefaultResponderKeyProvisioner(
    private val keyProvider: ResponderKeyProvider
) : ResponderKeyProvisioner {

    override fun provisionKey(
        keyId: String,
        keyMaterial: ByteArray,
        clearSourceBytes: Boolean
    ): Result<KeyProvisioningResult> {
        return try {
            if (!KEY_ID_REGEX.matches(keyId)) {
                return Result.failure(
                    IllegalArgumentException("Invalid keyId: '$keyId'. Must contain 1–64 ASCII letters, digits, dots, underscores, or hyphens.")
                )
            }
            if (keyMaterial.size != AES_256_BYTES) {
                return Result.failure(
                    IllegalArgumentException("Invalid key size: ${keyMaterial.size} bytes. Responder key must be exactly 32 bytes (AES-256).")
                )
            }

            val fingerprint = KeyFingerprint.sha256(keyMaterial)
            keyProvider.provisionResponderKey(keyId, keyMaterial)
            val isHardware = keyProvider.isHardwareBacked()

            Result.success(
                KeyProvisioningResult(
                    keyId = keyId,
                    fingerprint = fingerprint,
                    isHardwareBacked = isHardware
                )
            )
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            if (clearSourceBytes) {
                keyMaterial.fill(0)
            }
        }
    }

    override fun isKeyProvisioned(keyId: String): Boolean {
        return keyProvider.hasResponderKey(keyId)
    }

    override fun hasAnyProvisionedKey(): Boolean {
        return keyProvider.getProvisionedKeyIds().isNotEmpty()
    }

    override fun getSecretKey(keyId: String): SecretKey? {
        return try {
            keyProvider.getResponderKey(keyId)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    override fun getKeyFingerprint(keyId: String): String? {
        val secretKey = getSecretKey(keyId) ?: return null
        val encoded = secretKey.encoded ?: return null
        return try {
            KeyFingerprint.sha256(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    override fun removeKey(keyId: String): Boolean {
        return try {
            keyProvider.removeResponderKey(keyId)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun getKeyMetadata(keyId: String): ProvisionedKeyMetadata? {
        val fingerprint = getKeyFingerprint(keyId) ?: return null
        return ProvisionedKeyMetadata(
            keyId = keyId,
            fingerprint = fingerprint,
            isHardwareBacked = keyProvider.isHardwareBacked(),
            isAvailable = true
        )
    }

    override fun getProvisioningState(preferredKeyId: String?): SecurityProvisioningState {
        val activeKeyId = preferredKeyId?.takeIf { isKeyProvisioned(it) }
            ?: keyProvider.getProvisionedKeyIds().firstOrNull()

        if (activeKeyId == null) {
            return SecurityProvisioningState(
                isInitialized = true,
                isKeyProvisioned = false,
                activeKeyId = null,
                keyFingerprint = null,
                isHardwareBacked = keyProvider.isHardwareBacked(),
                statusMessage = "NOT PROVISIONED"
            )
        }

        val metadata = getKeyMetadata(activeKeyId)
        return SecurityProvisioningState(
            isInitialized = true,
            isKeyProvisioned = true,
            activeKeyId = activeKeyId,
            keyFingerprint = metadata?.fingerprint,
            isHardwareBacked = metadata?.isHardwareBacked ?: false,
            statusMessage = "ACTIVE"
        )
    }

    private companion object {
        const val AES_256_BYTES = 32
        val KEY_ID_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
