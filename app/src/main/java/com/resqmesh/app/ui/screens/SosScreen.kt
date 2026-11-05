package com.resqmesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.resqmesh.app.data.location.LocationState
import com.resqmesh.app.data.location.LocationStatus
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.data.model.UploadStatus
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.GlowCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.BorderEmergency
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.EmergencyRedDark
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

enum class EmergencyType(val label: String, val icon: ImageVector, val color: Color, val priority: IncidentPriority) {
    MEDICAL("Medical Assistance", Icons.Default.LocalHospital, EmergencyRed, IncidentPriority.CRITICAL),
    DISASTER("Natural Disaster", Icons.Default.LocalFireDepartment, WarningAmber, IncidentPriority.HIGH),
    TRAPPED("Trapped / Rescue", Icons.Default.Emergency, ElectricCyan, IncidentPriority.CRITICAL),
    SECURITY("Security Threat", Icons.Default.Security, WarningAmber, IncidentPriority.HIGH)
}

@Composable
fun SosScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val telemetry by (viewModel?.telemetry?.collectAsState() ?: remember { mutableStateOf(SystemTelemetry()) })
    val locationState by (viewModel?.locationState?.collectAsState() ?: remember { mutableStateOf(LocationState()) })
    val location by (viewModel?.location?.collectAsState() ?: remember { mutableStateOf(null) })
    val latestUploadStatus by (viewModel?.latestUploadStatus?.collectAsState() ?: remember { mutableStateOf(null) })
    val broadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    var localSosActive by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel?.refreshMeshTopology()
    }

    // Reflect ViewModel state or fallback to local toggle
    val isSosActive = telemetry.activeSosBeacon || localSosActive
    val activeLocalSos = broadcasts.firstOrNull { !it.isReceived && !it.isDemo }
    var selectedEmergencyType by remember { mutableStateOf(EmergencyType.MEDICAL) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ResQTopBar(title = "SOS EMERGENCY BROADCAST", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Status Indicator Header
            item {
                GlowCard(glowColor = if (isSosActive) EmergencyRed else SafeGreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingDot(
                                color = if (isSosActive) EmergencyRed else SafeGreen,
                                size = 12.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isSosActive) "BROADCAST TRANSMITTING" else "STANDBY (READY TO BROADCAST)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSosActive) EmergencyRed else SafeGreen,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isSosActive) "Multi-hop relay propagating to peer nodes" else "Tap below to initiate emergency distress signal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Animated SOS Center Button
            item {
                AnimatedDistressButton(
                    isActive = isSosActive,
                    onClick = {
                        if (isSosActive) {
                            viewModel?.cancelSos()
                            localSosActive = false
                        } else {
                            showConfirmDialog = true
                        }
                    }
                )
            }

            // 3. Emergency Type Selector Grid
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "INCIDENT CLASSIFICATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmergencyType.entries.take(2).forEach { type ->
                            EmergencyTypeChip(
                                type = type,
                                isSelected = selectedEmergencyType == type,
                                onClick = { selectedEmergencyType = type },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmergencyType.entries.drop(2).forEach { type ->
                            EmergencyTypeChip(
                                type = type,
                                isSelected = selectedEmergencyType == type,
                                onClick = { selectedEmergencyType = type },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. Incident Telemetry Capsule Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val (gpsLabel, gpsTint) = when (locationState.status) {
                        LocationStatus.FIXED -> "FIXED" to SafeGreen
                        LocationStatus.SEARCHING -> "SEARCHING" to WarningAmber
                        LocationStatus.STALE -> "STALE" to WarningAmber
                        LocationStatus.PERMISSION_DENIED -> "PERMISSION DENIED" to EmergencyRed
                        LocationStatus.LOCATION_DISABLED -> "LOCATION DISABLED" to EmergencyRed
                        LocationStatus.UNAVAILABLE -> "UNAVAILABLE" to EmergencyRed
                    }
                    TelemetryPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MyLocation,
                        label = "GPS STATUS",
                        value = gpsLabel,
                        tint = gpsTint
                    )
                    TelemetryPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.People,
                        label = "PEER RELAYS",
                        value = "${telemetry.connectedPeerCount} ONLINE",
                        tint = ElectricCyan
                    )
                    TelemetryPill(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocationOn,
                        label = "PROPAGATION",
                        value = if (isSosActive) "ACTIVE" else "STANDBY",
                        tint = if (isSosActive) EmergencyRed else SafeGreen
                    )
                }
            }

            // 4a. Live GPS Location & Satellite Telemetry Card
            item {
                LocationTelemetryCard(
                    locationState = locationState,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onOpenLocationSettings = {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (_: Exception) {}
                    }
                )
            }

            // 4b. Cloud Uplink Status Card
            if (latestUploadStatus != null && latestUploadStatus != UploadStatus.NOT_APPLICABLE) {
                item {
                    val status = latestUploadStatus!!
                    val statusColor = when (status) {
                        UploadStatus.UPLOADED -> SafeGreen
                        UploadStatus.UPLOADING -> ElectricCyan
                        UploadStatus.PENDING -> WarningAmber
                        UploadStatus.FAILED -> EmergencyRed
                        else -> ElectricCyan
                    }
                    val statusText = when (status) {
                        UploadStatus.UPLOADED -> "UPLOADED: Stored on ResQMesh Cloud"
                        UploadStatus.UPLOADING -> "UPLOADING: Transmitting to Backend..."
                        UploadStatus.PENDING -> "PENDING: Queued in Offline Storage"
                        UploadStatus.FAILED -> "FAILED / QUEUED FOR RETRY"
                        else -> "LOCAL ONLY"
                    }

                    GlassCard(borderColor = statusColor.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                PulsingDot(color = statusColor, size = 10.dp)
                                Column {
                                    Text(
                                        text = "BACKEND CLOUD UPLINK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (status == UploadStatus.FAILED) {
                                Button(
                                    onClick = { viewModel?.retryUploads() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmergencyRed,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("RETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 4c. BLE Mesh Peer Acknowledgement Feedback
            if (isSosActive && activeLocalSos != null) {
                item {
                    val isAcked = activeLocalSos.isAcknowledged
                    val ackCount = activeLocalSos.acknowledgedBy.size
                    val ackColor = if (isAcked) SafeGreen else WarningAmber
                    val ackTitle = if (isAcked) {
                        if (ackCount > 1) "DELIVERY CONFIRMED ($ackCount PEERS)" else "DELIVERY CONFIRMED (1 PEER)"
                    } else {
                        "WAITING FOR BLE PEER ACK"
                    }
                    val ackDesc = if (isAcked) {
                        "Acknowledged by: ${activeLocalSos.acknowledgedBy.joinToString(", ") { it.takeLast(6) }}"
                    } else {
                        "Beacon transmitting over BLE mesh. Waiting for nearby peer nodes to acknowledge receipt..."
                    }

                    GlassCard(borderColor = ackColor.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PulsingDot(color = ackColor, size = 10.dp)
                            Column {
                                Text(
                                    text = "BLE MESH ACKNOWLEDGEMENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = ackTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ackColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ackDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. Emergency Protocol Explanation Card
            item {
                GlassCard(borderColor = if (isSosActive) BorderEmergency else BorderGlass) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isSosActive) EmergencyRed else WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TACTICAL RESCUE PROTOCOL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "When activated, ResQMesh transmits a high-priority distress beacon across all accessible Bluetooth LE peer nodes without requiring cellular data or internet access. Relay devices repeat this signal automatically across the mesh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 6. Cancel SOS Action
            if (isSosActive) {
                item {
                    OutlinedButton(
                        onClick = {
                            viewModel?.cancelSos()
                            localSosActive = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, SafeGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeGreen)
                    ) {
                        Text(
                            text = "DISARM / CANCEL SOS SIGNAL",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Confirmation Dialog
        if (showConfirmDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSpaceBlack.copy(alpha = 0.85f))
                    .clickable { showConfirmDialog = false },
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    borderColor = EmergencyRed,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sos,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CONFIRM EMERGENCY BROADCAST",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Are you sure you want to broadcast a ${selectedEmergencyType.label} distress alert to all surrounding mesh nodes?",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("CANCEL")
                            }

                            Button(
                                onClick = {
                                    showConfirmDialog = false
                                    if (viewModel != null) {
                                        viewModel.sendSos(
                                            priority = selectedEmergencyType.priority,
                                            title = selectedEmergencyType.label,
                                            details = "Immediate offline assistance needed: ${selectedEmergencyType.label} incident reported via local beacon."
                                        )
                                    } else {
                                        localSosActive = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                            ) {
                                Text("BROADCAST", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedDistressButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "distress_halo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.25f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 550 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "distress_halo_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(haloScale)
                .background(EmergencyRed.copy(alpha = if (isActive) 0.22f else 0.08f), CircleShape)
                .border(2.dp, EmergencyRed.copy(alpha = if (isActive) 0.6f else 0.25f), CircleShape)
        )

        // Core trigger
        Button(
            onClick = onClick,
            modifier = Modifier.size(150.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) EmergencyRed else EmergencyRedDark,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 14.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sos,
                    contentDescription = "SOS",
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = if (isActive) "ACTIVE" else "HOLD SOS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun EmergencyTypeChip(
    type: EmergencyType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) type.color.copy(alpha = 0.18f) else SurfaceGlassNavy)
            .border(
                width = 1.dp,
                color = if (isSelected) type.color else BorderGlass,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = if (isSelected) type.color else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TelemetryPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
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
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun LocationTelemetryCard(
    locationState: LocationState,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    val statusColor = when (locationState.status) {
        LocationStatus.FIXED -> SafeGreen
        LocationStatus.SEARCHING -> WarningAmber
        LocationStatus.STALE -> WarningAmber
        LocationStatus.PERMISSION_DENIED -> EmergencyRed
        LocationStatus.LOCATION_DISABLED -> EmergencyRed
        LocationStatus.UNAVAILABLE -> EmergencyRed
    }

    val statusBadgeText = when (locationState.status) {
        LocationStatus.FIXED -> "SATELLITE LOCK ACQUIRED"
        LocationStatus.SEARCHING -> "ACQUIRING SATELLITE FIX..."
        LocationStatus.STALE -> "GPS FIX STALE (> 5 MIN OLD)"
        LocationStatus.PERMISSION_DENIED -> "PERMISSION DENIED"
        LocationStatus.LOCATION_DISABLED -> "GPS SERVICES DISABLED"
        LocationStatus.UNAVAILABLE -> "GPS HARDWARE UNAVAILABLE"
    }

    GlassCard(borderColor = statusColor.copy(alpha = 0.5f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingDot(color = statusColor, size = 10.dp)
                    Text(
                        text = "TACTICAL GEOLOCATION (100% OFFLINE)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = statusBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val coord = locationState.coordinate
            if (coord != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val latFormatted = "%.5f° %s".format(
                            kotlin.math.abs(coord.latitude),
                            if (coord.latitude >= 0) "N" else "S"
                        )
                        val lonFormatted = "%.5f° %s".format(
                            kotlin.math.abs(coord.longitude),
                            if (coord.longitude >= 0) "E" else "W"
                        )
                        Text(
                            text = "$latFormatted, $lonFormatted",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        val accText = locationState.accuracyMeters?.let { " • Accuracy: ±${"%.1f".format(it)}m" } ?: ""
                        val altText = coord.altitudeMeters?.let { " • Alt: ${it.toInt()}m" } ?: ""
                        val providerText = locationState.provider?.uppercase() ?: "GPS"
                        Text(
                            text = "Provider: $providerText$accText$altText",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                val explanation = when (locationState.status) {
                    LocationStatus.SEARCHING ->
                        "Searching for direct satellite fixes. Emergency SOS beacon can still be broadcast immediately without GPS coordinates."
                    LocationStatus.PERMISSION_DENIED ->
                        "Location permission is required to tag GPS coordinates in emergency distress beacons."
                    LocationStatus.LOCATION_DISABLED ->
                        "Device location is turned off in Android system settings. Turn on GPS to tag coordinates."
                    LocationStatus.UNAVAILABLE ->
                        "Location hardware service is unavailable on this device. Outbound SOS will dispatch without coordinates."
                    else ->
                        "No coordinate fix available yet. SOS will transmit with empty coordinates."
                }
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    fontSize = 12.sp
                )

                if (locationState.status == LocationStatus.PERMISSION_DENIED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmergencyRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("GRANT LOCATION PERMISSION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (locationState.status == LocationStatus.LOCATION_DISABLED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenLocationSettings,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningAmber,
                            contentColor = DeepSpaceBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("OPEN LOCATION SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
