package com.resqmesh.app.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.resqmesh.app.data.model.InMemoryDuplicateDetector
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketFragment
import com.resqmesh.app.data.model.MeshPacketReassembler
import com.resqmesh.app.data.model.NodeStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResQMesh-BLE"

@Singleton
class DefaultBleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gattPacketDispatcher: GattPacketDispatcher
) : BleManager {

    constructor(context: Context) : this(context, DefaultGattPacketDispatcher(context))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cleanupJob: Job? = null

    // Persistent Local Node ID
    override val localNodeId: String = getOrCreateNodeId()

    // Real-time discovered peers
    private val peersMap = ConcurrentHashMap<String, MeshNode>()
    private val _discoveredNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    override val discoveredNodes: StateFlow<List<MeshNode>> = _discoveredNodes.asStateFlow()

    // Real-time BLE operational status
    private val _bleStatus = MutableStateFlow(BleServiceStatus(localNodeId = localNodeId))
    override val bleStatus: StateFlow<BleServiceStatus> = _bleStatus.asStateFlow()

    // Inbound received mesh packets flow (consumed by Repository & Relay Engine)
    private val _inboundPackets = MutableSharedFlow<MeshPacket>(replay = 0, extraBufferCapacity = 64)
    override val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    // Fragmentation reassembly and duplicate tracking
    private val reassembler = MeshPacketReassembler()
    private val duplicateDetector = InMemoryDuplicateDetector()

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private var isAdvertisingActive = false
    private var isScanningActive = false
    private var isGattServerRunning = false
    private var isReceiverRegistered = false

    // Bluetooth Hardware State BroadcastReceiver
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.i(TAG, "Bluetooth switched ON via hardware/system")
                        updateBleStatus()
                        startMeshDiscovery()
                    }
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> {
                        Log.w(TAG, "Bluetooth switched OFF via hardware/system")
                        stopMeshDiscovery(isManualStop = false)
                        updateBleStatus()
                    }
                }
            }
        }
    }

    // GATT Server Callback for inbound peer connections and characteristic writes
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val address = device?.address ?: "UNKNOWN"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Inbound GATT client connected: $address (status=$status)")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Inbound GATT client disconnected: $address (status=$status)")
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT Service successfully added: ${service?.uuid}")
            } else {
                Log.e(TAG, "Failed to add GATT service: ${service?.uuid}, status=$status")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val charUuid = characteristic?.uuid
            val address = device?.address ?: "UNKNOWN"
            Log.d(TAG, "Inbound GATT write request from $address on $charUuid (len=${value?.size ?: 0}, responseNeeded=$responseNeeded)")

            // 1. Immediately send GATT response if requested to prevent remote client timeouts
            if (responseNeeded) {
                try {
                    val status = if (value != null && value.isNotEmpty()) {
                        BluetoothGatt.GATT_SUCCESS
                    } else {
                        BluetoothGatt.GATT_FAILURE
                    }
                    gattServer?.sendResponse(device, requestId, status, offset, value)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException sending GATT write response: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception sending GATT write response: ${e.message}")
                }
            }

            if (value == null || value.isEmpty()) {
                Log.w(TAG, "Received empty characteristic write from $address")
                return
            }

            // 2. Offload packet parsing & validation to background coroutine to never block binder thread
            scope.launch {
                processIncomingBytes(value, charUuid, address)
            }
        }
    }

    // Advertise Callback
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE advertising started successfully with UUID: ${BleConstants.RESQMESH_SERVICE_UUID_STRING}")
            isAdvertisingActive = true
            updateBleStatus()
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertisingActive = false
            val errorMsg = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertise data too large (>31 bytes)"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many active advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already active"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal Bluetooth controller error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising unsupported on hardware"
                else -> "Advertising failure code $errorCode"
            }
            Log.e(TAG, "BLE Advertising failed: $errorMsg")
            updateBleStatus(overrideMessage = errorMsg)
        }
    }

    // Scan Callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanningActive = false
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal scanner error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scanner unsupported"
                else -> "Scan failure code $errorCode"
            }
            Log.e(TAG, "BLE Scan failed: $errorMsg")
            updateBleStatus(overrideMessage = errorMsg)
        }
    }

    init {
        registerBluetoothStateReceiver()
        updateBleStatus()
    }

    private fun registerBluetoothStateReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                context.registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register Bluetooth state receiver: ${e.message}")
            }
        }
    }

    @Synchronized
    override fun startMeshDiscovery() {
        registerBluetoothStateReceiver()

        // 1. Hardware capability check
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!hasBle || bluetoothAdapter == null) {
            Log.e(TAG, "Device lacks Bluetooth Low Energy hardware")
            _bleStatus.value = _bleStatus.value.copy(
                state = BleOperationalState.UNSUPPORTED,
                isBleSupported = false,
                statusMessage = "BLE Hardware not supported on this device"
            )
            return
        }

        // 2. Runtime permission check
        val hasPermissions = BlePermissionHelper.hasPermissions(context)
        if (!hasPermissions) {
            Log.w(TAG, "Bluetooth permissions not granted")
            _bleStatus.value = _bleStatus.value.copy(
                state = BleOperationalState.PERMISSIONS_REQUIRED,
                hasPermissions = false,
                statusMessage = "Bluetooth runtime permissions required"
            )
            return
        }

        // 3. Adapter enabled check
        val isEnabled = bluetoothAdapter?.isEnabled == true
        if (!isEnabled) {
            Log.w(TAG, "Bluetooth adapter is currently turned off")
            _bleStatus.value = _bleStatus.value.copy(
                state = BleOperationalState.BLUETOOTH_DISABLED,
                isBluetoothEnabled = false,
                statusMessage = "Bluetooth is disabled in System Settings"
            )
            return
        }

        // 4. Start GATT Server for incoming mesh packets
        startGattServer()

        // 5. Start Advertising presence
        startAdvertising()

        // 6. Start Scanning for peers
        startScanning()

        // 7. Launch periodic cleanup loop
        startPeriodicCleanup()

        updateBleStatus()
    }

    private fun startGattServer() {
        if (isGattServerRunning && gattServer != null) return

        val bm = bluetoothManager ?: return
        val hasPermissions = BlePermissionHelper.hasPermissions(context)
        if (!hasPermissions) {
            Log.w(TAG, "Cannot start GATT server: permissions not granted")
            return
        }

        try {
            val server = bm.openGattServer(context, gattServerCallback)
            if (server == null) {
                Log.e(TAG, "openGattServer returned null")
                return
            }
            gattServer = server

            val service = BluetoothGattService(
                BleConstants.RESQMESH_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val sosChar = BluetoothGattCharacteristic(
                BleConstants.SOS_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            val ackChar = BluetoothGattCharacteristic(
                BleConstants.ACK_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            service.addCharacteristic(sosChar)
            service.addCharacteristic(ackChar)

            val success = server.addService(service)
            isGattServerRunning = success
            Log.i(TAG, "GATT Server registered service: $success (Service UUID: ${BleConstants.RESQMESH_SERVICE_UUID_STRING})")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException opening GATT server: ${e.message}")
            isGattServerRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception opening GATT server: ${e.message}")
            isGattServerRunning = false
        }
    }

    private fun stopGattServer() {
        if (gattServer == null) return
        try {
            gattServer?.clearServices()
            gattServer?.close()
            Log.i(TAG, "GATT server stopped and services cleared")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException closing GATT server: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing GATT server: ${e.message}")
        } finally {
            gattServer = null
            isGattServerRunning = false
            reassembler.clear()
        }
    }

    private fun startAdvertising() {
        if (isAdvertisingActive) return

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isMultipleAdvertisementSupported) {
            Log.w(TAG, "Device does not support multiple BLE advertisements")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser is unavailable on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely until explicitly stopped
            .build()

        // Construct 128-bit Service UUID advertisement
        // Keeps packet strictly within legacy 31-byte limit (Flags: 3B + 128-bit UUID: 18B = 21B)
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.RESQMESH_PARCEL_UUID)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // Secondary scan response includes device name
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE advertising: ${e.message}")
            _bleStatus.value = _bleStatus.value.copy(
                state = BleOperationalState.PERMISSIONS_REQUIRED,
                hasPermissions = false,
                statusMessage = "Permission revoked during advertising"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE advertising: ${e.message}")
        }
    }

    private fun startScanning() {
        if (isScanningActive) return

        val adapter = bluetoothAdapter ?: return
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner is unavailable")
            return
        }

        // Filter: Discover ONLY devices advertising the ResQMesh Service UUID
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(BleConstants.RESQMESH_PARCEL_UUID)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner?.startScan(filters, settings, scanCallback)
            isScanningActive = true
            Log.i(TAG, "BLE Scanning active for Service UUID: ${BleConstants.RESQMESH_SERVICE_UUID_STRING}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scanning: ${e.message}")
            _bleStatus.value = _bleStatus.value.copy(
                state = BleOperationalState.PERMISSIONS_REQUIRED,
                hasPermissions = false,
                statusMessage = "Permission revoked during scanning"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val rssi = result.rssi
        val now = System.currentTimeMillis()

        // Derive identifiable peer name
        val deviceName = try {
            if (BlePermissionHelper.hasPermissions(context)) device.name else null
        } catch (_: SecurityException) {
            null
        } ?: result.scanRecord?.deviceName ?: "ResQ Peer ${address.takeLast(5)}"

        val existing = peersMap[address]
        val updatedNode = if (existing != null) {
            existing.copy(
                rssi = rssi,
                lastSeenTimestamp = now,
                status = NodeStatus.ONLINE
            )
        } else {
            Log.i(TAG, "Discovered NEW ResQMesh peer node: $address ($deviceName) RSSI=$rssi dBm")
            MeshNode(
                id = address,
                name = deviceName,
                rssi = rssi,
                hopCount = 1, // Directly discovered peer = 1 hop
                isRelayCapable = true,
                lastSeenTimestamp = now,
                status = NodeStatus.ONLINE
            )
        }

        peersMap[address] = updatedNode
        publishDiscoveredNodes()
    }

    private fun publishDiscoveredNodes() {
        val list = peersMap.values.toList().sortedByDescending { it.rssi }
        _discoveredNodes.value = list
        updateBleStatus()
    }

    /**
     * Decodes, validates, and dispatches an inbound GATT characteristic byte payload.
     * Supports both unfragmented MeshPackets and multi-part MeshPacketFragments.
     */
    internal fun processIncomingBytes(
        bytes: ByteArray,
        charUuid: UUID? = null,
        senderAddress: String = "UNKNOWN"
    ): MeshPacket? {
        if (bytes.isEmpty()) {
            Log.w(TAG, "Empty byte array received from $senderAddress")
            return null
        }

        val packet: MeshPacket? = try {
            when {
                // Case 1: Unfragmented MeshPacket starting with magic bytes 'R', 'M'
                bytes.size >= 2 && bytes[0] == MeshPacket.MAGIC_1 && bytes[1] == MeshPacket.MAGIC_2 -> {
                    MeshPacket.fromByteArray(bytes)
                }

                // Case 2: MeshPacketFragment starting with fragment magic bytes 'R', 'F'
                bytes.size >= 2 && bytes[0] == MeshPacketFragment.FRAGMENT_MAGIC_1 && bytes[1] == MeshPacketFragment.FRAGMENT_MAGIC_2 -> {
                    val fragment = MeshPacketFragment.fromByteArray(bytes)
                    Log.d(TAG, "Received fragment ${fragment.fragmentIndex + 1}/${fragment.totalFragments} for msg ${fragment.messageId}")
                    reassembler.addFragment(fragment)
                }

                // Fallback attempt: try parsing as MeshPacket directly
                else -> {
                    MeshPacket.fromByteArray(bytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rejected malformed or corrupted inbound packet from $senderAddress: ${e.message}")
            null
        }

        if (packet != null) {
            val dedupeKey = "${packet.packetType}:${packet.messageId}"
            val isNew = duplicateDetector.markSeen(dedupeKey)
            Log.i(
                TAG,
                "Inbound ${packet.packetType} [${packet.messageId}] origin=${packet.originNodeId} " +
                        "sender=${packet.senderNodeId} via=$senderAddress hops=${packet.hopCount}/${packet.maxHops} (isNew=$isNew)"
            )

            val emitted = _inboundPackets.tryEmit(packet)
            if (!emitted) {
                scope.launch {
                    _inboundPackets.emit(packet)
                }
            }
        }

        return packet
    }

    private fun startPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(BleConstants.CLEANUP_INTERVAL_MS)
                val now = System.currentTimeMillis()
                var changed = false

                val iterator = peersMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val age = now - entry.value.lastSeenTimestamp
                    if (age > BleConstants.PEER_EXPIRY_TIMEOUT_MS) {
                        Log.i(TAG, "Pruning expired peer: ${entry.key}")
                        iterator.remove()
                        changed = true
                    } else if (age > BleConstants.PEER_STALE_TIMEOUT_MS && entry.value.status != NodeStatus.STALE) {
                        peersMap[entry.key] = entry.value.copy(status = NodeStatus.STALE)
                        changed = true
                    }
                }

                if (changed) {
                    publishDiscoveredNodes()
                }
            }
        }
    }

    @Synchronized
    override fun stopMeshDiscovery() {
        stopMeshDiscovery(isManualStop = true)
    }

    private fun stopMeshDiscovery(isManualStop: Boolean) {
        cleanupJob?.cancel()
        cleanupJob = null

        // Stop GATT Server
        stopGattServer()

        // Stop Advertiser
        if (isAdvertisingActive) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
                Log.i(TAG, "BLE advertising stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping advertising: ${e.message}")
            }
            isAdvertisingActive = false
        }

        // Stop Scanner
        if (isScanningActive) {
            try {
                scanner?.stopScan(scanCallback)
                Log.i(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scanner: ${e.message}")
            }
            isScanningActive = false
        }

        updateBleStatus(overrideMessage = if (isManualStop) "Mesh discovery paused" else null)
    }

    override suspend fun dispatchMeshPacket(packet: MeshPacket): BleBroadcastSummary {
        return dispatchMeshPacket(packet, emptySet())
    }

    override suspend fun dispatchMeshPacket(
        packet: MeshPacket,
        excludePeerIds: Set<String>
    ): BleBroadcastSummary {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Cannot dispatch packet [${packet.messageId}]: Bluetooth radio is disabled")
            return BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
        }

        if (!BlePermissionHelper.hasPermissions(context)) {
            Log.w(TAG, "Cannot dispatch packet [${packet.messageId}]: Bluetooth permissions missing")
            return BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
        }

        val targetPeers = peersMap.values.filter {
            it.status == NodeStatus.ONLINE &&
                    it.id !in excludePeerIds &&
                    it.name !in excludePeerIds
        }
        if (targetPeers.isEmpty()) {
            Log.i(TAG, "No online peers currently discovered for dispatch of [${packet.messageId}] (excluded: $excludePeerIds)")
            return BleBroadcastSummary(packet.messageId, 0, 0, emptyList())
        }

        Log.i(TAG, "Dispatching 1-hop BLE packet [${packet.messageId}] to ${targetPeers.size} peer(s)")
        val results = mutableListOf<BleDispatchResult>()
        var successCount = 0

        for (peer in targetPeers) {
            val device = try {
                adapter.getRemoteDevice(peer.id)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid Bluetooth address for peer ${peer.id}: ${e.message}")
                null
            }

            if (device == null) {
                results.add(
                    BleDispatchResult(
                        peerAddress = peer.id,
                        peerName = peer.name,
                        isDelivered = false,
                        error = "Invalid Bluetooth hardware address"
                    )
                )
                continue
            }

            val result = gattPacketDispatcher.dispatchToPeer(device, peer.name, packet)
            results.add(result)
            if (result.isDelivered) {
                successCount++
                Log.i(TAG, "Delivered [${packet.messageId}] to ${peer.name} (${peer.id}) in ${result.fragmentsSent} chunk(s)")
            } else {
                Log.w(TAG, "Failed delivery of [${packet.messageId}] to ${peer.name} (${peer.id}): ${result.error}")
            }
        }

        val summary = BleBroadcastSummary(
            messageId = packet.messageId,
            totalPeersAttempted = targetPeers.size,
            successfulDeliveries = successCount,
            results = results
        )

        Log.i(TAG, "Completed 1-hop BLE broadcast of [${packet.messageId}]: $successCount/${targetPeers.size} delivered")
        return summary
    }

    override fun refreshDiscovery() {
        peersMap.clear()
        _discoveredNodes.value = emptyList()
        stopMeshDiscovery(isManualStop = false)
        startMeshDiscovery()
    }

    private fun updateBleStatus(overrideMessage: String? = null) {
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val isEnabled = bluetoothAdapter?.isEnabled == true
        val hasPermissions = BlePermissionHelper.hasPermissions(context)
        val peerCount = peersMap.size

        val state = when {
            !hasBle -> BleOperationalState.UNSUPPORTED
            !hasPermissions -> BleOperationalState.PERMISSIONS_REQUIRED
            !isEnabled -> BleOperationalState.BLUETOOTH_DISABLED
            isScanningActive && isAdvertisingActive -> BleOperationalState.SCANNING_AND_ADVERTISING
            isScanningActive -> BleOperationalState.SCANNING_ONLY
            isAdvertisingActive -> BleOperationalState.ADVERTISING_ONLY
            else -> BleOperationalState.READY_IDLE
        }

        val message = overrideMessage ?: when (state) {
            BleOperationalState.UNSUPPORTED -> "BLE hardware not supported"
            BleOperationalState.PERMISSIONS_REQUIRED -> "Bluetooth permissions required"
            BleOperationalState.BLUETOOTH_DISABLED -> "Bluetooth radio is disabled"
            BleOperationalState.SCANNING_AND_ADVERTISING -> if (peerCount == 0) "Scanning & Advertising (0 peers in range)" else "Broadcasting on Mesh ($peerCount peers active)"
            BleOperationalState.SCANNING_ONLY -> "Scanning for nearby peers ($peerCount found)"
            BleOperationalState.ADVERTISING_ONLY -> "Broadcasting presence on mesh"
            BleOperationalState.READY_IDLE -> "BLE Mesh ready"
            BleOperationalState.ERROR -> "BLE operation error"
        }

        _bleStatus.value = BleServiceStatus(
            state = state,
            isBleSupported = hasBle,
            isBluetoothEnabled = isEnabled,
            isAdvertisingSupported = bluetoothAdapter?.isMultipleAdvertisementSupported == true,
            hasPermissions = hasPermissions,
            isScanning = isScanningActive,
            isAdvertising = isAdvertisingActive,
            discoveredPeerCount = peerCount,
            localNodeId = localNodeId,
            statusMessage = message
        )
    }

    private fun getOrCreateNodeId(): String {
        val prefs = context.getSharedPreferences("resqmesh_ble_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("local_node_id", null)
        if (!existing.isNullOrBlank()) return existing

        val newId = "RQM-" + UUID.randomUUID().toString().take(4).uppercase()
        prefs.edit().putString("local_node_id", newId).apply()
        return newId
    }
}
