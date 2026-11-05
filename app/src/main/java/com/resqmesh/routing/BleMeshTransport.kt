package com.resqmesh.routing

import com.resqmesh.model.MessagePacket

/**
 * Interface contract defining the BLE mesh transport layer.
 * Implemented by Member 1's BLE GATT/Advertising subsystem.
 */
interface BleMeshTransport {

    /**
     * Transmits or broadcasts a packet across the physical BLE mesh link.
     *
     * @param packet The packet to transmit.
     * @return True if the packet was successfully accepted by the BLE radio layer.
     */
    suspend fun sendPacket(packet: MessagePacket): Boolean

    /**
     * Registers a listener to receive inbound packets captured by the BLE radio layer.
     */
    fun setPacketReceivedListener(listener: (MessagePacket) -> Unit)

    /**
     * Returns true if this device currently has active BLE mesh neighbors in range.
     */
    fun isConnected(): Boolean
}
