package com.resqmesh.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.ble.BleOperationalState
import com.resqmesh.app.data.ble.BlePermissionHelper
import com.resqmesh.app.data.ble.BleServiceStatus
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.GlowCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.SurfaceCard
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshNetworkScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val liveNodes by (viewModel?.nodes?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val telemetry by (viewModel?.telemetry?.collectAsState() ?: remember { mutableStateOf(SystemTelemetry()) })
    val bleStatus by (viewModel?.bleStatus?.collectAsState() ?: remember { mutableStateOf(BleServiceStatus()) })
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel?.refreshMeshTopology()
        }
    }

    val activeRelaysCount = liveNodes.count { it.isRelayCapable }
    val maxHops = liveNodes.maxOfOrNull { it.hopCount } ?: if (liveNodes.isNotEmpty()) 1 else 0

    Scaffold(
        topBar = {
            ResQTopBar(title = "MESH TOPOLOGY MAP", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack,
        bottomBar = {
            FuturisticBottomBar(
                currentRoute = Screen.MeshNetwork.route,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.Dashboard -> onNavigateToHome()
                        Screen.MeshNetwork -> {}
                        Screen.Alerts -> onNavigateToAlerts()
                        Screen.RescueMap -> onNavigateToMap()
                        Screen.Settings -> onNavigateToSettings()
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
            // BLE Operational State Banner
            item {
                when (bleStatus.state) {
                    BleOperationalState.PERMISSIONS_REQUIRED -> {
                        GlassCard(borderColor = WarningAmber) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "BLUETOOTH PERMISSIONS REQUIRED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = WarningAmber)
                                }
                                Text(
                                    text = "Nearby peer discovery and autonomous relay require Bluetooth Scan, Advertise, and Connect permissions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepSpaceBlack),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("GRANT BLUETOOTH PERMISSIONS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    BleOperationalState.BLUETOOTH_DISABLED -> {
                        GlassCard(borderColor = EmergencyRed) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.BluetoothDisabled, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(24.dp))
                                Column {
                                    Text(text = "BLUETOOTH IS TURNED OFF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = EmergencyRed)
                                    Text(text = "Turn ON Bluetooth in Android Settings to discover mesh peers", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                    BleOperationalState.UNSUPPORTED -> {
                        GlassCard(borderColor = EmergencyRed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BluetoothDisabled, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = "BLUETOOTH LE NOT SUPPORTED ON THIS DEVICE", style = MaterialTheme.typography.bodyMedium, color = EmergencyRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        // Fully operational: Scanning and Advertising
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SafeGreen.copy(alpha = 0.10f))
                                .border(1.dp, SafeGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PulsingDot(color = SafeGreen, size = 8.dp)
                                    Text(
                                        text = bleStatus.statusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafeGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = bleStatus.localNodeId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 1. Central Radar Visualization
            item {
                MeshTopologyRadarCard()
            }

            // 2. Topology Telemetry Metrics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "PEERS IN RANGE",
                        value = "${liveNodes.size}",
                        tint = ElectricCyan
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "MAX HOPS",
                        value = "$maxHops",
                        tint = DeepBlueNeon
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        label = "RELAY HUBS",
                        value = "$activeRelaysCount",
                        tint = SafeGreen
                    )
                }
            }

            // 3. Discovered Peer Nodes Section Header & Refresh Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DISCOVERED PEERS (${liveNodes.size} IN RANGE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PulsingDot(color = if (liveNodes.isNotEmpty()) SafeGreen else WarningAmber, size = 8.dp)
                    }

                    OutlinedButton(
                        onClick = { viewModel?.refreshMeshTopology() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "SCAN", fontSize = 10.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Discovered Peer Nodes List or Empty State
            if (liveNodes.isEmpty()) {
                item {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.WifiTethering, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                            Text(
                                text = "NO PEERS FOUND IN RANGE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Actively scanning on Service UUID 7b1f3c8a... Bring another physical device running ResQMesh nearby to form a peer mesh link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(liveNodes.size) { index ->
                    PeerNodeItemCard(peer = liveNodes[index])
                }
            }

            // 4. Physical Medium Capabilities
            item {
                MediumCapabilitiesCard()
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MeshTopologyRadarCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    GlowCard(glowColor = ElectricCyan) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Hub, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "INTERACTIVE MESH RADAR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text(text = "BLE PHY 5.0", style = MaterialTheme.typography.labelSmall, color = ElectricCyan, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassNavy)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val maxRadius = size.minDimension / 2

                    // Concentric Range Rings
                    for (i in 1..4) {
                        drawCircle(
                            color = ElectricCyan.copy(alpha = 0.12f * i),
                            radius = maxRadius * (i / 4f),
                            center = center,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )
                    }

                    // Crosshair Axes
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.25f),
                        start = Offset(center.x, 0f),
                        end = Offset(center.x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.25f),
                        start = Offset(0f, center.y),
                        end = Offset(size.width, center.y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Rotating Radar Sweep Beam
                    val rad = Math.toRadians(sweepAngle.toDouble())
                    val sweepEnd = Offset(
                        x = (center.x + maxRadius * cos(rad)).toFloat(),
                        y = (center.y + maxRadius * sin(rad)).toFloat()
                    )
                    drawLine(
                        brush = Brush.radialGradient(
                            colors = listOf(ElectricCyan, Color.Transparent),
                            center = center,
                            radius = maxRadius
                        ),
                        start = center,
                        end = sweepEnd,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Local Host Center Indicator
                    drawCircle(color = SafeGreen, radius = 6.dp.toPx(), center = center)
                    drawCircle(color = SafeGreen.copy(alpha = 0.4f), radius = 10.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Autonomous Scanning & Advertising Enabled (128-bit UUID: 7b1f3c8a)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tint: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlassNavy)
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = tint)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun PeerNodeItemCard(peer: MeshNode) {
    val isRelay = peer.isRelayCapable
    val accentColor = if (isRelay) SafeGreen else ElectricCyan

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRelay) Icons.Default.Router else Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = peer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "${peer.id} • ${peer.status.name} • 1 Hop (Direct BLE)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCard)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${peer.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MediumCapabilitiesCard() {
    ResQCard {
        Column {
            Text(
                text = "SUPPORTED PHYSICAL TRANSPORT LAYERS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val mediums = listOf(
                Triple(Icons.Default.Bluetooth, "BLE 5.0 Broadcast & Scan", "Active on Service UUID 7b1f3c8a"),
                Triple(Icons.Default.WifiTethering, "Wi-Fi Aware / Direct", "Available for high-throughput payload exchange"),
                Triple(Icons.Default.Router, "LoRa Sub-GHz Gateway (Optional)", "Interface hook for SX1262 / SX1276 serial modules")
            )

            mediums.forEachIndexed { index, (icon, title, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Text(text = if (index == 0) "ACTIVE" else "STANDBY", style = MaterialTheme.typography.labelSmall, color = if (index == 0) SafeGreen else TextSecondary, fontWeight = FontWeight.Bold)
                }
                if (index < mediums.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
