package com.resqmesh.bletester.model

/**
 * Defines the functional type of a packet in the ResQMesh BLE mesh network.
 * Compatible with the core ResQMesh protocol.
 */
enum class MessageType(val defaultPriority: Int) {
    /**
     * Emergency distress broadcast (highest priority, immediate flooding).
     */
    SOS(100),

    /**
     * Direct or group text communication between rescue personnel.
     */
    CHAT(50),

    /**
     * GPS coordinates or location telemetry packet.
     */
    LOCATION(40),

    /**
     * End-to-end delivery acknowledgement.
     */
    ACK(80),

    /**
     * Mesh neighbor discovery and link quality beacon.
     */
    BEACON(10);

    companion object {
        fun fromString(type: String?): MessageType {
            return entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: CHAT
        }
    }
}
