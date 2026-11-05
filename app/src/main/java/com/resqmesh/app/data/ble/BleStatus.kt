package com.resqmesh.app.data.ble

/**
 * Operational state of the local device's Bluetooth Low Energy subsystem.
 */
enum class BleOperationalState {
    UNSUPPORTED,              // Device lacks Bluetooth LE hardware
    PERMISSIONS_REQUIRED,     // Missing runtime permissions (BLUETOOTH_SCAN, ADVERTISE, CONNECT, LOCATION)
    BLUETOOTH_DISABLED,       // Physical Bluetooth radio is turned OFF in system settings
    READY_IDLE,               // Initialized and ready to scan/advertise
    SCANNING_AND_ADVERTISING, // Fully active: scanning for peers and advertising presence
    SCANNING_ONLY,            // Scanning active; advertising failed or not supported on this chipset
    ADVERTISING_ONLY,         // Advertising active; scanning paused or failed
    ERROR                     // Failure reported by scanner or advertiser
}

/**
 * Comprehensive real-time status of the BLE subsystem.
 */
data class BleServiceStatus(
    val state: BleOperationalState = BleOperationalState.READY_IDLE,
    val isBleSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val isAdvertisingSupported: Boolean = true,
    val hasPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val discoveredPeerCount: Int = 0,
    val localNodeId: String = "RQM-LOCAL",
    val statusMessage: String = "Initializing BLE..."
)
