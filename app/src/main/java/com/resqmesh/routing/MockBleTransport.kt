package com.resqmesh.routing

import com.resqmesh.model.MessagePacket

/**
 * In-memory test / preview implementation of [BleMeshTransport].
 * Can be connected with other [MockBleTransport] instances to simulate a multi-hop mesh network.
 */
class MockBleTransport(var isConnectedState: Boolean = true) : BleMeshTransport {

    private var packetListener: ((MessagePacket) -> Unit)? = null
    val sentPackets = mutableListOf<MessagePacket>()
    val connectedPeers = mutableListOf<MockBleTransport>()

    override suspend fun sendPacket(packet: MessagePacket): Boolean {
        if (!isConnectedState) return false
        sentPackets.add(packet)

        // Relay to all simulated connected peers in mesh range
        for (peer in connectedPeers) {
            peer.receiveFromRadio(packet)
        }
        return true
    }

    override fun setPacketReceivedListener(listener: (MessagePacket) -> Unit) {
        this.packetListener = listener
    }

    override fun isConnected(): Boolean = isConnectedState

    /**
     * Simulates packet arrival from the radio layer.
     */
    fun receiveFromRadio(packet: MessagePacket) {
        if (isConnectedState) {
            packetListener?.invoke(packet)
        }
    }

    fun linkPeer(peer: MockBleTransport) {
        if (!connectedPeers.contains(peer)) {
            connectedPeers.add(peer)
        }
        if (!peer.connectedPeers.contains(this)) {
            peer.connectedPeers.add(this)
        }
    }

    fun unlinkPeer(peer: MockBleTransport) {
        connectedPeers.remove(peer)
        peer.connectedPeers.remove(this)
    }

    fun clearHistory() {
        sentPackets.clear()
    }
}
