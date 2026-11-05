package com.resqmesh.app.data.ble

/**
 * Result of transmitting an outbound MeshPacket to an individual discovered peer over BLE GATT.
 */
data class BleDispatchResult(
    val peerAddress: String,
    val peerName: String,
    val isDelivered: Boolean,
    val fragmentsSent: Int = 0,
    val error: String? = null
)

/**
 * High-level summary of an outbound one-hop BLE broadcast attempt targeting all nearby peers.
 */
data class BleBroadcastSummary(
    val messageId: String,
    val totalPeersAttempted: Int,
    val successfulDeliveries: Int,
    val results: List<BleDispatchResult>
) {
    val isDeliveredToAnyPeer: Boolean
        get() = successfulDeliveries > 0
}
