package com.resqmesh.security.transport

import com.resqmesh.security.envelope.AuthenticatedEnvelopeHeader
import com.resqmesh.security.envelope.MutableRelayHeader
import com.resqmesh.security.envelope.SecureMeshEnvelopeCodec
import com.resqmesh.security.keys.ResponderKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SecureEnvelopeFragmentationTest {

    private lateinit var reassembler: SecureEnvelopeReassembler
    private val random = SecureRandom()

    private val testKeyId = "responder-alpha-key"
    private val testKey = SecretKeySpec(ByteArray(32).also { random.nextBytes(it) }, "AES")
    private val keyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? = if (keyId == testKeyId) testKey else null
    }
    private lateinit var codec: SecureMeshEnvelopeCodec

    @Before
    fun setUp() {
        reassembler = SecureEnvelopeReassembler(reassemblyTimeoutMs = 10_000L, maxConcurrentTransfers = 10)
        codec = SecureMeshEnvelopeCodec(keyProvider)
    }

    private fun generateSampleData(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    @Test
    fun `small envelope fitting in single fragment round trip`() {
        val originalData = "SOS: Minor emergency at sector 1".toByteArray(Charsets.UTF_8)
        val mtu = 100
        val maxPayloadPerWrite = mtu - 3 // 97 bytes

        val fragments = SecureEnvelopeFragmenter.fragment(originalData, maxPayloadPerWrite, transferId = 101)
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].fragmentIndex)
        assertEquals(1, fragments[0].totalFragments)
        assertEquals(originalData.size, fragments[0].totalMessageLength)

        val wireBytes = fragments[0].toByteArray()
        val parsedFragment = SecureEnvelopeFragment.fromByteArray(wireBytes)
        assertEquals(fragments[0], parsedFragment)

        val reassembled = reassembler.addFragment("PEER-A", parsedFragment)
        assertNotNull(reassembled)
        assertArrayEquals(originalData, reassembled)
    }

    @Test
    fun `large envelope split across multiple fragments reassembles in order`() {
        val originalData = generateSampleData(2500)
        val mtu = 100
        val maxPayloadPerWrite = mtu - 3 // 97 bytes (chunk payload = 97 - 18 = 79 bytes)

        val fragments = SecureEnvelopeFragmenter.fragment(originalData, maxPayloadPerWrite, transferId = 202)
        assertTrue("Must require multiple fragments", fragments.size > 20)

        var result: ByteArray? = null
        for (i in fragments.indices) {
            val wireBytes = fragments[i].toByteArray()
            val parsed = SecureEnvelopeFragment.fromByteArray(wireBytes)
            result = reassembler.addFragment("PEER-A", parsed)
            if (i < fragments.lastIndex) {
                assertNull("Must return null while incomplete", result)
            }
        }

        assertNotNull("Must return reassembled bytes on final fragment", result)
        assertArrayEquals(originalData, result)
    }

    @Test
    fun `reassembly in reverse order succeeds`() {
        val originalData = generateSampleData(1200)
        val mtu = 64
        val maxPayloadPerWrite = mtu - 3

        val fragments = SecureEnvelopeFragmenter.fragment(originalData, maxPayloadPerWrite, transferId = 303)

        var result: ByteArray? = null
        for (i in fragments.indices.reversed()) {
            val wireBytes = fragments[i].toByteArray()
            val parsed = SecureEnvelopeFragment.fromByteArray(wireBytes)
            result = reassembler.addFragment("PEER-A", parsed)
            if (i > 0) {
                assertNull(result)
            }
        }

        assertNotNull(result)
        assertArrayEquals(originalData, result)
    }

    @Test
    fun `reassembly in random shuffled order succeeds`() {
        val originalData = generateSampleData(1800)
        val mtu = 80
        val maxPayloadPerWrite = mtu - 3

        val fragments = SecureEnvelopeFragmenter.fragment(originalData, maxPayloadPerWrite, transferId = 404)
        val shuffled = fragments.shuffled(random)

        var result: ByteArray? = null
        for (i in shuffled.indices) {
            val wireBytes = shuffled[i].toByteArray()
            val parsed = SecureEnvelopeFragment.fromByteArray(wireBytes)
            result = reassembler.addFragment("PEER-A", parsed)
            if (i < shuffled.lastIndex) {
                assertNull(result)
            }
        }

        assertNotNull(result)
        assertArrayEquals(originalData, result)
    }

    @Test
    fun `duplicate fragment delivery is handled gracefully and ignored`() {
        val originalData = generateSampleData(600)
        val mtu = 100
        val maxPayloadPerWrite = mtu - 3

        val fragments = SecureEnvelopeFragmenter.fragment(originalData, maxPayloadPerWrite, transferId = 505)

        // Deliver fragment 0, then duplicate fragment 0
        assertNull(reassembler.addFragment("PEER-A", fragments[0]))
        assertNull(reassembler.addFragment("PEER-A", fragments[0])) // duplicate ignored

        // Deliver remaining fragments
        var result: ByteArray? = null
        for (i in 1 until fragments.size) {
            result = reassembler.addFragment("PEER-A", fragments[i])
        }

        assertNotNull(result)
        assertArrayEquals(originalData, result)
    }

    @Test
    fun `corrupted fragment payload fails CRC32 verification immediately`() {
        val originalData = generateSampleData(300)
        val fragments = SecureEnvelopeFragmenter.fragment(originalData, 100, transferId = 606)

        val wireBytes = fragments[0].toByteArray()
        // Corrupt a byte in the payload area
        wireBytes[wireBytes.lastIndex] = (wireBytes[wireBytes.lastIndex].toInt() xor 0x01).toByte()

        assertThrows(InvalidFragmentException::class.java) {
            SecureEnvelopeFragment.fromByteArray(wireBytes)
        }
    }

    @Test
    fun `corrupted fragment checksum fails verification`() {
        val originalData = generateSampleData(100)
        val fragment = SecureEnvelopeFragment(
            transferId = 707,
            fragmentIndex = 0,
            totalFragments = 1,
            totalMessageLength = originalData.size,
            payload = originalData,
            checksum = 12345678L // Invalid checksum
        )

        val wireBytes = fragment.toByteArray()
        assertThrows(InvalidFragmentException::class.java) {
            SecureEnvelopeFragment.fromByteArray(wireBytes)
        }
    }

    @Test
    fun `invalid fragment index or count rejected`() {
        // fragmentIndex >= totalFragments
        assertThrows(InvalidFragmentException::class.java) {
            SecureEnvelopeFragment(
                transferId = 1,
                fragmentIndex = 5,
                totalFragments = 5,
                totalMessageLength = 100,
                payload = ByteArray(20)
            )
        }

        // totalFragments <= 0
        assertThrows(InvalidFragmentException::class.java) {
            SecureEnvelopeFragment(
                transferId = 1,
                fragmentIndex = 0,
                totalFragments = 0,
                totalMessageLength = 100,
                payload = ByteArray(20)
            )
        }

        // fragmentIndex < 0
        assertThrows(InvalidFragmentException::class.java) {
            SecureEnvelopeFragment(
                transferId = 1,
                fragmentIndex = -1,
                totalFragments = 3,
                totalMessageLength = 100,
                payload = ByteArray(20)
            )
        }
    }

    @Test
    fun `fragment metadata mismatch across chunks rejected by reassembler`() {
        val frag1 = SecureEnvelopeFragment(
            transferId = 808,
            fragmentIndex = 0,
            totalFragments = 3,
            totalMessageLength = 150,
            payload = ByteArray(50)
        )
        assertNull(reassembler.addFragment("PEER-A", frag1))

        // Contradicting chunk with different totalFragments
        val frag2Bad = SecureEnvelopeFragment(
            transferId = 808,
            fragmentIndex = 1,
            totalFragments = 5, // Mismatch!
            totalMessageLength = 150,
            payload = ByteArray(50)
        )
        assertThrows(InvalidFragmentException::class.java) {
            reassembler.addFragment("PEER-A", frag2Bad)
        }
    }

    @Test
    fun `oversized envelope exceeding maximum size rejected by fragmenter`() {
        val hugeData = ByteArray(SecureEnvelopeFragment.MAX_MESSAGE_LENGTH + 1)
        assertThrows(IllegalArgumentException::class.java) {
            SecureEnvelopeFragmenter.fragment(hugeData, 100)
        }
    }

    @Test
    fun `different MTU values handled accurately`() {
        val testPayload = generateSampleData(800)
        val testMtus = listOf(23, 50, 100, 247, 512, 517)

        for (mtu in testMtus) {
            val maxPayloadPerWrite = mtu - 3
            val fragments = SecureEnvelopeFragmenter.fragment(testPayload, maxPayloadPerWrite, transferId = mtu)

            var reassembled: ByteArray? = null
            for (fragment in fragments) {
                val wireBytes = fragment.toByteArray()
                assertTrue(
                    "Each wire fragment must fit within usable write size (wire: ${wireBytes.size}, max: $maxPayloadPerWrite)",
                    wireBytes.size <= maxPayloadPerWrite
                )
                val parsed = SecureEnvelopeFragment.fromByteArray(wireBytes)
                reassembled = reassembler.addFragment("PEER-MTU-$mtu", parsed)
            }

            assertNotNull("Reassembly must succeed for MTU $mtu", reassembled)
            assertArrayEquals("Data must match exactly for MTU $mtu", testPayload, reassembled)
        }
    }

    @Test
    fun `missing fragment timeout evicts incomplete transfers`() {
        val shortTimeoutReassembler = SecureEnvelopeReassembler(reassemblyTimeoutMs = 50L)
        val data = generateSampleData(200)
        val fragments = SecureEnvelopeFragmenter.fragment(data, 50, transferId = 999)

        // Send only first fragment
        assertNull(shortTimeoutReassembler.addFragment("PEER-A", fragments[0]))
        assertEquals(1, shortTimeoutReassembler.getActiveTransferCount())

        // Sleep past timeout
        Thread.sleep(60L)

        // Adding a new fragment triggers cleanup of expired sessions
        assertEquals(0, shortTimeoutReassembler.getActiveTransferCount())
    }

    @Test
    fun `transfers are isolated by peer address`() {
        val dataA = "Emergency Alert from Phone A".toByteArray(Charsets.UTF_8)
        val dataB = "Relay Telemetry from Phone B".toByteArray(Charsets.UTF_8)

        // Same transferId, but distinct peers
        val fragsA = SecureEnvelopeFragmenter.fragment(dataA, 30, transferId = 42)
        val fragsB = SecureEnvelopeFragmenter.fragment(dataB, 30, transferId = 42)

        // Deliver chunk 0 of A
        assertNull(reassembler.addFragment("PEER-A", fragsA[0]))
        // Deliver chunk 0 of B
        assertNull(reassembler.addFragment("PEER-B", fragsB[0]))

        assertEquals(2, reassembler.getActiveTransferCount())

        // Deliver remaining chunks of A
        var resultA: ByteArray? = null
        for (i in 1 until fragsA.size) {
            resultA = reassembler.addFragment("PEER-A", fragsA[i])
        }
        assertNotNull(resultA)
        assertArrayEquals(dataA, resultA)

        // Deliver remaining chunks of B
        var resultB: ByteArray? = null
        for (i in 1 until fragsB.size) {
            resultB = reassembler.addFragment("PEER-B", fragsB[i])
        }
        assertNotNull(resultB)
        assertArrayEquals(dataB, resultB)
    }

    @Test
    fun `exact byte preservation with binary encrypted envelope codec`() {
        val authHeader = AuthenticatedEnvelopeHeader(
            version = 1,
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-A1B2",
            keyId = testKeyId,
            timestamp = System.currentTimeMillis(),
            priority = 4,
            payloadType = 1
        )
        val relayHeader = MutableRelayHeader(
            senderNodeId = "RQM-A1B2",
            hopCount = 0,
            maxHops = 7
        )
        val plaintext = "CONFIDENTIAL: Critical flood rescue required at 12.9716, 77.5946".toByteArray(Charsets.UTF_8)

        // 1. Seal envelope
        val envelope = codec.seal(authHeader, relayHeader, plaintext)
        val wireEnvelopeBytes = codec.encode(envelope)

        // 2. Fragment at conservative MTU 64 (usable 61 bytes)
        val fragments = SecureEnvelopeFragmenter.fragment(wireEnvelopeBytes, maxPayloadPerWrite = 61, transferId = 1234)
        assertTrue(fragments.size > 1)

        // 3. Reassemble fragments
        var reassembledWireBytes: ByteArray? = null
        for (fragment in fragments) {
            reassembledWireBytes = reassembler.addFragment("PEER-GATEWAY", fragment)
        }

        assertNotNull(reassembledWireBytes)
        assertArrayEquals("Reassembled wire bytes must match sealed envelope bytes", wireEnvelopeBytes, reassembledWireBytes)

        // 4. Decode envelope and decrypt payload
        val decodedEnvelope = codec.decode(reassembledWireBytes!!)
        val decrypted = codec.open(decodedEnvelope)

        assertEquals(authHeader, decrypted.authenticatedHeader)
        assertEquals(relayHeader, decrypted.relayHeader)
        assertArrayEquals(plaintext, decrypted.plaintext)
    }
}
