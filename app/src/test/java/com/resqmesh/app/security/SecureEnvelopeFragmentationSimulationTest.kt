package com.resqmesh.app.security

import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.security.envelope.SecureMeshEnvelopeCodec
import com.resqmesh.security.keys.ResponderKeyProvider
import com.resqmesh.security.model.EnvelopeProcessingResult
import com.resqmesh.security.transport.SecureEnvelopeFragmenter
import com.resqmesh.security.transport.SecureEnvelopeReassembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end integration test validating secure envelope fragmentation, out-of-order reassembly,
 * and decryption across simulated BLE MTU boundaries.
 */
class SecureEnvelopeFragmentationSimulationTest {

    private val testKeyId = "responder-alpha-key"
    private val testKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val testSecretKey = SecretKeySpec(testKeyBytes, "AES")

    private val keyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? =
            if (keyId == testKeyId) testSecretKey else null
    }

    private lateinit var codec: SecureMeshEnvelopeCodec
    private lateinit var adapter: SecureMeshPacketAdapter
    private lateinit var reassembler: SecureEnvelopeReassembler

    @Before
    fun setUp() {
        codec = SecureMeshEnvelopeCodec(keyProvider)
        adapter = SecureMeshPacketAdapter(codec, keyProvider)
        reassembler = SecureEnvelopeReassembler()
    }

    @Test
    fun `end to end pack fragment reassemble unpack at minimum MTU 23`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Avalanche Hazard",
            content = "3 hikers trapped in crevasse. Urgent hypothermia risk.",
            latitude = 45.8326,
            longitude = 6.8652,
            timestamp = System.currentTimeMillis(),
            hopCount = 0,
            maxHops = 7
        )

        // 1. Pack domain packet to encrypted envelope
        val envelope = adapter.packToEnvelope(originalSos, testKeyId)
        val wireBytes = codec.encode(envelope)

        // 2. Fragment at minimum standard MTU (23 bytes, usable write = 20 bytes)
        val mtu = 23
        val maxPayloadPerWrite = mtu - 3 // 20 bytes
        val fragments = SecureEnvelopeFragmenter.fragment(wireBytes, maxPayloadPerWrite, transferId = 1001)
        assertTrue("At MTU 23, a complete envelope must require dozens of fragments", fragments.size > 20)

        // 3. Reassemble fragments in shuffled order (simulating asynchronous BLE GATT writes)
        val shuffled = fragments.shuffled(SecureRandom())
        var reassembledBytes: ByteArray? = null
        for (fragment in shuffled) {
            val chunkWire = fragment.toByteArray()
            reassembledBytes = reassembler.addFragmentBytes("NODE-A-BLE-ADDR", chunkWire)
        }

        assertNotNull("Reassembly must complete after all fragments are delivered", reassembledBytes)

        // 4. Decode envelope and decrypt
        val decodedEnvelope = codec.decode(reassembledBytes!!)
        val result = adapter.unpackEnvelope(decodedEnvelope)
        assertTrue("Decryption must succeed after reassembly", result is EnvelopeProcessingResult.Success)

        val decryptedEnvelope = (result as EnvelopeProcessingResult.Success).decrypted
        val reconstructedPacket = adapter.parseDecryptedPacket(decryptedEnvelope)

        assertEquals(originalSos.messageId, reconstructedPacket.messageId)
        assertEquals(originalSos.originNodeId, reconstructedPacket.originNodeId)
        assertEquals(originalSos.priority, reconstructedPacket.priority)
        assertEquals(originalSos.title, reconstructedPacket.title)
        assertEquals(originalSos.content, reconstructedPacket.content)
        assertEquals(originalSos.latitude!!, reconstructedPacket.latitude!!, 0.0001)
        assertEquals(originalSos.longitude!!, reconstructedPacket.longitude!!, 0.0001)
    }

    @Test
    fun `end to end pack fragment reassemble unpack at high MTU 512`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-B",
            senderNodeId = "RQM-NODE-B",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Forest Fire Approaching",
            content = "Evacuating station 4",
            latitude = 34.05,
            longitude = -118.25,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapter.packToEnvelope(originalSos, testKeyId)
        val wireBytes = codec.encode(envelope)

        // MTU 512 (usable write = 509 bytes) -> Envelope fits in 1 fragment!
        val mtu = 512
        val maxPayloadPerWrite = mtu - 3 // 509 bytes
        val fragments = SecureEnvelopeFragmenter.fragment(wireBytes, maxPayloadPerWrite, transferId = 2002)
        assertEquals(1, fragments.size)

        val reassembledBytes = reassembler.addFragmentBytes("NODE-B-BLE-ADDR", fragments[0].toByteArray())
        assertNotNull(reassembledBytes)

        val decodedEnvelope = codec.decode(reassembledBytes!!)
        val result = adapter.unpackEnvelope(decodedEnvelope)
        assertTrue(result is EnvelopeProcessingResult.Success)

        val packet = adapter.parseDecryptedPacket((result as EnvelopeProcessingResult.Success).decrypted)
        assertEquals("Forest Fire Approaching", packet.title)
    }
}
