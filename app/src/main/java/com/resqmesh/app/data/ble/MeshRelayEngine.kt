package com.resqmesh.app.data.ble

import android.util.Log
import com.resqmesh.app.data.model.DuplicateDetector
import com.resqmesh.app.data.model.InMemoryDuplicateDetector
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketType
import com.resqmesh.app.data.model.NodeStatus
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResQMesh-Relay"

/**
 * Categorical decision made by the [MeshRelayEngine] when evaluating an incoming packet.
 */
enum class RelayDecision {
    /** Packet successfully forwarded to one or more nearby peers. */
    RELAYED,

    /** ACK packet reached its destination originator node; forwarding halts. */
    DESTINATION_REACHED,

    /** Packet was already forwarded/processed by this node; dropped to prevent broadcast storms. */
    DROPPED_DUPLICATE,

    /** Packet hop count reached or exceeded maxHops; dropped to bound packet lifetime. */
    DROPPED_MAX_HOPS_EXCEEDED,

    /** Packet originated at this local node and looped back; dropped to prevent self-echo. */
    DROPPED_ORIGINATED_BY_SELF,

    /** Packet is not an SOS or supported packet type; not relayed. */
    DROPPED_NON_SOS,

    /** Packet failed invariant validation checks. */
    DROPPED_MALFORMED,

    /** Packet is eligible for relay, but no candidate online peers were found in range. */
    SKIPPED_NO_PEERS,

    /** Relay transmission was attempted, but delivery to all candidate peers failed. */
    RELAY_FAILED
}

/**
 * Result returned by [MeshRelayEngine.processAndRelay].
 */
data class RelayResult(
    val messageId: String,
    val decision: RelayDecision,
    val relayedPacket: MeshPacket? = null,
    val broadcastSummary: BleBroadcastSummary? = null,
    val message: String
)

/**
 * Contract for evaluating, validating, deduplicating, and forwarding incoming
 * multi-hop mesh packets across Bluetooth Low Energy peers.
 */
interface MeshRelayEngine {
    /**
     * Evaluates an incoming packet and forwards it to candidate peers if eligible.
     *
     * @param packet The incoming [MeshPacket].
     * @param incomingPeerAddress Optional hardware address or peer ID from which the packet was received.
     */
    suspend fun processAndRelay(
        packet: MeshPacket,
        incomingPeerAddress: String? = null
    ): RelayResult

    /**
     * Returns whether the given message ID has already been relayed or processed by this engine.
     */
    fun isAlreadyRelayed(messageId: String): Boolean

    /**
     * Clears duplicate tracking records (primarily for testing).
     */
    fun resetDuplicateTracking()
}

/**
 * Production implementation of [MeshRelayEngine].
 *
 * Guarantees:
 * 1. Forwards validated SOS packets only (ACK packets dropped).
 * 2. Increments [MeshPacket.hopCount] by 1 before dispatch.
 * 3. Enforces [MeshPacket.maxHops] (default 7); drops packets when hopCount >= maxHops.
 * 4. Loop & Duplicate protection via bounded, TTL-expired [DuplicateDetector].
 * 5. Self-loop prevention: drops packets where [MeshPacket.originNodeId] equals local node ID.
 * 6. Split-horizon / hairpin prevention: does not forward back to [MeshPacket.senderNodeId],
 *    [MeshPacket.originNodeId], or [incomingPeerAddress].
 * 7. Preserves original [MeshPacket.messageId] and [MeshPacket.originNodeId].
 * 8. Updates [MeshPacket.senderNodeId] to local node ID for the next transmission hop.
 * 9. Completely offline operation; fails gracefully without throwing or blocking.
 */
@Singleton
class DefaultMeshRelayEngine(
    private val bleManager: BleManager,
    duplicateDetector: DuplicateDetector
) : MeshRelayEngine {

    @Inject
    constructor(bleManager: BleManager) : this(
        bleManager = bleManager,
        duplicateDetector = InMemoryDuplicateDetector(
            maxCapacity = 1000,
            ttlMs = 10 * 60 * 1000L // 10 minutes
        )
    )

    private var activeDuplicateDetector: DuplicateDetector = duplicateDetector

    override fun isAlreadyRelayed(messageId: String): Boolean {
        return activeDuplicateDetector.isDuplicate(messageId)
    }

    override fun resetDuplicateTracking() {
        activeDuplicateDetector = InMemoryDuplicateDetector(
            maxCapacity = 1000,
            ttlMs = 10 * 60 * 1000L
        )
    }

    override suspend fun processAndRelay(
        packet: MeshPacket,
        incomingPeerAddress: String?
    ): RelayResult {
        val id = packet.messageId

        // 1. Validate Packet Invariants
        try {
            packet.validate()
        } catch (e: Exception) {
            Log.w(TAG, "Dropping malformed inbound packet [$id]: ${e.message}")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_MALFORMED,
                message = "Packet invariant validation failed: ${e.message}"
            )
        }

        return when (packet.packetType) {
            MeshPacketType.SOS -> processAndRelaySos(packet, incomingPeerAddress)
            MeshPacketType.ACK -> processAndRelayAck(packet, incomingPeerAddress)
        }
    }

    private suspend fun processAndRelaySos(
        packet: MeshPacket,
        incomingPeerAddress: String?
    ): RelayResult {
        val id = packet.messageId

        // 1. Self-Loopback Prevention: Never relay packets originated by this local node
        if (packet.originNodeId == bleManager.localNodeId) {
            Log.d(TAG, "Dropping packet [$id] originated by this local node (${bleManager.localNodeId})")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_ORIGINATED_BY_SELF,
                message = "Packet originated at this local node (${packet.originNodeId}). Dropped to prevent echo loop."
            )
        }

        // 2. Duplicate & Endless-Loop Protection: Forward each message at most once
        if (!activeDuplicateDetector.markSeen(id)) {
            Log.d(TAG, "Dropping duplicate packet [$id]; already processed or relayed")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_DUPLICATE,
                message = "Message [$id] has already been relayed or processed by this node."
            )
        }

        // 3. Hop Count & Max Hops Enforcement
        val nextHopCount = packet.hopCount + 1
        if (packet.hopCount >= packet.maxHops || nextHopCount > packet.maxHops) {
            Log.w(TAG, "Dropping packet [$id]: hop limit reached (${packet.hopCount}/${packet.maxHops})")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_MAX_HOPS_EXCEEDED,
                message = "Packet hop limit reached (${packet.hopCount}/${packet.maxHops}). Dropped."
            )
        }

        // 4. Construct Relayed Packet preserving original messageId & originNodeId
        val relayedPacket = try {
            packet.copy(
                senderNodeId = bleManager.localNodeId,
                hopCount = nextHopCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy packet for relay: ${e.message}")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_MALFORMED,
                message = "Failed to create relayed packet: ${e.message}"
            )
        }

        // 5. Split-Horizon / Hairpin Protection: Exclude the immediate sender, origin, and incoming address
        val excludeIds = buildSet {
            incomingPeerAddress?.let { add(it) }
            add(packet.senderNodeId)
            add(packet.originNodeId)
        }

        // 6. Check Candidate Discovered Peers
        val onlinePeers = bleManager.discoveredNodes.value.filter { peer ->
            peer.status == NodeStatus.ONLINE &&
                    peer.id !in excludeIds &&
                    peer.name !in excludeIds &&
                    (peer.nodeId == null || peer.nodeId !in excludeIds)
        }

        if (onlinePeers.isEmpty()) {
            Log.i(TAG, "No eligible candidate peers discovered to relay [$id] (excluded: $excludeIds)")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.SKIPPED_NO_PEERS,
                relayedPacket = relayedPacket,
                message = "No eligible online peers discovered to relay [$id]. Excluded: $excludeIds"
            )
        }

        // 7. Dispatch Relayed Packet over BLE GATT to All Eligible Peers
        Log.i(TAG, "Relaying packet [$id] at hop $nextHopCount (origin=${relayedPacket.originNodeId}, forwarder=${relayedPacket.senderNodeId}) to ${onlinePeers.size} peer(s)")
        val summary = try {
            bleManager.dispatchMeshPacket(relayedPacket, excludeIds)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during BLE dispatch of relayed packet [$id]: ${e.message}")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.RELAY_FAILED,
                relayedPacket = relayedPacket,
                message = "Exception during BLE dispatch: ${e.message}"
            )
        }

        return if (summary.isDeliveredToAnyPeer) {
            Log.i(TAG, "Successfully relayed [$id] to ${summary.successfulDeliveries}/${summary.totalPeersAttempted} peer(s)")
            RelayResult(
                messageId = id,
                decision = RelayDecision.RELAYED,
                relayedPacket = relayedPacket,
                broadcastSummary = summary,
                message = "Successfully relayed [$id] to ${summary.successfulDeliveries}/${summary.totalPeersAttempted} peer(s) at hop $nextHopCount."
            )
        } else {
            Log.w(TAG, "Relay delivery failed for [$id]: 0/${summary.totalPeersAttempted} peers received transmission")
            RelayResult(
                messageId = id,
                decision = RelayDecision.RELAY_FAILED,
                relayedPacket = relayedPacket,
                broadcastSummary = summary,
                message = "Failed to deliver relayed packet [$id] to any peer (${summary.successfulDeliveries}/${summary.totalPeersAttempted} delivered)."
            )
        }
    }

    private suspend fun processAndRelayAck(
        packet: MeshPacket,
        incomingPeerAddress: String?
    ): RelayResult {
        val id = packet.messageId

        // 1. Destination Check: If this node originated the SOS that this ACK is for, destination reached!
        if (packet.originNodeId == bleManager.localNodeId) {
            Log.i(TAG, "Inbound ACK [$id] reached destination node (${bleManager.localNodeId})")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DESTINATION_REACHED,
                relayedPacket = packet,
                message = "ACK [$id] reached destination originator node (${bleManager.localNodeId})."
            )
        }

        // 2. Duplicate & Loop Protection: Forward each ACK at most once
        if (!activeDuplicateDetector.markSeen(id)) {
            Log.d(TAG, "Dropping duplicate ACK packet [$id]; already processed or relayed")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_DUPLICATE,
                message = "ACK [$id] has already been relayed or processed by this node."
            )
        }

        // 3. Hop Limit Enforcement
        val nextHopCount = packet.hopCount + 1
        if (packet.hopCount >= packet.maxHops || nextHopCount > packet.maxHops) {
            Log.w(TAG, "Dropping ACK packet [$id]: hop limit reached (${packet.hopCount}/${packet.maxHops})")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_MAX_HOPS_EXCEEDED,
                message = "ACK hop limit reached (${packet.hopCount}/${packet.maxHops}). Dropped."
            )
        }

        // 4. Construct Relayed ACK Packet
        val relayedPacket = try {
            packet.copy(
                senderNodeId = bleManager.localNodeId,
                hopCount = nextHopCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy ACK packet for relay: ${e.message}")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.DROPPED_MALFORMED,
                message = "Failed to create relayed ACK packet: ${e.message}"
            )
        }

        // 5. Split-Horizon: Exclude the immediate predecessor that forwarded this ACK to us
        val excludeIds = buildSet {
            incomingPeerAddress?.let { add(it) }
            add(packet.senderNodeId)
        }

        // 6. Check Candidate Peers (toward originNodeId)
        val onlinePeers = bleManager.discoveredNodes.value.filter { peer ->
            peer.status == NodeStatus.ONLINE &&
                    peer.id !in excludeIds &&
                    peer.name !in excludeIds
        }

        if (onlinePeers.isEmpty()) {
            Log.i(TAG, "No eligible candidate peers discovered to relay ACK [$id] (excluded: $excludeIds)")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.SKIPPED_NO_PEERS,
                relayedPacket = relayedPacket,
                message = "No eligible online peers discovered to relay ACK [$id]. Excluded: $excludeIds"
            )
        }

        // 7. Dispatch Relayed ACK
        val summary = try {
            bleManager.dispatchMeshPacket(relayedPacket, excludeIds)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during BLE dispatch of relayed ACK [$id]: ${e.message}")
            return RelayResult(
                messageId = id,
                decision = RelayDecision.RELAY_FAILED,
                relayedPacket = relayedPacket,
                message = "Exception during BLE dispatch of ACK: ${e.message}"
            )
        }

        return if (summary.isDeliveredToAnyPeer) {
            RelayResult(
                messageId = id,
                decision = RelayDecision.RELAYED,
                relayedPacket = relayedPacket,
                broadcastSummary = summary,
                message = "Successfully relayed ACK [$id] to ${summary.successfulDeliveries}/${summary.totalPeersAttempted} peer(s) at hop $nextHopCount."
            )
        } else {
            RelayResult(
                messageId = id,
                decision = RelayDecision.RELAY_FAILED,
                relayedPacket = relayedPacket,
                broadcastSummary = summary,
                message = "Failed to deliver relayed ACK [$id] to any peer."
            )
        }
    }
}
