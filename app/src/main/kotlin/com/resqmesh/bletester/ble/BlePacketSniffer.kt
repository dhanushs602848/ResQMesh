package com.resqmesh.bletester.ble

import com.resqmesh.bletester.model.MessagePacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogDirection {
    TX, RX, SCAN, ADV, GATT_SERVER, GATT_CLIENT, INFO, ERROR
}

data class PacketLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: LogDirection,
    val deviceAddress: String? = null,
    val rssi: Int? = null,
    val rawBytes: ByteArray? = null,
    val parsedPacket: MessagePacket? = null,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val hexString: String?
        get() = rawBytes?.joinToString(separator = " ") { "%02X".format(it) }
}

/**
 * Live circular packet log sniffer for monitoring BLE mesh activity in real time.
 */
class BlePacketSniffer(private val maxEntries: Int = 200) {

    private val _logs = MutableStateFlow<List<PacketLogEntry>>(emptyList())
    val logs: StateFlow<List<PacketLogEntry>> = _logs.asStateFlow()

    fun log(
        direction: LogDirection,
        message: String,
        deviceAddress: String? = null,
        rssi: Int? = null,
        rawBytes: ByteArray? = null,
        parsedPacket: MessagePacket? = null
    ) {
        val entry = PacketLogEntry(
            direction = direction,
            message = message,
            deviceAddress = deviceAddress,
            rssi = rssi,
            rawBytes = rawBytes,
            parsedPacket = parsedPacket
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > maxEntries) {
            current.removeAt(current.lastIndex)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
