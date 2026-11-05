package com.resqmesh.security.transport

import java.security.SecureRandom

/**
 * Splits raw envelope byte arrays into MTU-sized [SecureEnvelopeFragment] instances.
 */
object SecureEnvelopeFragmenter {

    const val MIN_CHUNK_PAYLOAD = 1
    const val DEFAULT_MAX_CHUNK_PAYLOAD = 500

    private val secureRandom = SecureRandom()

    /**
     * Fragments [envelopeBytes] into a list of [SecureEnvelopeFragment]s sized to fit within [maxPayloadPerWrite].
     *
     * @param envelopeBytes Complete serialized envelope byte array.
     * @param maxPayloadPerWrite Usable BLE ATT payload per write (typically negotiatedMTU - 3).
     * @param transferId Unique identifier for this transfer session (generated randomly if not supplied).
     * @return List of fragments ready for sequential transmission over GATT.
     */
    fun fragment(
        envelopeBytes: ByteArray,
        maxPayloadPerWrite: Int,
        transferId: Int = secureRandom.nextInt()
    ): List<SecureEnvelopeFragment> {
        if (envelopeBytes.size > SecureEnvelopeFragment.MAX_MESSAGE_LENGTH) {
            throw IllegalArgumentException(
                "Envelope size (${envelopeBytes.size} bytes) exceeds maximum allowable size (${SecureEnvelopeFragment.MAX_MESSAGE_LENGTH} bytes)."
            )
        }

        val availableChunkPayload = (maxPayloadPerWrite - SecureEnvelopeFragment.HEADER_SIZE)
            .coerceAtLeast(MIN_CHUNK_PAYLOAD)
            .coerceAtMost(SecureEnvelopeFragment.MAX_PAYLOAD_CHUNK_SIZE)

        if (envelopeBytes.isEmpty()) {
            return listOf(
                SecureEnvelopeFragment(
                    transferId = transferId,
                    fragmentIndex = 0,
                    totalFragments = 1,
                    totalMessageLength = 0,
                    payload = ByteArray(0)
                )
            )
        }

        val totalFragments = (envelopeBytes.size + availableChunkPayload - 1) / availableChunkPayload
        val fragments = ArrayList<SecureEnvelopeFragment>(totalFragments)

        for (i in 0 until totalFragments) {
            val start = i * availableChunkPayload
            val end = (start + availableChunkPayload).coerceAtMost(envelopeBytes.size)
            val chunk = envelopeBytes.copyOfRange(start, end)

            fragments.add(
                SecureEnvelopeFragment(
                    transferId = transferId,
                    fragmentIndex = i,
                    totalFragments = totalFragments,
                    totalMessageLength = envelopeBytes.size,
                    payload = chunk
                )
            )
        }

        return fragments
    }
}
