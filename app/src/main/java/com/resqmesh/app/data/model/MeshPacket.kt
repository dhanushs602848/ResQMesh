package com.resqmesh.app.data.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.CRC32

/**
 * Protocol classification of a MeshPacket.
 */
enum class MeshPacketType(val code: Byte) {
    SOS(1),
    ACK(2);

    companion object {
        fun fromCode(code: Byte): MeshPacketType {
            return entries.firstOrNull { it.code == code }
                ?: throw MalformedPacketException("Unknown MeshPacketType code: $code")
        }
    }
}

/**
 * Fundamental wire-level packet representation for ResQMesh over BLE.
 * Designed to be serialized compactly into binary bytes for low-latency transmission
 * and fragmentation across BLE GATT characteristics.
 */
data class MeshPacket(
    val messageId: String,
    val originNodeId: String,
    val senderNodeId: String,
    val packetType: MeshPacketType,
    val priority: IncidentPriority,
    val title: String,
    val content: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val maxHops: Int = DEFAULT_MAX_HOPS
) {
    init {
        validate()
    }

    /**
     * Validates all packet invariants and bounds.
     * Throws [MalformedPacketException] if any field is invalid.
     */
    fun validate() {
        if (messageId.isBlank()) {
            throw MalformedPacketException("messageId cannot be blank")
        }
        if (messageId.length > MAX_ID_LENGTH) {
            throw MalformedPacketException("messageId length (${messageId.length}) exceeds maximum of $MAX_ID_LENGTH")
        }
        if (!MESSAGE_ID_REGEX.matches(messageId)) {
            throw MalformedPacketException("messageId '$messageId' contains invalid characters. Must be alphanumeric and hyphens/underscores.")
        }
        if (originNodeId.isBlank()) {
            throw MalformedPacketException("originNodeId cannot be blank")
        }
        if (originNodeId.length > MAX_ID_LENGTH) {
            throw MalformedPacketException("originNodeId length (${originNodeId.length}) exceeds maximum of $MAX_ID_LENGTH")
        }
        if (senderNodeId.isBlank()) {
            throw MalformedPacketException("senderNodeId cannot be blank")
        }
        if (senderNodeId.length > MAX_ID_LENGTH) {
            throw MalformedPacketException("senderNodeId length (${senderNodeId.length}) exceeds maximum of $MAX_ID_LENGTH")
        }
        if (title.length > MAX_TITLE_LENGTH) {
            throw MalformedPacketException("title length (${title.length}) exceeds maximum of $MAX_TITLE_LENGTH")
        }
        if (content.length > MAX_CONTENT_LENGTH) {
            throw MalformedPacketException("content length (${content.length}) exceeds maximum of $MAX_CONTENT_LENGTH")
        }
        if (hopCount < 0) {
            throw MalformedPacketException("hopCount ($hopCount) cannot be negative")
        }
        if (maxHops <= 0 || maxHops > ABSOLUTE_MAX_HOPS) {
            throw MalformedPacketException("maxHops ($maxHops) must be between 1 and $ABSOLUTE_MAX_HOPS")
        }
        if (hopCount > maxHops) {
            throw MalformedPacketException("hopCount ($hopCount) exceeds maxHops ($maxHops)")
        }
        if (timestamp <= 0L) {
            throw MalformedPacketException("timestamp ($timestamp) must be positive")
        }
        if (latitude != null) {
            if (latitude !in -90.0..90.0) {
                throw MalformedPacketException("latitude ($latitude) out of valid range [-90.0, 90.0]")
            }
        }
        if (longitude != null) {
            if (longitude !in -180.0..180.0) {
                throw MalformedPacketException("longitude ($longitude) out of valid range [-180.0, 180.0]")
            }
        }
        if ((latitude != null && longitude == null) || (latitude == null && longitude != null)) {
            throw MalformedPacketException("Latitude and longitude must both be present or both be null")
        }
    }

    /**
     * Extracts the original SOS message ID referenced by this ACK packet.
     * Returns null if this packet is not an ACK.
     */
    fun getOriginalMessageId(): String? {
        if (packetType != MeshPacketType.ACK) return null
        return if (title.startsWith("ACK-")) {
            title.removePrefix("ACK-")
        } else if (title.startsWith("ACK:")) {
            title.removePrefix("ACK:")
        } else {
            title.ifBlank { null }
        }
    }

    /**
     * Extracts the node ID that created this acknowledgement.
     * Returns null if this packet is not an ACK.
     */
    fun getAcknowledgingNodeId(): String? {
        if (packetType != MeshPacketType.ACK) return null
        return if (content.isNotBlank()) content else senderNodeId
    }

    /**
     * Serializes this MeshPacket into a compact, validated binary byte array
     * prefixed with protocol magic bytes and protected by a CRC32 checksum.
     */
    fun toByteArray(): ByteArray {
        val payloadStream = ByteArrayOutputStream()
        val dataOut = DataOutputStream(payloadStream)

        // Protocol Header
        dataOut.writeByte(MAGIC_1.toInt())
        dataOut.writeByte(MAGIC_2.toInt())
        dataOut.writeByte(PROTOCOL_VERSION.toInt())

        // Metadata & Route
        dataOut.writeByte(packetType.code.toInt())
        dataOut.writeByte(priority.ordinal)
        dataOut.writeByte(hopCount)
        dataOut.writeByte(maxHops)
        dataOut.writeLong(timestamp)

        // Location coordinates (flagged to save bandwidth if absent)
        if (latitude != null && longitude != null) {
            dataOut.writeByte(1)
            dataOut.writeDouble(latitude)
            dataOut.writeDouble(longitude)
        } else {
            dataOut.writeByte(0)
        }

        // Variable length UTF strings
        dataOut.writeUTF(messageId)
        dataOut.writeUTF(originNodeId)
        dataOut.writeUTF(senderNodeId)
        dataOut.writeUTF(title)
        dataOut.writeUTF(content)
        dataOut.flush()

        val payloadBytes = payloadStream.toByteArray()

        // Compute CRC32 checksum across entire payload for wire-level error rejection
        val crc = CRC32()
        crc.update(payloadBytes)
        val checksum = crc.value

        val finalStream = ByteArrayOutputStream(payloadBytes.size + 4)
        finalStream.write(payloadBytes)
        val checksumOut = DataOutputStream(finalStream)
        checksumOut.writeInt(checksum.toInt())
        checksumOut.flush()

        val finalBytes = finalStream.toByteArray()
        if (finalBytes.size > MAX_PACKET_SIZE_BYTES) {
            throw PacketSizeExceededException(finalBytes.size, MAX_PACKET_SIZE_BYTES)
        }

        return finalBytes
    }

    companion object {
        const val PROTOCOL_VERSION: Byte = 1
        const val MAGIC_1: Byte = 0x52.toByte() // 'R'
        const val MAGIC_2: Byte = 0x4D.toByte() // 'M'

        const val DEFAULT_MAX_HOPS = 7
        const val ABSOLUTE_MAX_HOPS = 15

        const val MAX_PACKET_SIZE_BYTES = 1024
        const val MAX_ID_LENGTH = 64
        const val MAX_TITLE_LENGTH = 120
        const val MAX_CONTENT_LENGTH = 500

        // Minimum wire size = 2 (magic) + 1 (version) + 1 (type) + 1 (priority) + 1 (hop) + 1 (maxHops) + 8 (time) + 1 (locFlag) + 5*2 (empty string UTF overheads) + 4 (crc) = 30 bytes
        const val MIN_WIRE_SIZE_BYTES = 30

        val MESSAGE_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")

        /**
         * Deserializes a binary byte array back into a [MeshPacket].
         * Performs wire verification (Magic bytes, Protocol version, CRC32 Checksum)
         * and validates all invariant fields before returning.
         */
        fun fromByteArray(bytes: ByteArray): MeshPacket {
            if (bytes.size < MIN_WIRE_SIZE_BYTES) {
                throw MalformedPacketException("Packet size (${bytes.size} bytes) is smaller than minimum wire size ($MIN_WIRE_SIZE_BYTES bytes)")
            }
            if (bytes.size > MAX_PACKET_SIZE_BYTES) {
                throw PacketSizeExceededException(bytes.size, MAX_PACKET_SIZE_BYTES)
            }

            // Verify Magic Bytes
            if (bytes[0] != MAGIC_1 || bytes[1] != MAGIC_2) {
                throw MalformedPacketException("Invalid protocol magic bytes: [${bytes[0]}, ${bytes[1]}]")
            }

            // Verify Protocol Version
            val version = bytes[2]
            if (version != PROTOCOL_VERSION) {
                throw UnsupportedProtocolVersionException(version)
            }

            // Verify CRC32 Checksum
            val payloadLength = bytes.size - 4
            val expectedCrc = ((bytes[payloadLength].toLong() and 0xFF shl 24) or
                    (bytes[payloadLength + 1].toLong() and 0xFF shl 16) or
                    (bytes[payloadLength + 2].toLong() and 0xFF shl 8) or
                    (bytes[payloadLength + 3].toLong() and 0xFF)) and 0xFFFFFFFFL

            val crcCalculator = CRC32()
            crcCalculator.update(bytes, 0, payloadLength)
            val actualCrc = crcCalculator.value

            if (expectedCrc != actualCrc) {
                throw ChecksumMismatchException(expected = expectedCrc, actual = actualCrc)
            }

            // Parse Payload
            try {
                val inputStream = ByteArrayInputStream(bytes, 3, payloadLength - 3)
                val dataIn = DataInputStream(inputStream)

                val typeCode = dataIn.readByte()
                val packetType = MeshPacketType.fromCode(typeCode)

                val priorityOrdinal = dataIn.readByte().toInt()
                val priorities = IncidentPriority.entries
                if (priorityOrdinal !in priorities.indices) {
                    throw MalformedPacketException("Invalid priority ordinal: $priorityOrdinal")
                }
                val priority = priorities[priorityOrdinal]

                val hopCount = dataIn.readByte().toInt() and 0xFF
                val maxHops = dataIn.readByte().toInt() and 0xFF
                val timestamp = dataIn.readLong()

                val hasLocation = dataIn.readByte().toInt() == 1
                val latitude = if (hasLocation) dataIn.readDouble() else null
                val longitude = if (hasLocation) dataIn.readDouble() else null

                val messageId = dataIn.readUTF()
                val originNodeId = dataIn.readUTF()
                val senderNodeId = dataIn.readUTF()
                val title = dataIn.readUTF()
                val content = dataIn.readUTF()

                return MeshPacket(
                    messageId = messageId,
                    originNodeId = originNodeId,
                    senderNodeId = senderNodeId,
                    packetType = packetType,
                    priority = priority,
                    title = title,
                    content = content,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = timestamp,
                    hopCount = hopCount,
                    maxHops = maxHops
                )
            } catch (e: MalformedPacketException) {
                throw e
            } catch (e: Exception) {
                throw MalformedPacketException("Corrupted packet structure: ${e.message}", e)
            }
        }

        /**
         * Safely creates an ACK [MeshPacket] in response to a validated incoming SOS packet.
         *
         * @param sosPacket The inbound SOS packet to acknowledge.
         * @param acknowledgingNodeId The local node ID generating this ACK.
         * @param maxHops Max hops allowed for this ACK to reach the SOS originator.
         */
        fun createAck(
            sosPacket: MeshPacket,
            acknowledgingNodeId: String,
            maxHops: Int = sosPacket.maxHops
        ): MeshPacket {
            if (sosPacket.packetType != MeshPacketType.SOS) {
                throw IllegalArgumentException("Cannot create ACK for non-SOS packet type: ${sosPacket.packetType}")
            }
            if (acknowledgingNodeId.isBlank()) {
                throw IllegalArgumentException("acknowledgingNodeId cannot be blank")
            }

            val cleanAckNodeId = acknowledgingNodeId.replace(':', '_').trim()
            val rawId = "ACK-${sosPacket.messageId}-$cleanAckNodeId"
            val safeId = if (rawId.length > MAX_ID_LENGTH) rawId.take(MAX_ID_LENGTH) else rawId

            return MeshPacket(
                messageId = safeId,
                originNodeId = sosPacket.originNodeId,
                senderNodeId = acknowledgingNodeId,
                packetType = MeshPacketType.ACK,
                priority = IncidentPriority.INFO,
                title = "ACK-${sosPacket.messageId}",
                content = acknowledgingNodeId,
                latitude = sosPacket.latitude,
                longitude = sosPacket.longitude,
                timestamp = System.currentTimeMillis(),
                hopCount = 0,
                maxHops = maxHops
            )
        }
    }
}

/**
 * Representation of a fragment chunk of a serialized MeshPacket.
 * Enables transmission across BLE MTU-constrained characteristics.
 */
data class MeshPacketFragment(
    val messageId: String,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshPacketFragment

        if (fragmentIndex != other.fragmentIndex) return false
        if (totalFragments != other.totalFragments) return false
        if (messageId != other.messageId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + fragmentIndex
        result = 31 * result + totalFragments
        result = 31 * result + data.contentHashCode()
        return result
    }

    fun toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        val out = DataOutputStream(stream)

        out.writeByte(FRAGMENT_MAGIC_1.toInt())
        out.writeByte(FRAGMENT_MAGIC_2.toInt())
        out.writeUTF(messageId)
        out.writeShort(fragmentIndex)
        out.writeShort(totalFragments)
        out.writeShort(data.size)
        out.write(data)
        out.flush()

        val payload = stream.toByteArray()
        val crc = CRC32()
        crc.update(payload)

        val finalStream = ByteArrayOutputStream(payload.size + 4)
        finalStream.write(payload)
        val finalOut = DataOutputStream(finalStream)
        finalOut.writeInt(crc.value.toInt())
        finalOut.flush()

        return finalStream.toByteArray()
    }

    companion object {
        const val FRAGMENT_MAGIC_1: Byte = 0x52.toByte() // 'R'
        const val FRAGMENT_MAGIC_2: Byte = 0x46.toByte() // 'F'

        fun fromByteArray(bytes: ByteArray): MeshPacketFragment {
            if (bytes.size < 12) {
                throw MalformedPacketException("Fragment size (${bytes.size}) too small")
            }
            if (bytes[0] != FRAGMENT_MAGIC_1 || bytes[1] != FRAGMENT_MAGIC_2) {
                throw MalformedPacketException("Invalid fragment magic bytes: [${bytes[0]}, ${bytes[1]}]")
            }

            val payloadLength = bytes.size - 4
            val expectedCrc = ((bytes[payloadLength].toLong() and 0xFF shl 24) or
                    (bytes[payloadLength + 1].toLong() and 0xFF shl 16) or
                    (bytes[payloadLength + 2].toLong() and 0xFF shl 8) or
                    (bytes[payloadLength + 3].toLong() and 0xFF)) and 0xFFFFFFFFL

            val crc = CRC32()
            crc.update(bytes, 0, payloadLength)
            if (expectedCrc != crc.value) {
                throw ChecksumMismatchException(expectedCrc, crc.value)
            }

            val input = ByteArrayInputStream(bytes, 2, payloadLength - 2)
            val dataIn = DataInputStream(input)

            val messageId = dataIn.readUTF()
            val fragmentIndex = dataIn.readUnsignedShort()
            val totalFragments = dataIn.readUnsignedShort()
            val dataSize = dataIn.readUnsignedShort()
            val chunkData = ByteArray(dataSize)
            dataIn.readFully(chunkData)

            return MeshPacketFragment(
                messageId = messageId,
                fragmentIndex = fragmentIndex,
                totalFragments = totalFragments,
                data = chunkData
            )
        }
    }
}

/**
 * Utility to fragment a serialized [MeshPacket] into MTU-friendly chunks.
 */
object MeshPacketFragmenter {

    // Default BLE GATT MTU payload capacity (e.g., MTU 247 with overhead reserved)
    const val DEFAULT_MAX_CHUNK_SIZE = 180
    // Conservative fallback for minimum legacy ATT MTU (23 bytes - 3B ATT header - 16B fragment header)
    const val MIN_SAFE_CHUNK_SIZE = 20

    fun fragment(packet: MeshPacket, maxChunkSize: Int = DEFAULT_MAX_CHUNK_SIZE): List<MeshPacketFragment> {
        val safeChunkSize = maxChunkSize.coerceAtLeast(MIN_SAFE_CHUNK_SIZE)
        val packetBytes = packet.toByteArray()

        val totalFragments = (packetBytes.size + safeChunkSize - 1) / safeChunkSize
        val fragments = ArrayList<MeshPacketFragment>(totalFragments)

        for (i in 0 until totalFragments) {
            val start = i * safeChunkSize
            val end = (start + safeChunkSize).coerceAtMost(packetBytes.size)
            val chunk = packetBytes.copyOfRange(start, end)
            fragments.add(
                MeshPacketFragment(
                    messageId = packet.messageId,
                    fragmentIndex = i,
                    totalFragments = totalFragments,
                    data = chunk
                )
            )
        }

        return fragments
    }
}

/**
 * Thread-safe reassembler that buffers incoming [MeshPacketFragment]s and
 * emits the complete [MeshPacket] once all chunks have arrived.
 */
class MeshPacketReassembler(
    private val fragmentTimeoutMs: Long = 30_000L
) {
    private data class AssemblyBucket(
        val totalFragments: Int,
        val fragments: Array<ByteArray?>,
        var receivedCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val assemblyMap = Collections.synchronizedMap(LinkedHashMap<String, AssemblyBucket>())

    @Synchronized
    fun addFragment(fragment: MeshPacketFragment): MeshPacket? {
        cleanExpiredBuckets()

        if (fragment.totalFragments <= 0 || fragment.fragmentIndex >= fragment.totalFragments || fragment.fragmentIndex < 0) {
            throw MalformedPacketException("Invalid fragment index ${fragment.fragmentIndex} of total ${fragment.totalFragments}")
        }

        val bucket = assemblyMap.getOrPut(fragment.messageId) {
            AssemblyBucket(
                totalFragments = fragment.totalFragments,
                fragments = arrayOfNulls(fragment.totalFragments)
            )
        }

        if (bucket.totalFragments != fragment.totalFragments) {
            throw MalformedPacketException("Fragment totalFragments mismatch for message ${fragment.messageId}")
        }

        if (bucket.fragments[fragment.fragmentIndex] == null) {
            bucket.fragments[fragment.fragmentIndex] = fragment.data
            bucket.receivedCount++
        }

        if (bucket.receivedCount == bucket.totalFragments) {
            assemblyMap.remove(fragment.messageId)

            val totalSize = bucket.fragments.sumOf { it?.size ?: 0 }
            val reassembledBytes = ByteArray(totalSize)
            var offset = 0
            for (chunk in bucket.fragments) {
                if (chunk == null) throw MalformedPacketException("Missing fragment chunk during assembly")
                System.arraycopy(chunk, 0, reassembledBytes, offset, chunk.size)
                offset += chunk.size
            }

            return MeshPacket.fromByteArray(reassembledBytes)
        }

        return null
    }

    @Synchronized
    fun clear() {
        assemblyMap.clear()
    }

    private fun cleanExpiredBuckets() {
        val now = System.currentTimeMillis()
        val iterator = assemblyMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAt > fragmentTimeoutMs) {
                iterator.remove()
            }
        }
    }
}

/**
 * Interface for deduplication tracking of received mesh messages.
 */
interface DuplicateDetector {
    fun isDuplicate(messageId: String): Boolean
    fun markSeen(messageId: String): Boolean
}

/**
 * High-performance thread-safe LRU implementation of [DuplicateDetector] with TTL expiration.
 */
class InMemoryDuplicateDetector(
    private val maxCapacity: Int = 1000,
    private val ttlMs: Long = 10 * 60 * 1000L // 10 minutes default
) : DuplicateDetector {

    private data class Entry(val timestamp: Long)

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(maxCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
                return size > maxCapacity
            }
        }
    )

    override fun isDuplicate(messageId: String): Boolean {
        cleanupExpired()
        return cache.containsKey(messageId)
    }

    override fun markSeen(messageId: String): Boolean {
        cleanupExpired()
        val isNew = cache.putIfAbsent(messageId, Entry(System.currentTimeMillis())) == null
        return isNew
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > ttlMs) {
                iterator.remove()
            }
        }
    }
}

// Custom Protocol Exceptions
open class MeshProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MalformedPacketException(message: String, cause: Throwable? = null) : MeshProtocolException(message, cause)
class UnsupportedProtocolVersionException(val version: Byte) : MeshProtocolException("Unsupported protocol version: $version")
class ChecksumMismatchException(val expected: Long, val actual: Long) : MeshProtocolException("Checksum mismatch: expected $expected, got $actual")
class PacketSizeExceededException(val size: Int, val max: Int) : MeshProtocolException("Packet size ($size bytes) exceeds limit ($max bytes)")
