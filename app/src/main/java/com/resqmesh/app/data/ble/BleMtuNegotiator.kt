package com.resqmesh.app.data.ble

import android.bluetooth.BluetoothGatt
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Functional interface wrapping the GATT MTU request to enable unit testing without Android framework mocks.
 */
interface GattMtuRequester {
    val deviceAddress: String
    fun requestMtu(mtu: Int): Boolean
}

/**
 * Handles safe MTU negotiation and peer-specific payload size calculations for BLE GATT connections.
 *
 * Implements strict Bluetooth Core Specification ATT rules:
 * - Default ATT MTU is 23 bytes (3 bytes ATT header: opcode + attribute handle).
 * - Usable payload per write is always (negotiatedMTU - 3).
 * - Maximum requested MTU is typically 512 or 517 bytes.
 * - Handles asynchronous callback delays, failures, disconnections, and per-peer variations.
 */
@Singleton
open class BleMtuNegotiator @Inject constructor() {

    var preferredMtu: Int = DEFAULT_PREFERRED_MTU
    var negotiationTimeoutMs: Long = DEFAULT_NEGOTIATION_TIMEOUT_MS

    // Tracks negotiated MTU per peer device address
    private val peerMtuMap = ConcurrentHashMap<String, Int>()

    /**
     * Negotiates MTU with a connected [BluetoothGatt] peer.
     *
     * @param gatt Active connected BluetoothGatt client.
     * @param mtuDeferred CompletableDeferred triggered by [android.bluetooth.BluetoothGattCallback.onMtuChanged].
     * @return The actual negotiated MTU, or [DEFAULT_ATT_MTU] (23) if negotiation fails or times out.
     */
    suspend fun negotiateMtu(
        gatt: BluetoothGatt,
        mtuDeferred: CompletableDeferred<Int>
    ): Int {
        val requester = object : GattMtuRequester {
            override val deviceAddress: String = try { gatt.device?.address } catch (_: Exception) { null } ?: "UNKNOWN"
            override fun requestMtu(mtu: Int): Boolean {
                return try {
                    gatt.requestMtu(mtu)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception calling gatt.requestMtu: ${e.message}")
                    false
                }
            }
        }
        return negotiateMtu(requester, mtuDeferred)
    }

    /**
     * Testable overload accepting [GattMtuRequester].
     */
    suspend fun negotiateMtu(
        requester: GattMtuRequester,
        mtuDeferred: CompletableDeferred<Int>
    ): Int {
        val address = requester.deviceAddress
        val requestedMtu = preferredMtu.coerceIn(DEFAULT_ATT_MTU, MAX_ATT_MTU)
        Log.d(TAG, "Requesting MTU $requestedMtu for peer $address")

        val requested = try {
            requester.requestMtu(requestedMtu)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to invoke requestMtu($requestedMtu) for $address: ${e.message}")
            false
        }

        if (!requested) {
            Log.w(TAG, "requestMtu returned false for $address, falling back to default $DEFAULT_ATT_MTU")
            recordPeerMtu(address, DEFAULT_ATT_MTU)
            return DEFAULT_ATT_MTU
        }

        val actualMtu = try {
            withTimeoutOrNull(negotiationTimeoutMs) {
                mtuDeferred.await()
            } ?: run {
                Log.w(TAG, "MTU negotiation timed out after ${negotiationTimeoutMs}ms for $address, falling back to $DEFAULT_ATT_MTU")
                DEFAULT_ATT_MTU
            }
        } catch (e: Exception) {
            Log.w(TAG, "MTU negotiation failed exceptionally for $address: ${e.message}, falling back to $DEFAULT_ATT_MTU")
            DEFAULT_ATT_MTU
        }

        val finalMtu = actualMtu.coerceIn(DEFAULT_ATT_MTU, MAX_ATT_MTU)
        Log.i(TAG, "Negotiated MTU for $address: $finalMtu (usable payload: ${calculateUsablePayload(finalMtu)} bytes)")
        recordPeerMtu(address, finalMtu)
        return finalMtu
    }

    /**
     * Records an MTU negotiated for a peer device.
     */
    fun recordPeerMtu(address: String, mtu: Int) {
        val safeMtu = mtu.coerceIn(DEFAULT_ATT_MTU, MAX_ATT_MTU)
        peerMtuMap[address.trim().uppercase()] = safeMtu
    }

    /**
     * Returns the negotiated MTU for [address], or [DEFAULT_ATT_MTU] (23) if unnegotiated.
     */
    fun getPeerMtu(address: String): Int {
        return peerMtuMap[address.trim().uppercase()] ?: DEFAULT_ATT_MTU
    }

    /**
     * Calculates the maximum usable ATT payload size in bytes for [address].
     * Always accounts for the 3-byte ATT header.
     */
    fun getUsablePayloadSize(address: String): Int {
        val mtu = getPeerMtu(address)
        return calculateUsablePayload(mtu)
    }

    /**
     * Calculates usable payload size for an explicit MTU value: (mtu - 3) coerced to at least 20 bytes.
     */
    fun calculateUsablePayload(mtu: Int): Int {
        val effectiveMtu = mtu.coerceIn(DEFAULT_ATT_MTU, MAX_ATT_MTU)
        return (effectiveMtu - ATT_HEADER_BYTES).coerceAtLeast(MIN_USABLE_PAYLOAD)
    }

    /**
     * Evicts peer MTU entry upon device disconnection.
     */
    fun removePeer(address: String) {
        peerMtuMap.remove(address.trim().uppercase())
    }

    /**
     * Clears all cached peer MTU values.
     */
    fun clear() {
        peerMtuMap.clear()
    }

    companion object {
        private const val TAG = "BleMtuNegotiator"
        const val DEFAULT_ATT_MTU = 23
        const val ATT_HEADER_BYTES = 3
        const val MIN_USABLE_PAYLOAD = 20
        const val DEFAULT_PREFERRED_MTU = 512
        const val MAX_ATT_MTU = 517
        const val DEFAULT_NEGOTIATION_TIMEOUT_MS = 2000L
    }
}
