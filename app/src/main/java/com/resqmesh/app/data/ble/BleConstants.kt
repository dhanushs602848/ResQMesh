package com.resqmesh.app.data.ble

import android.os.ParcelUuid
import java.util.UUID

/**
 * Common Bluetooth Low Energy constants for ResQMesh.
 * Uses a single standard 128-bit custom service UUID for peer discovery.
 */
object BleConstants {

    // Custom 128-bit ResQMesh Service UUID (valid hex and hyphens only)
    const val RESQMESH_SERVICE_UUID_STRING = "7b1f3c8a-92e4-4d8b-b6d1-4e9a8f2c3b5d"
    val RESQMESH_SERVICE_UUID: UUID = UUID.fromString(RESQMESH_SERVICE_UUID_STRING)
    val RESQMESH_PARCEL_UUID: ParcelUuid = ParcelUuid(RESQMESH_SERVICE_UUID)

    // Custom 128-bit Characteristic UUIDs for GATT write/notify exchange
    const val SOS_CHARACTERISTIC_UUID_STRING = "7b1f3c8b-92e4-4d8b-b6d1-4e9a8f2c3b5d"
    val SOS_CHARACTERISTIC_UUID: UUID = UUID.fromString(SOS_CHARACTERISTIC_UUID_STRING)

    const val ACK_CHARACTERISTIC_UUID_STRING = "7b1f3c8c-92e4-4d8b-b6d1-4e9a8f2c3b5d"
    val ACK_CHARACTERISTIC_UUID: UUID = UUID.fromString(ACK_CHARACTERISTIC_UUID_STRING)

    // Periodic sweep timings
    const val PEER_STALE_TIMEOUT_MS = 25_000L
    const val PEER_EXPIRY_TIMEOUT_MS = 60_000L
    const val CLEANUP_INTERVAL_MS = 8_000L

    // Valid ResQMesh Node ID: Starts with "RQM-" followed by 4 to 16 alphanumeric characters or hyphens
    val NODE_ID_REGEX = Regex("^RQM-[A-Z0-9_-]{4,16}$", RegexOption.IGNORE_CASE)

    fun isValidNodeId(nodeId: String?): Boolean {
        if (nodeId.isNullOrBlank()) return false
        return NODE_ID_REGEX.matches(nodeId.trim())
    }
}
