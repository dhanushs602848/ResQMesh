package com.resqmesh.app.data.ble

import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Architectural contract for Bluetooth Low Energy mesh discovery, advertising,
 * and inbound GATT server packet reception.
 */
interface BleManager {
    val discoveredNodes: StateFlow<List<MeshNode>>
    val bleStatus: StateFlow<BleServiceStatus>
    val localNodeId: String
    val inboundPackets: SharedFlow<MeshPacket>

    fun startMeshDiscovery()
    fun stopMeshDiscovery()
    fun refreshDiscovery()

    /**
     * Transmits a MeshPacket over BLE GATT to all currently discovered peers (one hop).
     * Returns delivery confirmations per target peer.
     */
    suspend fun dispatchMeshPacket(packet: MeshPacket): BleBroadcastSummary

    /**
     * Transmits a MeshPacket over BLE GATT to discovered peers, excluding specified peer addresses or node IDs.
     * Defaults to delegating to [dispatchMeshPacket(MeshPacket)].
     */
    suspend fun dispatchMeshPacket(
        packet: MeshPacket,
        excludePeerIds: Set<String>
    ): BleBroadcastSummary = dispatchMeshPacket(packet)
}
