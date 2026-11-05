package com.resqmesh.security.transport

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Thread-safe reassembler that buffers incoming [SecureEnvelopeFragment]s and
 * reconstructs the original encrypted envelope byte array once all chunks arrive.
 *
 * Implements strict memory and security guardrails:
 * - Transfers isolated by peer address and transfer ID.
 * - Out-of-order fragment buffering.
 * - Duplicate fragment detection and ignoring.
 * - Incomplete transfer timeout eviction.
 * - Bounded maximum concurrent transfer capacity to prevent memory exhaustion attacks.
 * - Byte-level total length and integrity validation.
 */
class SecureEnvelopeReassembler(
    private val reassemblyTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxConcurrentTransfers: Int = DEFAULT_MAX_CONCURRENT_TRANSFERS
) {

    private data class TransferKey(
        val peerAddress: String,
        val transferId: Int
    )

    private class AssemblyBucket(
        val transferId: Int,
        val totalFragments: Int,
        val totalMessageLength: Int,
        val fragments: Array<ByteArray?>,
        var receivedCount: Int = 0,
        var receivedBytes: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )

    // Synchronized LRU-style map holding active assembly sessions
    private val assemblyMap = Collections.synchronizedMap(LinkedHashMap<TransferKey, AssemblyBucket>())

    /**
     * Ingests a raw byte array representing a [SecureEnvelopeFragment].
     *
     * @param peerAddress Remote peer address.
     * @param fragmentBytes Raw fragment wire bytes.
     * @return Fully reassembled byte array if this fragment completed the message, or null if incomplete.
     * @throws InvalidFragmentException if the fragment is malformed, corrupted, or violates limits.
     */
    @Synchronized
    fun addFragmentBytes(peerAddress: String, fragmentBytes: ByteArray): ByteArray? {
        val fragment = SecureEnvelopeFragment.fromByteArray(fragmentBytes)
        return addFragment(peerAddress, fragment)
    }

    /**
     * Ingests a [SecureEnvelopeFragment] from [peerAddress].
     *
     * @param peerAddress Remote peer address (e.g., Bluetooth MAC address or node identifier).
     * @param fragment Parsed and validated fragment.
     * @return Fully reassembled byte array if this fragment completed the message, or null if incomplete.
     * @throws InvalidFragmentException if the fragment contradicts existing transfer metadata or bounds.
     */
    @Synchronized
    fun addFragment(peerAddress: String, fragment: SecureEnvelopeFragment): ByteArray? {
        cleanExpiredBuckets()

        val key = TransferKey(peerAddress.trim().uppercase(), fragment.transferId)
        var bucket = assemblyMap[key]

        if (bucket == null) {
            // Enforce concurrency limit: evict oldest session if capacity reached
            if (assemblyMap.size >= maxConcurrentTransfers) {
                val oldestKey = assemblyMap.keys.firstOrNull()
                if (oldestKey != null) {
                    assemblyMap.remove(oldestKey)
                }
            }

            bucket = AssemblyBucket(
                transferId = fragment.transferId,
                totalFragments = fragment.totalFragments,
                totalMessageLength = fragment.totalMessageLength,
                fragments = arrayOfNulls(fragment.totalFragments)
            )
            assemblyMap[key] = bucket
        } else {
            // Guard against transfer metadata tampering across chunks
            if (bucket.totalFragments != fragment.totalFragments) {
                throw InvalidFragmentException(
                    "Fragment totalFragments mismatch for transfer ${fragment.transferId}: expected ${bucket.totalFragments}, received ${fragment.totalFragments}."
                )
            }
            if (bucket.totalMessageLength != fragment.totalMessageLength) {
                throw InvalidFragmentException(
                    "Fragment totalMessageLength mismatch for transfer ${fragment.transferId}: expected ${bucket.totalMessageLength}, received ${fragment.totalMessageLength}."
                )
            }
        }

        // Duplicate fragment check
        if (bucket.fragments[fragment.fragmentIndex] != null) {
            // Duplicate chunk received; ignore without corrupting state or counters
            return null
        }

        // Record fragment chunk
        bucket.fragments[fragment.fragmentIndex] = fragment.payload
        bucket.receivedCount++
        bucket.receivedBytes += fragment.payload.size

        // Check completion
        if (bucket.receivedCount == bucket.totalFragments) {
            assemblyMap.remove(key)

            if (bucket.receivedBytes != bucket.totalMessageLength) {
                throw InvalidFragmentException(
                    "Reassembled byte count (${bucket.receivedBytes}) does not match declared totalMessageLength (${bucket.totalMessageLength})."
                )
            }

            val reassembled = ByteArray(bucket.totalMessageLength)
            var offset = 0
            for (index in bucket.fragments.indices) {
                val chunk = bucket.fragments[index]
                    ?: throw InvalidFragmentException("Missing chunk at index $index during final reassembly.")
                System.arraycopy(chunk, 0, reassembled, offset, chunk.size)
                offset += chunk.size
            }

            return reassembled
        }

        return null
    }

    @Synchronized
    fun getActiveTransferCount(): Int {
        cleanExpiredBuckets()
        return assemblyMap.size
    }

    @Synchronized
    fun clearPeer(peerAddress: String) {
        val target = peerAddress.trim().uppercase()
        val iterator = assemblyMap.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().key.peerAddress == target) {
                iterator.remove()
            }
        }
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
            if (now - entry.value.createdAt > reassemblyTimeoutMs) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_CONCURRENT_TRANSFERS = 100
    }
}
