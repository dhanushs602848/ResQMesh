package com.resqmesh.bletester.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.resqmesh.bletester.model.MessagePacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connects to a remote peer phone via BLE GATT Client, transmits packets,
 * and measures round-trip latency (ping-pong test).
 */
class BleGattClientManager(
    private val context: Context,
    private val sniffer: BlePacketSniffer
) {
    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _lastRoundTripLatencyMs = MutableStateFlow<Long?>(null)
    val lastRoundTripLatencyMs: StateFlow<Long?> = _lastRoundTripLatencyMs.asStateFlow()

    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private var pingStartTime: Long = 0

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val addr = gatt?.device?.address ?: "UNKNOWN"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected (Discovering Services...)"
                _connectedDeviceAddress.value = addr
                sniffer.log(LogDirection.GATT_CLIENT, "Connected to $addr. Discovering services...", deviceAddress = addr)
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                _connectedDeviceAddress.value = null
                targetCharacteristic = null
                sniffer.log(LogDirection.GATT_CLIENT, "Disconnected from $addr (Status: $status)", deviceAddress = addr)
                close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val addr = gatt?.device?.address ?: "UNKNOWN"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(MessagePacket.SERVICE_UUID)
                if (service != null) {
                    targetCharacteristic = service.getCharacteristic(MessagePacket.CHARACTERISTIC_UUID)
                    _connectionState.value = "Connected & Ready (ResQMesh Service Found)"
                    sniffer.log(LogDirection.GATT_CLIENT, "Found ResQMesh Service on $addr!", deviceAddress = addr)
                } else {
                    _connectionState.value = "Connected (Non-ResQMesh Device)"
                    sniffer.log(LogDirection.GATT_CLIENT, "Services discovered on $addr, but ResQMesh UUID not found", deviceAddress = addr)
                }
            } else {
                _connectionState.value = "Service Discovery Failed ($status)"
                sniffer.log(LogDirection.ERROR, "Service discovery failed on $addr: $status", deviceAddress = addr)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val addr = gatt?.device?.address ?: "UNKNOWN"
            val rtt = if (pingStartTime > 0) System.currentTimeMillis() - pingStartTime else null
            _lastRoundTripLatencyMs.value = rtt

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val rttStr = if (rtt != null) " (RTT: ${rtt}ms)" else ""
                val msg = "Packet write confirmed by $addr$rttStr"
                sniffer.log(
                    direction = LogDirection.TX,
                    message = msg,
                    deviceAddress = addr,
                    rawBytes = characteristic?.value
                )
            } else {
                sniffer.log(LogDirection.ERROR, "Packet write failed to $addr: status $status", deviceAddress = addr)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val addr = gatt?.device?.address ?: "UNKNOWN"
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.value != null) {
                val value = characteristic.value
                val parsed = MessagePacket.fromByteArray(value)
                val msg = "Read response from $addr: ${String(value)}"
                sniffer.log(
                    direction = LogDirection.RX,
                    message = msg,
                    deviceAddress = addr,
                    rawBytes = value,
                    parsedPacket = parsed
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        if (!BlePermissions.hasAllPermissions(context)) {
            sniffer.log(LogDirection.ERROR, "Missing permissions to connect GATT")
            return false
        }

        close()
        _connectionState.value = "Connecting to ${device.address}..."
        sniffer.log(LogDirection.GATT_CLIENT, "Initiating GATT connection to ${device.address}")
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            return true
        } catch (e: Exception) {
            _connectionState.value = "Connection error"
            sniffer.log(LogDirection.ERROR, "Exception connecting: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPacket(packet: MessagePacket): Boolean {
        val gatt = bluetoothGatt
        val char = targetCharacteristic
        if (gatt == null || char == null) {
            sniffer.log(LogDirection.ERROR, "Cannot send packet: Not connected or characteristic not ready")
            return false
        }

        val bytes = packet.toByteArray()
        char.value = bytes
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        pingStartTime = System.currentTimeMillis()

        try {
            val success = gatt.writeCharacteristic(char)
            if (success) {
                sniffer.log(
                    direction = LogDirection.TX,
                    message = "Transmitting packet [Type=${packet.messageType}, ID=${packet.id.take(8)}] to ${gatt.device.address}",
                    deviceAddress = gatt.device.address,
                    rawBytes = bytes,
                    parsedPacket = packet
                )
            } else {
                sniffer.log(LogDirection.ERROR, "writeCharacteristic returned false")
            }
            return success
        } catch (e: Exception) {
            sniffer.log(LogDirection.ERROR, "Exception writing characteristic: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            // Ignore
        }
        bluetoothGatt = null
        targetCharacteristic = null
        _connectionState.value = "Disconnected"
        _connectedDeviceAddress.value = null
    }
}
