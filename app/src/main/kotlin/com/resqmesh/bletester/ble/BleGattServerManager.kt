package com.resqmesh.bletester.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.resqmesh.bletester.model.MessagePacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hosts a real BLE GATT Server on the phone with ResQMesh service and characteristic.
 * Allows another phone to connect, write packets, and perform round-trip ping tests.
 */
class BleGattServerManager(
    private val context: Context,
    private val sniffer: BlePacketSniffer
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<String>>(emptyList())
    val connectedClients: StateFlow<List<String>> = _connectedClients.asStateFlow()

    private val _packetsReceivedCount = MutableStateFlow(0)
    val packetsReceivedCount: StateFlow<Int> = _packetsReceivedCount.asStateFlow()

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val addr = device?.address ?: "UNKNOWN"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val current = _connectedClients.value.toMutableList()
                if (!current.contains(addr)) current.add(addr)
                _connectedClients.value = current
                sniffer.log(LogDirection.GATT_SERVER, "GATT Client connected: $addr", deviceAddress = addr)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val current = _connectedClients.value.toMutableList()
                current.remove(addr)
                _connectedClients.value = current
                sniffer.log(LogDirection.GATT_SERVER, "GATT Client disconnected: $addr", deviceAddress = addr)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val addr = device?.address ?: "UNKNOWN"
            if (value != null && characteristic?.uuid == MessagePacket.CHARACTERISTIC_UUID) {
                _packetsReceivedCount.value += 1
                val parsed = MessagePacket.fromByteArray(value)
                val info = if (parsed != null) {
                    "[ResQMesh Packet Type=${parsed.messageType}, ID=${parsed.id.take(8)}, Payload='${parsed.payload}']"
                } else {
                    "[Raw bytes: ${value.size} bytes]"
                }

                sniffer.log(
                    direction = LogDirection.RX,
                    message = "GATT Write received from $addr: $info",
                    deviceAddress = addr,
                    rawBytes = value,
                    parsedPacket = parsed
                )

                // Update characteristic value
                characteristic.value = value
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val addr = device?.address ?: "UNKNOWN"
            val value = characteristic?.value ?: "ResQMesh-Server-OK".toByteArray()
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            sniffer.log(
                direction = LogDirection.GATT_SERVER,
                message = "GATT Read request served for $addr",
                deviceAddress = addr,
                rawBytes = value
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        if (!BlePermissions.hasAllPermissions(context)) {
            sniffer.log(LogDirection.ERROR, "Missing permissions to start GATT server")
            return false
        }

        if (bluetoothManager == null) {
            sniffer.log(LogDirection.ERROR, "BluetoothManager not available")
            return false
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, serverCallback)
            if (gattServer == null) {
                sniffer.log(LogDirection.ERROR, "Failed to open GATT Server")
                return false
            }

            val service = BluetoothGattService(
                MessagePacket.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val characteristic = BluetoothGattCharacteristic(
                MessagePacket.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or
                        BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            service.addCharacteristic(characteristic)
            gattServer?.addService(service)

            _isServerRunning.value = true
            sniffer.log(LogDirection.INFO, "GATT Server STARTED (UUID: ${MessagePacket.SERVICE_UUID})")
            return true
        } catch (e: Exception) {
            sniffer.log(LogDirection.ERROR, "Exception starting GATT Server: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (e: Exception) {
            // Ignore
        }
        gattServer = null
        _isServerRunning.value = false
        _connectedClients.value = emptyList()
        sniffer.log(LogDirection.INFO, "GATT Server stopped")
    }
}
