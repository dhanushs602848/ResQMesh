package com.resqmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.resqmesh.app.data.ble.BleManager
import com.resqmesh.app.data.ble.BlePermissionHelper
import com.resqmesh.app.navigation.ResQMeshNavHost
import com.resqmesh.app.ui.theme.ResQMeshTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var bleManager: BleManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            bleManager.startMeshDiscovery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prompt user for Bluetooth Scan, Advertise, Connect, and Location permissions
        if (!BlePermissionHelper.hasPermissions(this)) {
            permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
        } else {
            bleManager.startMeshDiscovery()
        }

        setContent {
            ResQMeshTheme {
                ResQMeshNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BlePermissionHelper.hasPermissions(this)) {
            bleManager.startMeshDiscovery()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopMeshDiscovery()
    }
}
