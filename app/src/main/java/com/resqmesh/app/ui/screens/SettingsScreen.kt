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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

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
                        value = "RQM-7701-ALPHA",
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
