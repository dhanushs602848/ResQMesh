package com.resqmesh.bletester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.resqmesh.bletester.ble.BleAdvertiserManager
import com.resqmesh.bletester.ble.BleGattClientManager
import com.resqmesh.bletester.ble.BleGattServerManager
import com.resqmesh.bletester.ble.BlePacketSniffer
import com.resqmesh.bletester.ble.BlePermissions
import com.resqmesh.bletester.ble.BleScannerManager
import com.resqmesh.bletester.ui.BleTesterScreen
import com.resqmesh.bletester.ui.theme.ResQMeshBleTesterTheme

class MainActivity : ComponentActivity() {

    private lateinit var sniffer: BlePacketSniffer
    private lateinit var scannerManager: BleScannerManager
    private lateinit var advertiserManager: BleAdvertiserManager
    private lateinit var serverManager: BleGattServerManager
    private lateinit var clientManager: BleGattClientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sniffer = BlePacketSniffer()
        scannerManager = BleScannerManager(this, sniffer)
        advertiserManager = BleAdvertiserManager(this, sniffer)
        serverManager = BleGattServerManager(this, sniffer)
        clientManager = BleGattClientManager(this, sniffer)

        enableEdgeToEdge()
        setContent {
            ResQMeshBleTesterTheme {
                var hasPermissions by remember {
                    mutableStateOf(BlePermissions.hasAllPermissions(this@MainActivity))
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = BlePermissions.hasAllPermissions(this@MainActivity)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BleTesterScreen(
                        scannerManager = scannerManager,
                        advertiserManager = advertiserManager,
                        serverManager = serverManager,
                        clientManager = clientManager,
                        sniffer = sniffer,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = {
                            permissionLauncher.launch(BlePermissions.getRequiredPermissions())
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerManager.stopScanning()
        advertiserManager.stopAdvertising()
        serverManager.stopServer()
        clientManager.close()
    }
}
