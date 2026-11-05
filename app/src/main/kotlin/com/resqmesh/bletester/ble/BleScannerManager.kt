package com.resqmesh.bletester.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.resqmesh.bletester.model.MessagePacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredPeer(
    val device: BluetoothDevice,
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val serviceData: ByteArray? = null,
    val parsedPacket: MessagePacket? = null,
    val isResQMeshNode: Boolean = false
)

/**
 * Manages physical BLE scanning to discover neighboring ResQMesh nodes and monitor packets.
 */
class BleScannerManager(
    private val context: Context,
    private val sniffer: BlePacketSniffer
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _filterOnlyResQMesh = MutableStateFlow(false)
    val filterOnlyResQMesh: StateFlow<Boolean> = _filterOnlyResQMesh.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun setFilterOnlyResQMesh(filter: Boolean) {
        _filterOnlyResQMesh.value = filter
        if (_isScanning.value) {
            // Restart scan with updated filters
            stopScanning()
            startScanning()
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            handleScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal BLE controller error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported"
                else -> "Error code: $errorCode"
            }
            val msg = "Scan FAILED: $reason"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address ?: "UNKNOWN"
        val name = device.name ?: "Unknown Peer"
        val rssi = result.rssi

        val pUuid = ParcelUuid(MessagePacket.SERVICE_UUID)
        val scanRecord = result.scanRecord
        val serviceData = scanRecord?.getServiceData(pUuid)
        val hasResQMeshUuid = scanRecord?.serviceUuids?.contains(pUuid) == true || serviceData != null

        var parsedPacket: MessagePacket? = null
        if (serviceData != null) {
            parsedPacket = MessagePacket.fromByteArray(serviceData)
        }

        val peer = DiscoveredPeer(
            device = device,
            address = address,
            name = name,
            rssi = rssi,
            lastSeenTimestamp = System.currentTimeMillis(),
            serviceData = serviceData,
            parsedPacket = parsedPacket,
            isResQMeshNode = hasResQMeshUuid
        )

        // Update list
        val current = _discoveredPeers.value.toMutableList()
        val index = current.indexOfFirst { it.address == address }
        if (index != -1) {
            current[index] = peer
        } else {
            current.add(peer)
        }
        _discoveredPeers.value = current

        // Log to packet sniffer
        if (hasResQMeshUuid || !_filterOnlyResQMesh.value) {
            sniffer.log(
                direction = LogDirection.SCAN,
                message = "Peer $address ($name) RSSI: $rssi dBm ${if (hasResQMeshUuid) "[ResQMesh Node]" else ""}",
                deviceAddress = address,
                rssi = rssi,
                rawBytes = serviceData,
                parsedPacket = parsedPacket
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        if (!BlePermissions.hasAllPermissions(context)) {
            val msg = "Missing required BLE permissions to scan"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val msg = "Bluetooth is disabled or not supported"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            val msg = "BLE Scanner not available on this device"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val filters = mutableListOf<ScanFilter>()
        if (_filterOnlyResQMesh.value) {
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(MessagePacket.SERVICE_UUID))
                    .build()
            )
        }

        try {
            scanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            val msg = "Scanning active (Filters: ${if (_filterOnlyResQMesh.value) "ResQMesh UUID only" else "All BLE"})"
            _statusMessage.value = msg
            sniffer.log(LogDirection.INFO, msg)
            return true
        } catch (e: Exception) {
            val msg = "Exception starting scan: ${e.message}"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        _isScanning.value = false
        _statusMessage.value = "Scan stopped"
        sniffer.log(LogDirection.INFO, "Scan stopped")
    }

    fun clearPeers() {
        _discoveredPeers.value = emptyList()
    }
}
