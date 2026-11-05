package com.resqmesh.security.model

/**
 * Non-sensitive metadata representation of a provisioned responder key.
 *
 * CRITICAL SECURITY INVARIANT:
 * This model NEVER contains secret key material or raw bytes.
 */
data class ProvisionedKeyMetadata(
    val keyId: String,
    val fingerprint: String,
    val isHardwareBacked: Boolean = false,
    val isAvailable: Boolean = true
)

/**
 * Result returned upon successful key provisioning.
 */
data class KeyProvisioningResult(
    val keyId: String,
    val fingerprint: String,
    val isHardwareBacked: Boolean
)

/**
 * Current state of the security and responder key provisioning subsystem for presentation in UI.
 * Contains only non-sensitive descriptors and status indicators.
 */
data class SecurityProvisioningState(
    val isInitialized: Boolean = true,
    val isKeyProvisioned: Boolean = false,
    val activeKeyId: String? = null,
    val keyFingerprint: String? = null,
    val isHardwareBacked: Boolean = false,
    val statusMessage: String = "NOT PROVISIONED"
)

/**
 * Categorical failure reasons for security, envelope, and cryptographic operations.
 * Allows graceful handling without crash or silent delivery.
 */
enum class SecurityFailureReason {
    MISSING_KEY,
    UNKNOWN_KEY,
    WRONG_KEY,
    INVALID_AUTHENTICATION_TAG,
    INVALID_RELAY_AUTHENTICATION,
    INVALID_HOP_COUNT,
    DUPLICATE_MESSAGE,
    EXPIRED_MESSAGE,
    EXPIRED_TIMESTAMP,
    UNSUPPORTED_VERSION,
    MALFORMED_ENVELOPE,
    PAYLOAD_SIZE_EXCEEDED
}

/**
 * Result of attempting to process an encrypted envelope at an endpoint or relay node.
 */
sealed class EnvelopeProcessingResult {
    data class Success(val decrypted: com.resqmesh.security.envelope.DecryptedMeshEnvelope) : EnvelopeProcessingResult()
    data class RelayedWithoutDecryption(val envelope: com.resqmesh.security.envelope.EncryptedMeshEnvelope) : EnvelopeProcessingResult()
    data class Rejected(val reason: SecurityFailureReason, val detail: String) : EnvelopeProcessingResult()
}
