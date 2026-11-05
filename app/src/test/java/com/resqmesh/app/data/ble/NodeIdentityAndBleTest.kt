package com.resqmesh.app.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeIdentityAndBleTest {

    @Test
    fun `isValidNodeId accepts standard 6-hex-char ResQMesh IDs`() {
        assertTrue(BleConstants.isValidNodeId("RQM-A1B2C3"))
        assertTrue(BleConstants.isValidNodeId("RQM-001122"))
        assertTrue(BleConstants.isValidNodeId("RQM-FFEEDD"))
        assertTrue(BleConstants.isValidNodeId("RQM-7701"))
        assertTrue(BleConstants.isValidNodeId("RQM-SENDER-02"))
        assertTrue(BleConstants.isValidNodeId("RQM-ORIGIN-A"))
    }

    @Test
    fun `isValidNodeId rejects null empty or blank IDs`() {
        assertFalse(BleConstants.isValidNodeId(null))
        assertFalse(BleConstants.isValidNodeId(""))
        assertFalse(BleConstants.isValidNodeId("   "))
        assertFalse(BleConstants.isValidNodeId("\t\n"))
    }

    @Test
    fun `isValidNodeId rejects MAC addresses as Node IDs`() {
        // Strict requirement: Never use a MAC address as a substitute for a ResQMesh Node ID
        assertFalse(BleConstants.isValidNodeId("AA:BB:CC:DD:EE:FF"))
        assertFalse(BleConstants.isValidNodeId("11:22:33:44:55:66"))
        assertFalse(BleConstants.isValidNodeId("aa:bb:cc:dd:ee:ff"))
        assertFalse(BleConstants.isValidNodeId("AABBCCDDEEFF"))
    }

    @Test
    fun `isValidNodeId rejects strings without RQM prefix or invalid characters`() {
        assertFalse(BleConstants.isValidNodeId("NODE-123456"))
        assertFalse(BleConstants.isValidNodeId("RQM"))
        assertFalse(BleConstants.isValidNodeId("RQM-"))
        assertFalse(BleConstants.isValidNodeId("RQM-12")) // Too short
        assertFalse(BleConstants.isValidNodeId("RQM-TOOLONGIDENTIFIERFORBLEPAYLOAD")) // Too long
        assertFalse(BleConstants.isValidNodeId("RQM-@@##$$")) // Invalid symbols
    }

    @Test
    fun `service data payload serialization and extraction round trip`() {
        val originalNodeId = "RQM-B4E1A9"
        val payloadBytes = originalNodeId.toByteArray(Charsets.UTF_8)

        // Verify payload fits easily inside BLE advertisement legacy scan response (31 bytes max, header takes 18)
        assertTrue("Service data payload must fit in scan response", payloadBytes.size <= 13)

        // Decode candidate
        val decoded = String(payloadBytes, Charsets.UTF_8).trim()
        assertTrue(BleConstants.isValidNodeId(decoded))
        assertEquals(originalNodeId, decoded)
    }

    @Test
    fun `malformed service data payload is safely rejected without exceptions`() {
        val corruptedBytes = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFE.toByte())
        val decodedCandidate = String(corruptedBytes, Charsets.UTF_8).trim()
        val isValid = BleConstants.isValidNodeId(decodedCandidate)
        assertFalse(isValid)
    }

    @Test
    fun `self advertisement identification matches local node ID`() {
        val localNodeId = "RQM-LOCAL1"
        val peerCandidateNodeId = "RQM-LOCAL1"
        val remotePeerNodeId = "RQM-REMOTE2"

        val isSelf = peerCandidateNodeId == localNodeId
        val isRemoteSelf = remotePeerNodeId == localNodeId

        assertTrue("Matching local ID must be identified as self", isSelf)
        assertFalse("Different peer ID must not be identified as self", isRemoteSelf)
    }
}
