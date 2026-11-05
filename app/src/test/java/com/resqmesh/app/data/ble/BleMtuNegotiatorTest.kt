package com.resqmesh.app.data.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BleMtuNegotiatorTest {

    private lateinit var negotiator: BleMtuNegotiator

    @Before
    fun setUp() {
        negotiator = BleMtuNegotiator()
    }

    private class FakeGattRequester(
        override val deviceAddress: String,
        var requestMtuResult: Boolean = true
    ) : GattMtuRequester {
        var lastRequestedMtu: Int = -1
        override fun requestMtu(mtu: Int): Boolean {
            lastRequestedMtu = mtu
            return requestMtuResult
        }
    }

    @Test
    fun `default unnegotiated MTU is 23 with usable payload 20`() {
        val peer = "AA:BB:CC:DD:EE:01"
        assertEquals(23, negotiator.getPeerMtu(peer))
        assertEquals(20, negotiator.getUsablePayloadSize(peer))
    }

    @Test
    fun `usable payload calculation accounts for 3-byte ATT header across range`() {
        // MTU 23 -> 20 bytes
        assertEquals(20, negotiator.calculateUsablePayload(23))
        // MTU 64 -> 61 bytes
        assertEquals(61, negotiator.calculateUsablePayload(64))
        // MTU 247 -> 244 bytes
        assertEquals(244, negotiator.calculateUsablePayload(247))
        // MTU 512 -> 509 bytes
        assertEquals(509, negotiator.calculateUsablePayload(512))
        // MTU 517 -> 514 bytes
        assertEquals(514, negotiator.calculateUsablePayload(517))
    }

    @Test
    fun `MTU values clamped within valid range 23 to 517`() {
        assertEquals(20, negotiator.calculateUsablePayload(10)) // Clamped to 23
        assertEquals(514, negotiator.calculateUsablePayload(600)) // Clamped to 517
    }

    @Test
    fun `recording and retrieving peer-specific MTU`() {
        val peer1 = "AA:BB:CC:DD:EE:01"
        val peer2 = "AA:BB:CC:DD:EE:02"

        negotiator.recordPeerMtu(peer1, 247)
        negotiator.recordPeerMtu(peer2, 512)

        assertEquals(247, negotiator.getPeerMtu(peer1))
        assertEquals(244, negotiator.getUsablePayloadSize(peer1))

        assertEquals(512, negotiator.getPeerMtu(peer2))
        assertEquals(509, negotiator.getUsablePayloadSize(peer2))

        // Peer 3 was never recorded -> falls back to default 23
        assertEquals(23, negotiator.getPeerMtu("AA:BB:CC:DD:EE:03"))
        assertEquals(20, negotiator.getUsablePayloadSize("AA:BB:CC:DD:EE:03"))
    }

    @Test
    fun `peer removal evicts cached MTU`() {
        val peer = "AA:BB:CC:DD:EE:01"
        negotiator.recordPeerMtu(peer, 512)
        assertEquals(512, negotiator.getPeerMtu(peer))

        negotiator.removePeer(peer)
        assertEquals(23, negotiator.getPeerMtu(peer))
    }

    @Test
    fun `clear removes all cached peer MTUs`() {
        negotiator.recordPeerMtu("PEER-1", 247)
        negotiator.recordPeerMtu("PEER-2", 512)

        negotiator.clear()
        assertEquals(23, negotiator.getPeerMtu("PEER-1"))
        assertEquals(23, negotiator.getPeerMtu("PEER-2"))
    }

    @Test
    fun `negotiateMtu succeeds and returns negotiated value`() = runBlocking {
        val fakeRequester = FakeGattRequester("AA:BB:CC:DD:EE:FF", requestMtuResult = true)
        val deferred = CompletableDeferred<Int>()
        deferred.complete(247) // Peer responds with 247

        val actualMtu = negotiator.negotiateMtu(fakeRequester, deferred)
        assertEquals(247, actualMtu)
        assertEquals(512, fakeRequester.lastRequestedMtu)
        assertEquals(247, negotiator.getPeerMtu("AA:BB:CC:DD:EE:FF"))
        assertEquals(244, negotiator.getUsablePayloadSize("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `negotiateMtu returns default 23 when requestMtu returns false`() = runBlocking {
        val fakeRequester = FakeGattRequester("AA:BB:CC:DD:EE:FF", requestMtuResult = false)
        val deferred = CompletableDeferred<Int>()

        val actualMtu = negotiator.negotiateMtu(fakeRequester, deferred)
        assertEquals(23, actualMtu)
        assertEquals(23, negotiator.getPeerMtu("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `negotiateMtu returns default 23 when callback times out`() = runBlocking {
        val fakeRequester = FakeGattRequester("AA:BB:CC:DD:EE:FF", requestMtuResult = true)
        val fastNegotiator = BleMtuNegotiator()
        fastNegotiator.negotiationTimeoutMs = 50L // 50ms timeout for test

        val deferred = CompletableDeferred<Int>()
        // Do not complete deferred -> will time out

        val actualMtu = fastNegotiator.negotiateMtu(fakeRequester, deferred)
        assertEquals(23, actualMtu)
        assertEquals(23, fastNegotiator.getPeerMtu("AA:BB:CC:DD:EE:FF"))
    }
}
