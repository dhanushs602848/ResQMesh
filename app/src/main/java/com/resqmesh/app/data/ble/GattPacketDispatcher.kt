package com.resqmesh.app.data.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.resqmesh.app.data.model.MeshPacket
import com.resqmesh.app.data.model.MeshPacketFragmenter
import com.resqmesh.app.data.model.MeshPacketType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResQMesh-GattClient"

/**
 * Interface responsible for one-hop point-to-point BLE GATT transmission to a remote peer.
 */
interface GattPacketDispatcher {
    suspend fun dispatchToPeer(
        device: BluetoothDevice,
        deviceName: String,
        packet: MeshPacket,
        timeoutMs: Long = 12_000L
    ): BleDispatchResult
}

/**
 * Production implementation of [GattPacketDispatcher].
 * Handles connection initiation, MTU negotiation, service & characteristic discovery,
 * sequential packet/fragment transmission, confirmed write callbacks, and safe cleanup.
 */
@Singleton
class DefaultGattPacketDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) : GattPacketDispatcher {

    override suspend fun dispatchToPeer(
        device: BluetoothDevice,
        deviceName: String,
        packet: MeshPacket,
        timeoutMs: Long
    ): BleDispatchResult {
        val address = device.address ?: "UNKNOWN"

        // 1. Runtime permission validation
        if (!BlePermissionHelper.hasPermissions(context)) {
            Log.w(TAG, "Cannot dispatch to $address ($deviceName): Bluetooth permissions missing")
            return BleDispatchResult(
                peerAddress = address,
                peerName = deviceName,
                isDelivered = false,
                error = "Bluetooth runtime permissions not granted"
            )
        }

        val targetCharUuid = when (packet.packetType) {
            MeshPacketType.SOS -> BleConstants.SOS_CHARACTERISTIC_UUID
            MeshPacketType.ACK -> BleConstants.ACK_CHARACTERISTIC_UUID
        }

        var gatt: BluetoothGatt? = null

        return try {
            withTimeout(timeoutMs) {
                val connectDeferred = CompletableDeferred<BluetoothGatt>()
                val mtuDeferred = CompletableDeferred<Int>()
                val servicesDeferred = CompletableDeferred<Int>()
                var activeWriteDeferred: CompletableDeferred<Int>? = null

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        Log.d(TAG, "GATT connection state change for $address: status=$status, newState=$newState")
                        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                            connectDeferred.complete(g)
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                            val errorMsg = "GATT disconnected or failed (status=$status, state=$newState)"
                            val ex = IOException(errorMsg)
                            connectDeferred.completeExceptionally(ex)
                            mtuDeferred.completeExceptionally(ex)
                            servicesDeferred.completeExceptionally(ex)
                            activeWriteDeferred?.completeExceptionally(ex)
                        }
                    }

                    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                        Log.d(TAG, "MTU changed for $address to $mtu (status=$status)")
                        mtuDeferred.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        Log.d(TAG, "Services discovered for $address (status=$status)")
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            servicesDeferred.complete(status)
                        } else {
                            servicesDeferred.completeExceptionally(IOException("Service discovery failed with status $status"))
                        }
                    }

                    override fun onCharacteristicWrite(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        Log.d(TAG, "Characteristic write completed for $address on ${characteristic.uuid} (status=$status)")
                        activeWriteDeferred?.complete(status)
                    }
                }

                // 2. Establish BLE GATT connection
                Log.i(TAG, "Connecting GATT client to $address ($deviceName)...")
                val connectedGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }

                if (connectedGatt == null) {
                    return@withTimeout BleDispatchResult(
                        peerAddress = address,
                        peerName = deviceName,
                        isDelivered = false,
                        error = "device.connectGatt() returned null"
                    )
                }
                gatt = connectedGatt

                // Await connection completion
                connectDeferred.await()

                // 3. Negotiate high MTU (512 bytes)
                var negotiatedMtu = 23
                try {
                    val requested = connectedGatt.requestMtu(512)
                    if (requested) {
                        negotiatedMtu = try {
                            kotlinx.coroutines.withTimeoutOrNull(2000L) { mtuDeferred.await() } ?: 23
                        } catch (_: Exception) {
                            23
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "MTU negotiation skipped: ${e.message}")
                    negotiatedMtu = 23
                }

                // 4. Discover remote GATT services
                connectedGatt.discoverServices()
                servicesDeferred.await()

                val service = connectedGatt.getService(BleConstants.RESQMESH_SERVICE_UUID)
                if (service == null) {
                    Log.w(TAG, "Peer $address does not host ResQMesh service ${BleConstants.RESQMESH_SERVICE_UUID_STRING}")
                    return@withTimeout BleDispatchResult(
                        peerAddress = address,
                        peerName = deviceName,
                        isDelivered = false,
                        error = "ResQMesh GATT service not found on peer"
                    )
                }

                val characteristic = service.getCharacteristic(targetCharUuid)
                if (characteristic == null) {
                    Log.w(TAG, "Peer $address does not host target characteristic $targetCharUuid")
                    return@withTimeout BleDispatchResult(
                        peerAddress = address,
                        peerName = deviceName,
                        isDelivered = false,
                        error = "Target characteristic $targetCharUuid not found on peer"
                    )
                }

                // 5. Prepare byte payload and fragments
                val rawBytes = packet.toByteArray()
                val maxPayloadPerWrite = (negotiatedMtu - 3).coerceAtLeast(20)

                val chunksToWrite: List<ByteArray> = if (rawBytes.size <= maxPayloadPerWrite) {
                    listOf(rawBytes)
                } else {
                    // Fragment using standard MeshPacketFragmenter
                    val chunkDataLimit = (maxPayloadPerWrite - 25).coerceAtLeast(20)
                    val fragments = MeshPacketFragmenter.fragment(packet, maxChunkSize = chunkDataLimit)
                    fragments.map { it.toByteArray() }
                }

                Log.i(
                    TAG,
                    "Writing ${chunksToWrite.size} chunk(s) (total ${rawBytes.size} bytes) to $address for msg [${packet.messageId}]"
                )

                // 6. Write chunks sequentially, awaiting confirmed onCharacteristicWrite for each
                for (index in chunksToWrite.indices) {
                    val chunk = chunksToWrite[index]
                    val writeDeferred = CompletableDeferred<Int>()
                    activeWriteDeferred = writeDeferred

                    val writeInitiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        connectedGatt.writeCharacteristic(
                            characteristic,
                            chunk,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        ) == BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = chunk
                        @Suppress("DEPRECATION")
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        connectedGatt.writeCharacteristic(characteristic)
                    }

                    if (!writeInitiated) {
                        return@withTimeout BleDispatchResult(
                            peerAddress = address,
                            peerName = deviceName,
                            isDelivered = false,
                            error = "writeCharacteristic returned false for chunk $index"
                        )
                    }

                    val writeStatus = writeDeferred.await()
                    if (writeStatus != BluetoothGatt.GATT_SUCCESS) {
                        return@withTimeout BleDispatchResult(
                            peerAddress = address,
                            peerName = deviceName,
                            isDelivered = false,
                            error = "GATT write failed with status $writeStatus on chunk $index"
                        )
                    }
                }

                Log.i(TAG, "Confirmed delivery of [${packet.messageId}] to $address ($deviceName)")
                BleDispatchResult(
                    peerAddress = address,
                    peerName = deviceName,
                    isDelivered = true,
                    fragmentsSent = chunksToWrite.size
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "GATT dispatch to $address timed out after ${timeoutMs}ms")
            BleDispatchResult(
                peerAddress = address,
                peerName = deviceName,
                isDelivered = false,
                error = "Transmission timed out after ${timeoutMs}ms"
            )
        } catch (e: Exception) {
            Log.e(TAG, "GATT dispatch to $address failed: ${e.message}")
            BleDispatchResult(
                peerAddress = address,
                peerName = deviceName,
                isDelivered = false,
                error = e.message ?: "GATT transmission failed"
            )
        } finally {
            // 7. Unconditionally disconnect and release GATT client resources
            try {
                gatt?.disconnect()
            } catch (_: Exception) {}
            try {
                gatt?.close()
            } catch (_: Exception) {}
        }
    }
}
