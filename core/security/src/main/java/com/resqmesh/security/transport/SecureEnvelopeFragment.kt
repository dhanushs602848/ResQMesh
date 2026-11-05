package com.resqmesh.security.transport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/**
 * Exception thrown when a secure envelope fragment is corrupted, malformed, or violates wire bounds.
 */
class InvalidFragmentException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An individual fragment of a serialized [com.resqmesh.security.envelope.EncryptedMeshEnvelope].
 *
 * Wire format (18-byte header + variable payload):
 * - Magic bytes: 'S', 'F' (0x53, 0x46) [2 bytes]
 * - Transfer ID: 32-bit signed integer [4 bytes]
 * - Fragment Index: 16-bit unsigned integer [2 bytes]
 * - Total Fragments: 16-bit unsigned integer [2 bytes]
 * - Total Message Length: 32-bit signed integer [4 bytes]
 * - Checksum: 32-bit unsigned CRC32 of payload [4 bytes]
 * - Payload: Raw chunk bytes [0..N bytes]
 */
data class SecureEnvelopeFragment(
    val transferId: Int,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val totalMessageLength: Int,
    val payload: ByteArray,
    val checksum: Long = computeCrc32(payload)
) {
    init {
        if (totalFragments <= 0 || totalFragments > MAX_FRAGMENTS) {
            throw InvalidFragmentException("totalFragments ($totalFragments) must be between 1 and $MAX_FRAGMENTS.")
        }
        if (fragmentIndex < 0 || fragmentIndex >= totalFragments) {
            throw InvalidFragmentException("fragmentIndex ($fragmentIndex) out of bounds [0, ${totalFragments - 1}].")
        }
        if (totalMessageLength <= 0 || totalMessageLength > MAX_MESSAGE_LENGTH) {
            throw InvalidFragmentException("totalMessageLength ($totalMessageLength) must be between 1 and $MAX_MESSAGE_LENGTH.")
        }
        if (payload.size > MAX_PAYLOAD_CHUNK_SIZE) {
            throw InvalidFragmentException("payload size (${payload.size}) exceeds maximum allowed chunk size $MAX_PAYLOAD_CHUNK_SIZE.")
        }
    }

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream(HEADER_SIZE + payload.size)
        DataOutputStream(out).use { dos ->
            dos.write(MAGIC)
            dos.writeInt(transferId)
            dos.writeShort(fragmentIndex)
            dos.writeShort(totalFragments)
            dos.writeInt(totalMessageLength)
            dos.writeInt(checksum.toInt())
            dos.write(payload)
        }
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureEnvelopeFragment) return false
        return transferId == other.transferId &&
            fragmentIndex == other.fragmentIndex &&
            totalFragments == other.totalFragments &&
            totalMessageLength == other.totalMessageLength &&
            checksum == other.checksum &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = transferId
        result = 31 * result + fragmentIndex
        result = 31 * result + totalFragments
        result = 31 * result + totalMessageLength
        result = 31 * result + checksum.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        val MAGIC = byteArrayOf('S'.code.toByte(), 'F'.code.toByte()) // 0x53, 0x46
        const val HEADER_SIZE = 2 + 4 + 2 + 2 + 4 + 4 // 18 bytes
        const val MAX_FRAGMENTS = 2048
        const val MAX_MESSAGE_LENGTH = 128 * 1024 // 128 KB
        const val MAX_PAYLOAD_CHUNK_SIZE = 1024 // 1 KB max per BLE ATT chunk

        fun computeCrc32(data: ByteArray): Long {
            val crc = CRC32()
            crc.update(data)
            return crc.value
        }

        fun fromByteArray(bytes: ByteArray): SecureEnvelopeFragment {
            if (bytes.size < HEADER_SIZE) {
                throw InvalidFragmentException("Fragment byte array is truncated (size: ${bytes.size}, min: $HEADER_SIZE).")
            }

            val bais = ByteArrayInputStream(bytes)
            val dis = DataInputStream(bais)

            val magic = ByteArray(2)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw InvalidFragmentException("Invalid fragment magic bytes.")
            }

            val transferId = dis.readInt()
            val fragmentIndex = dis.readShort().toInt() and 0xFFFF
            val totalFragments = dis.readShort().toInt() and 0xFFFF
            val totalMessageLength = dis.readInt()
            val expectedChecksum = dis.readInt().toLong() and 0xFFFFFFFFL

            val payloadLength = bytes.size - HEADER_SIZE
            val payload = ByteArray(payloadLength)
            dis.readFully(payload)

            val actualChecksum = computeCrc32(payload)
            if (actualChecksum != expectedChecksum) {
                throw InvalidFragmentException("Fragment payload checksum mismatch: expected $expectedChecksum, computed $actualChecksum.")
            }

            return SecureEnvelopeFragment(
                transferId = transferId,
                fragmentIndex = fragmentIndex,
                totalFragments = totalFragments,
                totalMessageLength = totalMessageLength,
                payload = payload,
                checksum = expectedChecksum
            )
        }
    }
}
