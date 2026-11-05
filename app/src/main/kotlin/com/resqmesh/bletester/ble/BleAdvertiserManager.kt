package com.resqmesh.bletester.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.resqmesh.bletester.model.MessagePacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls the physical BLE advertiser radio for broadcasting ResQMesh beacons and packets.
 */
class BleAdvertiserManager(
    private val context: Context,
    private val sniffer: BlePacketSniffer
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _packetsSentCount = MutableStateFlow(0)
    val packetsSentCount: StateFlow<Int> = _packetsSentCount.asStateFlow()

    private var activeCallback: AdvertiseCallback? = null

    @SuppressLint("MissingPermission")
    fun startAdvertising(packet: MessagePacket, connectable: Boolean = true): Boolean {
        if (!BlePermissions.hasAllPermissions(context)) {
            val msg = "Missing required BLE permissions to advertise"
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

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            val msg = "BLE Advertising not supported on this device hardware"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }

        // Stop any active advertisement first
        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0) // Run until explicitly stopped
            .build()

        val pUuid = ParcelUuid(MessagePacket.SERVICE_UUID)
        val packetBytes = packet.toByteArray()

        // BLE legacy advertising payload is limited to 31 bytes total (including headers).
        // If packet exceeds 24 bytes of service data, we send a compact header or full packet if supported
        val payloadData = if (packetBytes.size <= 24) {
            packetBytes
        } else {
            // Send truncated wire header + message ID for fast discovery
            "${packet.id.take(8)}|${packet.messageType}|${packet.senderId}".toByteArray()
        }

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(pUuid)
            .addServiceData(pUuid, payloadData)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
                _packetsSentCount.value += 1
                val msg = "Advertising STARTED [Type=${packet.messageType}, ID=${packet.id.take(8)}]"
                _statusMessage.value = msg
                sniffer.log(
                    direction = LogDirection.ADV,
                    message = msg,
                    rawBytes = payloadData,
                    parsedPacket = packet
                )
            }

            override fun onStartFailure(errorCode: Int) {
                _isAdvertising.value = false
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large for BLE advertisement"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising feature unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal hardware/driver error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers active"
                    else -> "Error code: $errorCode"
                }
                val msg = "Advertising FAILED: $reason"
                _statusMessage.value = msg
                sniffer.log(LogDirection.ERROR, msg)
            }
        }

        activeCallback = callback
        try {
            advertiser?.startAdvertising(settings, advertiseData, callback)
            _statusMessage.value = "Starting advertiser..."
            return true
        } catch (e: Exception) {
            val msg = "Exception starting advertiser: ${e.message}"
            _statusMessage.value = msg
            sniffer.log(LogDirection.ERROR, msg)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        activeCallback?.let { cb ->
            try {
                advertiser?.stopAdvertising(cb)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        activeCallback = null
        _isAdvertising.value = false
        _statusMessage.value = "Advertising stopped"
        sniffer.log(LogDirection.INFO, "Advertiser stopped")
    }
}
