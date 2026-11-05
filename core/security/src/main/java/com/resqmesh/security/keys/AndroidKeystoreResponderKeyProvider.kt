package com.resqmesh.security.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stores provisioned group keys encrypted at rest. A per-install Android Keystore AES key wraps
 * the shared 32-byte responder key; the shared key itself is never written in plaintext.
 */
class AndroidKeystoreResponderKeyProvider(
    context: Context,
    preferencesName: String = "resqmesh_responder_keys"
) : ResponderKeyProvider {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun provisionResponderKey(keyId: String, keyMaterial: ByteArray) {
        validateKeyId(keyId)
        require(keyMaterial.size == AES_256_BYTES) { "Responder group key must be exactly 32 bytes." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        cipher.updateAAD(keyId.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(keyMaterial)
        val packed = ByteArray(cipher.iv.size + ciphertext.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(ciphertext, 0, packed, cipher.iv.size, ciphertext.size)
        preferences.edit().putString(storageKey(keyId), Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        packed.fill(0)
    }

    @Synchronized
    override fun getResponderKey(keyId: String): SecretKey? {
        validateKeyId(keyId)
        val stored = preferences.getString(storageKey(keyId), null) ?: return null
        val packed = try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("Stored responder key is malformed.", error)
        }
        if (packed.size <= GCM_NONCE_BYTES + GCM_TAG_BYTES) {
            packed.fill(0)
            throw GeneralSecurityException("Stored responder key is truncated.")
        }
        val nonce = packed.copyOfRange(0, GCM_NONCE_BYTES)
        val wrapped = packed.copyOfRange(GCM_NONCE_BYTES, packed.size)
        packed.fill(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(keyId.toByteArray(StandardCharsets.UTF_8))
        val material = cipher.doFinal(wrapped)
        wrapped.fill(0)
        if (material.size != AES_256_BYTES) {
            material.fill(0)
            throw GeneralSecurityException("Stored responder key has an invalid size.")
        }
        return try {
            SecretKeySpec(material, "AES")
        } finally {
            material.fill(0)
            nonce.fill(0)
        }
    }

    @Synchronized
    fun removeResponderKey(keyId: String) {
        validateKeyId(keyId)
        preferences.edit().remove(storageKey(keyId)).apply()
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun storageKey(keyId: String): String = "responder." + keyId

    private fun validateKeyId(keyId: String) {
        require(KEY_ID.matches(keyId)) { "keyId must contain 1–64 ASCII letters, digits, dots, underscores, or hyphens." }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_256_BYTES = 32
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val WRAPPING_KEY_ALIAS = "resqmesh.responder.wrap.v1"
        val KEY_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
