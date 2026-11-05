package com.resqmesh.app.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.Earth3DGlobe
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.GlowCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.EmergencyRedDark
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.SurfaceCard
import com.resqmesh.app.ui.theme.SurfaceDarkVoid
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

@Composable
fun DashboardScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateToSos: () -> Unit,
    onNavigateToMesh: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val listState = rememberLazyListState()

    val telemetry by (viewModel?.telemetry?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(SystemTelemetry()) })
    val nodes by (viewModel?.nodes?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) })
    val broadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(emptyList()) })

    // SCROLL-LINKED PARALLAX CALCULATIONS
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val firstVisibleItemScrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

    val earthScale by remember {
        derivedStateOf {
            if (firstVisibleItemIndex > 0) 0.65f
            else (1f - (firstVisibleItemScrollOffset / 850f) * 0.35f).coerceIn(0.65f, 1f)
        }
    }

    val earthAlpha by remember {
        derivedStateOf {
            if (firstVisibleItemIndex > 1) 0.3f
            else (1f - (firstVisibleItemScrollOffset / 700f) * 0.5f).coerceIn(0.3f, 1f)
        }
    }

    val showCompactFloatingSos by remember {
        derivedStateOf {
            firstVisibleItemIndex > 1 || (firstVisibleItemIndex == 1 && firstVisibleItemScrollOffset > 300)
        }
    }

    Scaffold(
        containerColor = DeepSpaceBlack,
        bottomBar = {
            FuturisticBottomBar(
                currentRoute = Screen.Dashboard.route,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.Dashboard -> {}
                        Screen.MeshNetwork -> onNavigateToMesh()
                        Screen.Alerts -> onNavigateToAlerts()
                        Screen.RescueMap -> onNavigateToMap()
                        Screen.Settings -> onNavigateToSettings()
                        else -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Futuristic Header
                item {
                    DashboardHeader(
                        activeSos = telemetry.activeSosBeacon,
                        onNavigateToAlerts = onNavigateToAlerts,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }

                // 2. Large Interactive 3D Earth Globe Visual with Scroll Parallax & Drag
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                            .graphicsLayer {
                                scaleX = earthScale
                                scaleY = earthScale
                                alpha = earthAlpha
                                translationY = -(firstVisibleItemScrollOffset * 0.25f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Earth3DGlobe(
                            modifier = Modifier.size(270.dp),
                            showNetworkMesh = true,
                            showEmergencyPulse = telemetry.activeSosBeacon
                        )
                    }
                }

                // 3. Centerpiece SOS Emergency Trigger Control
                item {
                    PrimarySosSection(
                        isBeaconActive = telemetry.activeSosBeacon,
                        onClick = onNavigateToSos,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 4. Tactical Status Ribbon
                item {
                    TacticalStatusRibbon(
                        telemetry = telemetry,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    SubsystemPermissionWarning(telemetry, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }

                // 5. Hardware Subsystem Grid
                item {
                    HardwareSubsystemsSection(
                        telemetry = telemetry,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 6. Mesh Network Realtime Metrics Card
                item {
                    MeshMetricsCard(
                        peerCount = nodes.size,
                        onNavigateToMesh = onNavigateToMesh,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // 7. Recent Emergency Broadcasts Preview
                item {
                    RecentAlertsPreviewCard(
                        alertCount = broadcasts.size,
                        recentTitle = broadcasts.firstOrNull()?.title,
                        onNavigateToAlerts = onNavigateToAlerts,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Floating Compact SOS Button
            AnimatedVisibility(
                visible = showCompactFloatingSos,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                FloatingCompactSosButton(
                    isBeaconActive = telemetry.activeSosBeacon,
                    onClick = onNavigateToSos
                )
            }
        }
    }
}

@Composable
private fun SubsystemPermissionWarning(telemetry: SystemTelemetry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissions = remember(Build.VERSION.SDK_INT) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
    }
    val missing = remember(permissionRefresh) {
        permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }
    val hasBluetoothConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val bluetoothRadioUnavailable = hasBluetoothConnect && BluetoothAdapter.getDefaultAdapter()?.isEnabled != true
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val deviceLocationDisabled = !LocationManagerCompat.isLocationEnabled(locationManager)
    val bluetoothUnavailable = !telemetry.isBluetoothEnabled || bluetoothRadioUnavailable
    val locationUnavailable = !telemetry.isLocationEnabled || deviceLocationDisabled
    if (missing.isNotEmpty() || bluetoothUnavailable || locationUnavailable) {
        GlassCard(borderColor = WarningAmber.copy(alpha = 0.55f), modifier = modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SUBSYSTEM NEEDS ATTENTION", color = WarningAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (missing.isNotEmpty()) Text("Bluetooth or location permissions are missing. Grant them to enable the related features.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (bluetoothUnavailable) Text("Bluetooth is reported as disabled.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (locationUnavailable) Text("Location is reported as disabled.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                    else context.startActivity(
                        Intent(if (bluetoothUnavailable) Settings.ACTION_BLUETOOTH_SETTINGS else Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    )
                }) {
                    Text(if (missing.isNotEmpty()) "GRANT PERMISSIONS" else "OPEN BLUETOOTH SETTINGS", color = ElectricCyan)
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    activeSos: Boolean,
    onNavigateToAlerts: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "RESQMESH",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                PulsingDot(color = if (activeSos) EmergencyRed else SafeGreen, size = 8.dp)
            }
            Text(
                text = if (activeSos) "EMERGENCY BEACON ACTIVE" else "EMERGENCY MESH NETWORK",
                style = MaterialTheme.typography.labelSmall,
                color = if (activeSos) EmergencyRed else ElectricCyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onNavigateToAlerts,
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceGlassNavy, CircleShape)
                    .border(1.dp, BorderGlass, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts",
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceGlassNavy, CircleShape)
                    .border(1.dp, BorderGlass, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PrimarySosSection(
    isBeaconActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "primary_sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isBeaconActive) 1.25f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBeaconActive) 600 else 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_halo"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulseScale)
                    .background(EmergencyRed.copy(alpha = if (isBeaconActive) 0.25f else 0.12f), CircleShape)
                    .border(1.5.dp, EmergencyRed.copy(alpha = if (isBeaconActive) 0.6f else 0.35f), CircleShape)
            )

            Button(
                onClick = onClick,
                modifier = Modifier.size(136.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sos,
                        contentDescription = "SOS",
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = if (isBeaconActive) "SOS ACTIVE" else "SEND SOS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isBeaconActive) "BEACON TRANSMITTING TO MESH RELAYS" else "BROADCAST EMERGENCY ALERT TO PEERS",
            style = MaterialTheme.typography.labelSmall,
            color = if (isBeaconActive) EmergencyRed else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun FloatingCompactSosButton(
    isBeaconActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        EmergencyRed,
                        EmergencyRedDark
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingDot(color = Color.White, size = 8.dp)
            Icon(
                imageVector = Icons.Default.Sos,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (isBeaconActive) "SOS TRANSMITTING" else "EMERGENCY SOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TacticalStatusRibbon(
    telemetry: SystemTelemetry,
    modifier: Modifier = Modifier
) {
    val statusColor = if (telemetry.activeSosBeacon) EmergencyRed else SafeGreen

    GlowCard(
        glowColor = statusColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (telemetry.activeSosBeacon) "SOS BEACON PROPAGATING" else "CORE PROTOCOL ACTIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${telemetry.connectedPeerCount} mesh peers • ${telemetry.totalPacketsRelayed} packets routed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            PulsingDot(color = statusColor, size = 10.dp)
        }
    }
}

@Composable
private fun HardwareSubsystemsSection(
    telemetry: SystemTelemetry,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubsystemChip(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bluetooth,
            label = "BLE MESH",
            status = if (telemetry.isBluetoothEnabled) "READY" else "OFF",
            tint = if (telemetry.isBluetoothEnabled) ElectricCyan else TextSecondary
        )
        SubsystemChip(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.GpsFixed,
            label = "GPS",
            status = if (telemetry.isLocationEnabled) "FIXED" else "OFFLINE",
            tint = if (telemetry.isLocationEnabled) SafeGreen else WarningAmber
        )
        SubsystemChip(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.WifiTethering,
            label = "P2P RELAY",
            status = if (telemetry.isMeshRelayActive) "ARMED" else "PAUSED",
            tint = if (telemetry.isMeshRelayActive) SafeGreen else WarningAmber
        )
    }
}

@Composable
private fun SubsystemChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    status: String,
    tint: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlassNavy)
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = tint,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun MeshMetricsCard(
    peerCount: Int,
    onNavigateToMesh: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        borderColor = ElectricCyan,
        onClick = onNavigateToMesh,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NETWORK TELEMETRY",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "EXPAND ➔",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                TelemetryStatItem(label = "PEER NODES", value = peerCount.toString(), unit = "NODES", color = ElectricCyan)
                TelemetryStatItem(label = "HOP RADIUS", value = "4", unit = "MAX", color = DeepBlueNeon)
                TelemetryStatItem(label = "LATENCY", value = "<40", unit = "MS", color = SafeGreen)
            }
        }
    }
}

@Composable
private fun TelemetryStatItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun RecentAlertsPreviewCard(
    alertCount: Int,
    recentTitle: String?,
    onNavigateToAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResQCard(
        onClick = onNavigateToAlerts,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EMERGENCY BROADCASTS ($alertCount)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "VIEW FEED",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = recentTitle ?: "System initialized in tactical mesh monitoring mode. Stand by for inbound packet relays.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
