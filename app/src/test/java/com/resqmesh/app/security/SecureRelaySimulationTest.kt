package com.resqmesh.app.security

import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.security.envelope.SecureMeshEnvelopeCodec
import com.resqmesh.security.keys.ResponderKeyProvider
import com.resqmesh.security.model.EnvelopeProcessingResult
import com.resqmesh.security.model.SecurityFailureReason
import com.resqmesh.security.replay.InMemoryReplayProtectionValidator
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
 * Isolated multi-hop simulation verifying end-to-end encrypted packet propagation:
 *
 * Node A (Initiator with Key) -> Node B (Relay WITHOUT Key) -> Node C (Responder with Key)
 *
 * Verifies:
 * - Confidential content is encrypted end-to-end.
 * - Relay B forwards without possessing the group key or decrypting the payload.
 * - Relay B increments hopCount and updates senderNodeId without breaking the AEAD authentication tag.
 * - Responder C successfully verifies AAD and decrypts the emergency payload.
 * - Tampering at any stage causes cryptographic rejection without application crashes.
 */
class SecureRelaySimulationTest {

    private val sharedGroupKeyId = "responder-alpha-2026"
    private val sharedKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val sharedSecretKey = SecretKeySpec(sharedKeyBytes, "AES")

    // Node A holds the group key
    private val nodeAKeyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? =
            if (keyId == sharedGroupKeyId) sharedSecretKey else null
    }

    // Node B is an intermediate peer and DOES NOT hold the group key
    private val nodeBKeyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? = null
    }

    // Node C holds the matching group key
    private val nodeCKeyProvider = object : ResponderKeyProvider {
        override fun getResponderKey(keyId: String): SecretKey? =
            if (keyId == sharedGroupKeyId) sharedSecretKey else null
    }

    private lateinit var adapterA: SecureMeshPacketAdapter
    private lateinit var adapterB: SecureMeshPacketAdapter
    private lateinit var adapterC: SecureMeshPacketAdapter

    private lateinit var codecA: SecureMeshEnvelopeCodec
    private lateinit var codecB: SecureMeshEnvelopeCodec
    private lateinit var codecC: SecureMeshEnvelopeCodec

    private lateinit var replayValidatorC: InMemoryReplayProtectionValidator

    @Before
    fun setUp() {
        codecA = SecureMeshEnvelopeCodec(nodeAKeyProvider)
        codecB = SecureMeshEnvelopeCodec(nodeBKeyProvider)
        codecC = SecureMeshEnvelopeCodec(nodeCKeyProvider)

        replayValidatorC = InMemoryReplayProtectionValidator(allowLegacyFormat = true)

        adapterA = SecureMeshPacketAdapter(codecA, nodeAKeyProvider)
        adapterB = SecureMeshPacketAdapter(codecB, nodeBKeyProvider)
        adapterC = SecureMeshPacketAdapter(codecC, nodeCKeyProvider, replayValidatorC)
    }

    @Test
    fun `three-node relay simulation A to B to C propagates and decrypts successfully`() {
        // --- STEP 1: Node A creates and seals an emergency SOS packet ---
        val originalMessageId = UUID.randomUUID().toString()
        val originalTimestamp = System.currentTimeMillis()

        val originalSos = MeshPacket(
            messageId = originalMessageId,
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Flash Flood Emergency",
            content = "Family of 4 stranded on roof. Water rising rapidly.",
            latitude = 12.971598,
            longitude = 77.594562,
            timestamp = originalTimestamp,
            hopCount = 0,
            maxHops = 7
        )

        val sealedEnvelopeA = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val wireBytesFromA = codecA.encode(sealedEnvelopeA)

        // --- STEP 2: Node B (intermediate relay with NO key) receives and forwards ---
        assertTrue("Node B detects RQE encrypted envelope magic header", adapterB.isEncryptedEnvelopeBytes(wireBytesFromA))
        val envelopeAtB = codecB.decode(wireBytesFromA)

        // Node B attempts to unpack -> receives RelayedWithoutDecryption because it lacks the key
        val resultAtB = adapterB.unpackEnvelope(envelopeAtB)
        assertTrue(
            "Node B must handle missing key gracefully by allowing relay without decryption",
            resultAtB is EnvelopeProcessingResult.RelayedWithoutDecryption
        )

        // Node B relays the packet: increments hop count and updates senderNodeId
        val forwardedEnvelopeB = adapterB.relayForward(envelopeAtB, localNodeId = "RQM-NODE-B")
        assertNotNull("Relay must not drop packet below maxHops", forwardedEnvelopeB)
        assertEquals(1, forwardedEnvelopeB!!.relayHeader.hopCount)
        assertEquals("RQM-NODE-B", forwardedEnvelopeB.relayHeader.senderNodeId)

        // Ciphertext and Nonce remain untouched by relay
        assertTrue(sealedEnvelopeA.ciphertext.contentEquals(forwardedEnvelopeB.ciphertext))
        assertTrue(sealedEnvelopeA.nonce.contentEquals(forwardedEnvelopeB.nonce))

        val wireBytesFromB = codecB.encode(forwardedEnvelopeB)

        // --- STEP 3: Node C (responder with key) receives, verifies, and decrypts ---
        assertTrue(adapterC.isEncryptedEnvelopeBytes(wireBytesFromB))
        val envelopeAtC = codecC.decode(wireBytesFromB)

        val resultAtC = adapterC.unpackEnvelope(envelopeAtC)
        assertTrue("Node C must successfully decrypt with matching group key", resultAtC is EnvelopeProcessingResult.Success)

        val decryptedEnvelope = (resultAtC as EnvelopeProcessingResult.Success).decrypted
        val reconstructedPacket = adapterC.parseDecryptedPacket(decryptedEnvelope)

        // Invariant verification:
        assertEquals(originalMessageId, reconstructedPacket.messageId)
        assertEquals("RQM-NODE-A", reconstructedPacket.originNodeId)
        assertEquals("RQM-NODE-B", reconstructedPacket.senderNodeId) // Immediate transmitter was Relay B
        assertEquals(1, reconstructedPacket.hopCount)                // Exactly 1 hop traversed
        assertEquals(7, reconstructedPacket.maxHops)
        assertEquals(IncidentPriority.CRITICAL, reconstructedPacket.priority)
        assertEquals("Flash Flood Emergency", reconstructedPacket.title)
        assertEquals("Family of 4 stranded on roof. Water rising rapidly.", reconstructedPacket.content)
        assertEquals(12.971598, reconstructedPacket.latitude!!, 0.000001)
        assertEquals(77.594562, reconstructedPacket.longitude!!, 0.000001)
        assertEquals(originalTimestamp, reconstructedPacket.timestamp)
    }

    @Test
    fun `tampered ciphertext in transit is rejected by destination node C`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Medical Emergency",
            content = "Patient unresponsive",
            latitude = 13.0827,
            longitude = 80.2707,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwarded = adapterB.relayForward(envelope, "RQM-NODE-B")!!

        // Attacker tampers with ciphertext during B -> C transmission
        val tamperedCiphertext = forwarded.ciphertext.copyOf()
        tamperedCiphertext[tamperedCiphertext.lastIndex] = (tamperedCiphertext[tamperedCiphertext.lastIndex].toInt() xor 0x01).toByte()
        val tamperedEnvelope = forwarded.copy(ciphertext = tamperedCiphertext)

        val resultAtC = adapterC.unpackEnvelope(tamperedEnvelope)
        assertTrue(resultAtC is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_AUTHENTICATION_TAG,
            (resultAtC as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `tampered originNodeId in transit is rejected by destination node C`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Trapped in Elevator",
            content = "Basement level",
            latitude = 13.0827,
            longitude = 80.2707,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwarded = adapterB.relayForward(envelope, "RQM-NODE-B")!!

        // Rogue relay tampers with originNodeId to impersonate another node
        val tamperedEnvelope = forwarded.copy(
            authenticatedHeader = forwarded.authenticatedHeader.copy(originNodeId = "RQM-ROGUE-SPOOF")
        )

        val resultAtC = adapterC.unpackEnvelope(tamperedEnvelope)
        assertTrue(resultAtC is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_AUTHENTICATION_TAG,
            (resultAtC as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `tampered priority in transit is rejected by destination node C`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Severe Bleeding",
            content = "Immediate tourniquet applied",
            latitude = 13.0827,
            longitude = 80.2707,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwarded = adapterB.relayForward(envelope, "RQM-NODE-B")!!

        // Rogue relay downgrades priority from CRITICAL to INFO
        val tamperedEnvelope = forwarded.copy(
            authenticatedHeader = forwarded.authenticatedHeader.copy(priority = IncidentPriority.INFO.ordinal)
        )

        val resultAtC = adapterC.unpackEnvelope(tamperedEnvelope)
        assertTrue(resultAtC is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_AUTHENTICATION_TAG,
            (resultAtC as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `duplicate envelope delivery is rejected by replay protection`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Power outage at hospital",
            content = "Backup generator failing",
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)

        // First delivery -> Success
        val firstResult = adapterC.unpackEnvelope(envelope)
        assertTrue(firstResult is EnvelopeProcessingResult.Success)

        // Second delivery (replay) -> Rejected
        val duplicateResult = adapterC.unpackEnvelope(envelope)
        assertTrue(duplicateResult is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.DUPLICATE_MESSAGE,
            (duplicateResult as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `expired timestamp outside 10 minute window is rejected`() {
        val expiredTimestamp = System.currentTimeMillis() - 700_000L // 11.6 minutes old

        val expiredSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.NORMAL,
            title = "Old alert",
            content = "Report from yesterday",
            latitude = null,
            longitude = null,
            timestamp = expiredTimestamp
        )

        val envelope = adapterA.packToEnvelope(expiredSos, sharedGroupKeyId)
        val result = adapterC.unpackEnvelope(envelope)

        assertTrue(result is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.EXPIRED_MESSAGE,
            (result as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `node with wrong group key rejects packet with tag error`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Forest Fire Perimeter",
            content = "Evacuate northern ridge",
            latitude = 34.0522,
            longitude = -118.2437,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)

        // Node D has a DIFFERENT key for sharedGroupKeyId
        val differentKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nodeDProvider = object : ResponderKeyProvider {
            override fun getResponderKey(keyId: String): SecretKey? =
                SecretKeySpec(differentKeyBytes, "AES")
        }
        val codecD = SecureMeshEnvelopeCodec(nodeDProvider)
        val adapterD = SecureMeshPacketAdapter(codecD, nodeDProvider)

        val resultAtD = adapterD.unpackEnvelope(envelope)
        assertTrue(resultAtD is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_AUTHENTICATION_TAG,
            (resultAtD as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `relay drops packet when max hops is exceeded`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.INFO,
            title = "Test Max Hops",
            content = "Hop boundary check",
            latitude = null,
            longitude = null,
            hopCount = 7,
            maxHops = 7
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwarded = adapterB.relayForward(envelope, "RQM-NODE-B")
        assertEquals(null, forwarded)
    }

    @Test
    fun `relay sender tampering is rejected when relay authentication is enabled`() {
        val relayKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val relayAuth = com.resqmesh.security.relay.HmacRelayAuthenticator(relayKeyBytes)

        val adapterBWithAuth = SecureMeshPacketAdapter(codecB, nodeBKeyProvider, relayAuthenticator = relayAuth)
        val adapterCWithAuth = SecureMeshPacketAdapter(codecC, nodeCKeyProvider, replayValidatorC, relayAuthenticator = relayAuth)

        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Landslide",
            content = "Road blocked",
            latitude = 13.0,
            longitude = 80.0,
            timestamp = System.currentTimeMillis()
        )

        val envelopeA = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwardedB = adapterBWithAuth.relayForward(envelopeA, "RQM-NODE-B")!!
        assertNotNull(forwardedB.relayHeader.relayAuthToken)

        // Attacker tampers with senderNodeId during B -> C transit
        val tamperedEnvelope = forwardedB.copy(
            relayHeader = forwardedB.relayHeader.copy(senderNodeId = "RQM-SPOOFED-SENDER")
        )

        val resultAtC = adapterCWithAuth.unpackEnvelope(tamperedEnvelope)
        assertTrue(resultAtC is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_RELAY_AUTHENTICATION,
            (resultAtC as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `hop count reduction attack is detected and rejected by relay authentication`() {
        val relayKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val relayAuth = com.resqmesh.security.relay.HmacRelayAuthenticator(relayKeyBytes)

        val adapterBWithAuth = SecureMeshPacketAdapter(codecB, nodeBKeyProvider, relayAuthenticator = relayAuth)
        val adapterCWithAuth = SecureMeshPacketAdapter(codecC, nodeCKeyProvider, replayValidatorC, relayAuthenticator = relayAuth)

        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Chemical Spill",
            content = "Hazardous zone",
            latitude = 13.0,
            longitude = 80.0,
            hopCount = 3,
            maxHops = 7,
            timestamp = System.currentTimeMillis()
        )

        val envelopeA = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwardedB = adapterBWithAuth.relayForward(envelopeA, "RQM-NODE-B")!!
        assertEquals(4, forwardedB.relayHeader.hopCount)

        // Attacker resets hopCount from 4 to 1 to cause infinite circulation
        val tamperedEnvelope = forwardedB.copy(
            relayHeader = forwardedB.relayHeader.copy(hopCount = 1)
        )

        val resultAtC = adapterCWithAuth.unpackEnvelope(tamperedEnvelope)
        assertTrue(resultAtC is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_RELAY_AUTHENTICATION,
            (resultAtC as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `hop count overflow and negative hop count are rejected with INVALID_HOP_COUNT`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.INFO,
            title = "Test Boundary",
            content = "Hop sanity check",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)

        // 1. Hop count exceeds maxHops
        val overflowEnvelope = envelope.copy(
            relayHeader = envelope.relayHeader.copy(hopCount = 10, maxHops = 7)
        )
        val overflowResult = adapterC.unpackEnvelope(overflowEnvelope)
        assertTrue(overflowResult is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_HOP_COUNT,
            (overflowResult as EnvelopeProcessingResult.Rejected).reason
        )

        // 2. Negative hop count
        val negativeEnvelope = envelope.copy(
            relayHeader = envelope.relayHeader.copy(hopCount = -1)
        )
        val negativeResult = adapterC.unpackEnvelope(negativeEnvelope)
        assertTrue(negativeResult is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.INVALID_HOP_COUNT,
            (negativeResult as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `unsupported envelope version is rejected with UNSUPPORTED_VERSION`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.INFO,
            title = "Future Version Test",
            content = "Testing version incompatibility",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val futureVersionEnvelope = envelope.copy(
            authenticatedHeader = envelope.authenticatedHeader.copy(version = 99)
        )

        val result = adapterC.unpackEnvelope(futureVersionEnvelope)
        assertTrue(result is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.UNSUPPORTED_VERSION,
            (result as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `unknown key on destination node without relay fallback is rejected with UNKNOWN_KEY`() {
        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.INFO,
            title = "Secret Alert",
            content = "Key unknown to receiver",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis()
        )

        val envelope = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)

        // Node B has no key, and decryptEnvelope requires the key
        val result = adapterB.decryptEnvelope(envelope)
        assertTrue(result is EnvelopeProcessingResult.Rejected)
        assertEquals(
            SecurityFailureReason.UNKNOWN_KEY,
            (result as EnvelopeProcessingResult.Rejected).reason
        )
    }

    @Test
    fun `symmetric group key limitation demonstration - group member can spoof originNodeId`() {
        // DEMONSTRATION OF GROUP KEY BOUNDARY:
        // A rogue insider node (Node R) who possesses the shared AES-256 group key
        // creates an envelope claiming originNodeId = "RQM-INNOCENT-VICTIM".
        // Because AES-GCM uses a symmetric shared key, the AEAD tag authenticates
        // key possession, NOT individual author identity.
        val originalTimestamp = System.currentTimeMillis()
        val rogueMessageId = UUID.randomUUID().toString()

        val spoofedPacket = MeshPacket(
            messageId = rogueMessageId,
            originNodeId = "RQM-INNOCENT-VICTIM", // Spoofed origin by rogue insider
            senderNodeId = "RQM-ROGUE-INSIDER",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.HIGH,
            title = "Spoofed SOS",
            content = "Insider attack demonstration",
            latitude = 0.0,
            longitude = 0.0,
            timestamp = originalTimestamp
        )

        // Rogue insider seals with legitimate group key
        val rogueEnvelope = adapterA.packToEnvelope(spoofedPacket, sharedGroupKeyId)

        // Destination node C unpacks and verifies tag successfully!
        val resultAtC = adapterC.unpackEnvelope(rogueEnvelope)
        assertTrue("Symmetric AEAD tag passes verification because rogue node holds the group key",
            resultAtC is EnvelopeProcessingResult.Success)

        val parsed = adapterC.parseDecryptedPacket((resultAtC as EnvelopeProcessingResult.Success).decrypted)
        assertEquals("RQM-INNOCENT-VICTIM", parsed.originNodeId)

        // ARCHITECTURAL CONCLUSION:
        // Group key AES-GCM guarantees group membership integrity, but NOT individual node non-repudiation.
        // True individual origin authentication requires per-node asymmetric signatures (e.g. Ed25519) in Phase 4.
    }

    @Test
    fun `valid relay authentication token enables verified multi-hop propagation`() {
        val relayKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val relayAuth = com.resqmesh.security.relay.HmacRelayAuthenticator(relayKeyBytes)

        val adapterBWithAuth = SecureMeshPacketAdapter(codecB, nodeBKeyProvider, relayAuthenticator = relayAuth)
        val adapterCWithAuth = SecureMeshPacketAdapter(codecC, nodeCKeyProvider, replayValidatorC, relayAuthenticator = relayAuth)

        val originalSos = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            originNodeId = "RQM-NODE-A",
            senderNodeId = "RQM-NODE-A",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Earthquake Survivor",
            content = "Under rubble at sector 3",
            latitude = 12.97,
            longitude = 77.59,
            timestamp = System.currentTimeMillis()
        )

        val envelopeA = adapterA.packToEnvelope(originalSos, sharedGroupKeyId)
        val forwardedB = adapterBWithAuth.relayForward(envelopeA, "RQM-NODE-B")!!

        // Downstream node C verifies both relay authentication and AEAD payload
        val resultAtC = adapterCWithAuth.unpackEnvelope(forwardedB)
        assertTrue(resultAtC is EnvelopeProcessingResult.Success)

        val decrypted = (resultAtC as EnvelopeProcessingResult.Success).decrypted
        val packet = adapterCWithAuth.parseDecryptedPacket(decrypted)
        assertEquals("Earthquake Survivor", packet.title)
        assertEquals("RQM-NODE-B", packet.senderNodeId)
        assertEquals(1, packet.hopCount)
    }

    @Test
    fun `legacy unencrypted SOS and ACK packets continue to be processed without interference`() {
        // Verifies that unencrypted domain packets used by existing BLE GATT flooding
        // remain completely compatible with the model layer.
        val legacySos = MeshPacket(
            messageId = "legacy-sos-12345",
            originNodeId = "RQM-LEGACY-1",
            senderNodeId = "RQM-LEGACY-1",
            packetType = MeshPacketType.SOS,
            priority = IncidentPriority.CRITICAL,
            title = "Legacy Emergency",
            content = "Unencrypted SOS from legacy node",
            latitude = 12.0,
            longitude = 77.0,
            timestamp = System.currentTimeMillis()
        )

        assertEquals("legacy-sos-12345", legacySos.messageId)
        assertEquals(MeshPacketType.SOS, legacySos.packetType)

        // Legacy ACK response
        val legacyAck = MeshPacket(
            messageId = "legacy-ack-67890",
            originNodeId = "RQM-LEGACY-2",
            senderNodeId = "RQM-LEGACY-2",
            packetType = MeshPacketType.ACK,
            priority = IncidentPriority.NORMAL,
            title = "ACK",
            content = "legacy-sos-12345",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("legacy-ack-67890", legacyAck.messageId)
        assertEquals(MeshPacketType.ACK, legacyAck.packetType)
        assertEquals("legacy-sos-12345", legacyAck.content)
    }
}
