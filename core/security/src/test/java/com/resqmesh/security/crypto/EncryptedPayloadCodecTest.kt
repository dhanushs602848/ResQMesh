package com.resqmesh.security.crypto

import com.resqmesh.security.keys.ResponderKeyProvider
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedPayloadCodecTest {
    private val groupKey = ByteArray(32) { (it * 7 + 3).toByte() }
    private val keyId = "demo-group-1"

    @Test fun encryptDecryptSupportsSosAndNormalPayloads() {
        for (type in ResponderMessageType.entries) {
            val plain = "${type.name}:example payload".toByteArray()
            val encrypted = codec(groupKey).encrypt(keyId, type, plain)
            val decrypted = codec(groupKey).decrypt(encrypted)
            assertEquals(type, decrypted.type)
            assertEquals(keyId, decrypted.keyId)
            assertArrayEquals(plain, decrypted.plaintext)
        }
    }

    @Test fun wrongKeyIsRejected() {
        val packet = codec(groupKey).encrypt(keyId, ResponderMessageType.SOS, byteArrayOf(1, 2, 3))
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec(ByteArray(32) { (it + 1).toByte() }).decrypt(packet)
        }
    }

    @Test fun modifiedCiphertextIsRejected() {
        val packet = codec(groupKey).encrypt(keyId, ResponderMessageType.NORMAL, "hello".toByteArray())
        packet[packet.lastIndex] = (packet.last().toInt() xor 0x40).toByte()
        assertThrows(InvalidEncryptedPayloadException::class.java) { codec(groupKey).decrypt(packet) }
    }

    @Test fun alteredAuthenticatedMetadataIsRejected() {
        val packet = codec(groupKey).encrypt(keyId, ResponderMessageType.SOS, "help".toByteArray())
        packet[4] = ResponderMessageType.NORMAL.wireValue.toByte()
        assertThrows(InvalidEncryptedPayloadException::class.java) { codec(groupKey).decrypt(packet) }
    }

    @Test fun eachEncryptionUsesFreshNonce() {
        val codec = codec(groupKey)
        val first = codec.encrypt(keyId, ResponderMessageType.NORMAL, byteArrayOf(9))
        val second = codec.encrypt(keyId, ResponderMessageType.NORMAL, byteArrayOf(9))
        val nonceStart = 6 + keyId.length
        assertNotEquals(first.copyOfRange(nonceStart, nonceStart + 12).toList(), second.copyOfRange(nonceStart, nonceStart + 12).toList())
    }

    @Test fun malformedAndTruncatedPacketsAreRejected() {
        val packet = codec(groupKey).encrypt(keyId, ResponderMessageType.NORMAL, "payload".toByteArray())
        assertThrows(InvalidEncryptedPayloadException::class.java) { codec(groupKey).decrypt(packet.copyOf(packet.size - 1)) }
        val badMagic = packet.copyOf().also { it[0] = 0 }
        assertThrows(InvalidEncryptedPayloadException::class.java) { codec(groupKey).decrypt(badMagic) }
        val badLength = packet.copyOf().also { it[packet.size - 4] = 0x7f }
        assertThrows(InvalidEncryptedPayloadException::class.java) { codec(groupKey).decrypt(badLength) }
    }

    private fun codec(material: ByteArray): EncryptedPayloadCodec {
        val key = SecretKeySpec(material, "AES")
        val provider = object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? = if (keyId == this@EncryptedPayloadCodecTest.keyId) key else null
        }
        return EncryptedPayloadCodec(provider, SecureRandom())
    }
}
