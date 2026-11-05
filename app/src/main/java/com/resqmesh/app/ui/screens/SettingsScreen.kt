package com.resqmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.BackendConnectionTest
import com.resqmesh.app.data.ble.BleServiceStatus
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel
import com.resqmesh.security.model.SecurityProvisioningState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit,
    onReplayIntro: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToMesh: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToMap: () -> Unit = {}
) {
    val telemetry by (viewModel?.telemetry?.collectAsState() ?: remember { mutableStateOf(SystemTelemetry()) })
    val bleStatus by (viewModel?.bleStatus?.collectAsState() ?: remember { mutableStateOf(BleServiceStatus()) })
    val pendingUploadCount by (viewModel?.pendingUploadCount?.collectAsState() ?: remember { mutableStateOf(0) })
    val securityState by (viewModel?.securityState?.collectAsState() ?: remember { mutableStateOf(SecurityProvisioningState()) })
    val coroutineScope = rememberCoroutineScope()
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var isRetryingUploads by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ResQTopBar(title = "SETTINGS & PROFILE", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack,
        bottomBar = {
            FuturisticBottomBar(
                currentRoute = Screen.Settings.route,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.Dashboard -> onNavigateToHome()
                        Screen.MeshNetwork -> onNavigateToMesh()
                        Screen.Alerts -> onNavigateToAlerts()
                        Screen.RescueMap -> onNavigateToMap()
                        Screen.Settings -> {}
                        else -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Replay Cinematic Intro Hero Banner
            item {
                GlassCard(borderColor = ElectricCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.15f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "REPLAY 3D INTRO", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "Re-watch cinematic Earth introduction", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        Button(
                            onClick = onReplayIntro,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepSpaceBlack)
                        ) {
                            Text(text = "PLAY", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tactical Profile
            item {
                SettingsSection(title = "TACTICAL OPERATOR PROFILE") {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        label = "Callsign / Operator",
                        value = "ResQ Operator Alpha",
                        tint = ElectricCyan
                    )
                    SettingsRow(
                        icon = Icons.Default.Security,
                        label = "Mesh Node Hardware ID",
                        value = bleStatus.localNodeId.ifBlank { "RQM-LOCAL" },
                        tint = ElectricCyan
                    )
                    SettingsRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "Battery Reserve",
                        value = "${telemetry.batteryPercent}% (Normal)",
                        tint = SafeGreen
                    )
                }
            }

            // Backend Uplink & Cloud Gateway Connection Test
            item {
                SettingsSection(title = "BACKEND UPLINK & CLOUD GATEWAY") {
                    SettingsRow(
                        icon = Icons.Default.Hub,
                        label = "Server Endpoint",
                        value = "http://172.16.19.160:3000/",
                        tint = ElectricCyan
                    )

                    SettingsRow(
                        icon = if (pendingUploadCount == 0) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        label = "Offline SOS Queue",
                        value = if (pendingUploadCount == 0) "Synchronized (0 pending)" else "$pendingUploadCount pending upload",
                        tint = if (pendingUploadCount == 0) SafeGreen else WarningAmber
                    )

                    if (pendingUploadCount > 0) {
                        Button(
                            onClick = {
                                if (!isRetryingUploads) {
                                    isRetryingUploads = true
                                    coroutineScope.launch {
                                        viewModel?.retryUploads()
                                        isRetryingUploads = false
                                    }
                                }
                            },
                            enabled = !isRetryingUploads,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarningAmber,
                                contentColor = DeepSpaceBlack,
                                disabledContainerColor = WarningAmber.copy(alpha = 0.4f),
                                disabledContentColor = DeepSpaceBlack.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isRetryingUploads) "RETRYING UPLOADS..." else "Retry Pending Uploads ($pendingUploadCount)",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    connectionStatus?.let { status ->
                        val isSuccess = status.startsWith("CONNECTED")
                        val statusColor = if (isSuccess) SafeGreen else EmergencyRed

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PulsingDot(color = statusColor, size = 8.dp)
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isTestingConnection) {
                                isTestingConnection = true
                                connectionStatus = "Connecting to backend..."
                                coroutineScope.launch {
                                    connectionStatus = BackendConnectionTest.testConnection()
                                    isTestingConnection = false
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = DeepSpaceBlack,
                            disabledContainerColor = ElectricCyan.copy(alpha = 0.4f),
                            disabledContentColor = DeepSpaceBlack.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isTestingConnection) "TESTING CONNECTION..." else "Test Backend Connection",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Connectivity Permissions
            item {
                SettingsSection(title = "MESH & RADIO CHANNELS") {
                    ToggleRow(
                        icon = Icons.Default.Bluetooth,
                        label = "Bluetooth LE Mesh Discovery",
                        subtitle = "Autonomous peer relay exchange",
                        tint = ElectricCyan,
                        defaultChecked = telemetry.isBluetoothEnabled
                    )
                    ToggleRow(
                        icon = Icons.Default.GpsFixed,
                        label = "Offline GPS Geolocation",
                        subtitle = "Attach rescue coordinates to SOS beacons",
                        tint = SafeGreen,
                        defaultChecked = telemetry.isLocationEnabled
                    )
                }
            }

            // Emergency Alert Preferences
            item {
                SettingsSection(title = "DISTRESS NOTIFICATION PREFERENCES") {
                    ToggleRow(
                        icon = Icons.Default.Notifications,
                        label = "High Priority Audio Siren",
                        subtitle = "Bypass Do-Not-Disturb on valid SOS packets",
                        tint = WarningAmber,
                        defaultChecked = true
                    )
                    ToggleRow(
                        icon = Icons.Default.Vibration,
                        label = "Tactical Haptic Vibration",
                        subtitle = "Vibrate pulse on inbound peer relays",
                        tint = WarningAmber,
                        defaultChecked = true
                    )
                }
            }

            // Responder Security (Phase 3 Foundation)
            item {
                SettingsSection(title = "RESPONDER MESH SECURITY (PHASE 3)") {
                    SettingsRow(
                        icon = Icons.Default.Security,
                        label = "Security Module",
                        value = "AES-256-GCM AEAD",
                        tint = ElectricCyan
                    )
                    SettingsRow(
                        icon = Icons.Default.VpnKey,
                        label = "Key Storage Backing",
                        value = if (securityState.isHardwareBacked) "Hardware Keystore (TEE)" else "Android Keystore",
                        tint = if (securityState.isHardwareBacked) SafeGreen else ElectricCyan
                    )
                    SettingsRow(
                        icon = Icons.Default.Lock,
                        label = "Provisioning Status",
                        value = securityState.statusMessage,
                        tint = if (securityState.isKeyProvisioned) SafeGreen else WarningAmber
                    )
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = "Provisioned Key ID",
                        value = securityState.activeKeyId ?: "None (Unprovisioned)",
                        tint = TextSecondary
                    )
                    SettingsRow(
                        icon = Icons.Default.Fingerprint,
                        label = "SHA-256 Fingerprint",
                        value = securityState.keyFingerprint?.take(16)?.let { "$it..." } ?: "None",
                        tint = TextSecondary
                    )
                    SettingsRow(
                        icon = Icons.Default.Hub,
                        label = "Live BLE Route",
                        value = "UNCONNECTED (Safety Isolated)",
                        tint = WarningAmber
                    )

                    // Ephemeral Test Key Controls (Testing & verification; no hardcoded keys)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel?.provisionEphemeralTestKey()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepSpaceBlack
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "TEST PROVISION", fontWeight = FontWeight.Bold)
                        }

                        if (securityState.isKeyProvisioned) {
                            Button(
                                onClick = {
                                    viewModel?.removeProvisionedKey(securityState.activeKeyId ?: "responder-group-test")
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmergencyRed,
                                    contentColor = DeepSpaceBlack
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "CLEAR KEY", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // About Specification
            item {
                SettingsSection(title = "SYSTEM SPECIFICATION") {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = "Application Version",
                        value = "1.0.0 (Hackathon Release)",
                        tint = TextSecondary
                    )
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = "Mesh Protocol Spec",
                        value = "RQM-FLOOD-V1 • Max Hops: 7",
                        tint = TextSecondary
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        ResQCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    defaultChecked: Boolean = false
) {
    var checked by remember { mutableStateOf(defaultChecked) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = tint,
                checkedTrackColor = tint.copy(alpha = 0.35f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.2f)
            )
        )
    }
}
