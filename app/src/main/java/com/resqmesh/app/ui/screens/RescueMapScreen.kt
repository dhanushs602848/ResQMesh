package com.resqmesh.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.GeoCoordinate
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

data class MapIncidentMarker(
    val id: String,
    val title: String,
    val type: String,
    val xOffsetFrac: Float,
    val yOffsetFrac: Float,
    val isEmergency: Boolean
)

@Composable
fun RescueMapScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToMesh: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    val location by (viewModel?.location?.collectAsState() ?: remember {
        mutableStateOf<GeoCoordinate?>(null)
    })
    val broadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    // Generate dynamic incident markers from broadcast stream with fallback
    val markers = remember(broadcasts) {
        if (broadcasts.isNotEmpty()) {
            broadcasts.mapIndexed { index, msg ->
                val fracX = (0.25f + (index * 0.22f)) % 0.85f
                val fracY = (0.30f + (index * 0.20f)) % 0.80f
                MapIncidentMarker(
                    id = msg.id,
                    title = msg.title,
                    type = msg.priority.displayName,
                    xOffsetFrac = fracX,
                    yOffsetFrac = fracY,
                    isEmergency = msg.priority == IncidentPriority.CRITICAL || msg.priority == IncidentPriority.HIGH
                )
            }
        } else {
            listOf(
                MapIncidentMarker("M-1", "Medical Distress", "SOS", 0.35f, 0.40f, true),
                MapIncidentMarker("M-2", "Mesh Relay Point Beta", "RELAY", 0.65f, 0.30f, false),
                MapIncidentMarker("M-3", "Safe Evacuation Sector", "SHELTER", 0.70f, 0.68f, false)
            )
        }
    }

    Scaffold(
        topBar = {
            ResQTopBar(title = "TACTICAL RESCUE MAP", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack,
        bottomBar = {
            FuturisticBottomBar(
                currentRoute = Screen.RescueMap.route,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.Dashboard -> onNavigateToHome()
                        Screen.MeshNetwork -> onNavigateToMesh()
                        Screen.Alerts -> onNavigateToAlerts()
                        Screen.RescueMap -> {}
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tactical Map Canvas Container
                item {
                    TacticalMapCanvas(
                        zoom = zoomLevel,
                        markers = markers,
                        onZoomIn = { zoomLevel = (zoomLevel + 0.2f).coerceAtMost(2.0f) },
                        onZoomOut = { zoomLevel = (zoomLevel - 0.2f).coerceAtLeast(0.6f) }
                    )
                }

                // GPS Telemetry Banner
                item {
                    val latStr = location?.latitude?.let { "%.4f° N".format(it) } ?: "ACQUIRING..."
                    val lonStr = location?.longitude?.let { "%.4f° E".format(it) } ?: "GPS LOCK"
                    val accStr = location?.accuracyMeters?.let { " • Acc: ±${it.toInt()}m" } ?: ""

                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(SafeGreen.copy(alpha = 0.15f), CircleShape)
                                        .border(1.dp, SafeGreen.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "GPS COORDINATES: $latStr, $lonStr$accStr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "Autonomous GNSS fix attached to local beacon packets", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            PulsingDot(color = SafeGreen, size = 8.dp)
                        }
                    }
                }

                // Active Incident Points on Map
                item {
                    Text(
                        text = "INCIDENT INCIDENCE TELEMETRY (${markers.size} PINPOINTED)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                items(markers.size) { idx ->
                    val marker = markers[idx]
                    ResQCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (marker.isEmergency) Icons.Default.Sos else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (marker.isEmergency) EmergencyRed else ElectricCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = marker.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "Classification: ${marker.type} • ID: ${marker.id}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Text(
                                text = "PINPOINT",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TacticalMapCanvas(
    zoom: Float,
    markers: List<MapIncidentMarker>,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map_radar_pulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "map_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DeepSpaceBlack)
            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
    ) {
        // Grid & Topographic Vector Simulation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Futuristic Grid Overlay
            val gridStep = 40f * zoom
            var x = 0f
            while (x < w) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }

            var y = 0f
            while (y < h) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // 2. Simulated Topographic Sector Contours
            val contourCenter = Offset(w * 0.5f, h * 0.5f)
            listOf(60f, 110f, 160f, 220f).forEach { r ->
                drawCircle(
                    color = DeepBlueNeon.copy(alpha = 0.12f),
                    radius = r * zoom,
                    center = contourCenter,
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }

            // 3. User Central Position
            val userPos = Offset(w * 0.48f, h * 0.52f)
            drawCircle(
                color = SafeGreen.copy(alpha = 0.25f),
                radius = pulseSize,
                center = userPos
            )
            drawCircle(color = SafeGreen, radius = 6f, center = userPos)
            drawCircle(color = Color.White, radius = 2.5f, center = userPos)

            // 4. Incident & Relay Markers
            markers.forEach { marker ->
                val mPos = Offset(w * marker.xOffsetFrac, h * marker.yOffsetFrac)
                val mColor = if (marker.isEmergency) EmergencyRed else ElectricCyan

                // Connect to user with dashed relay vector line
                drawLine(
                    color = mColor.copy(alpha = 0.35f),
                    start = userPos,
                    end = mPos,
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                drawCircle(color = mColor.copy(alpha = 0.3f), radius = 14f, center = mPos)
                drawCircle(color = mColor, radius = 6f, center = mPos)
                drawCircle(color = Color.White, radius = 2f, center = mPos)
            }
        }

        // Overlay Map Controls (Zoom In, Zoom Out)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceGlassNavy, CircleShape)
                    .border(1.dp, BorderGlass, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceGlassNavy, CircleShape)
                    .border(1.dp, BorderGlass, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }

        // Legend Tag
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceGlassNavy)
                .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = SafeGreen, size = 6.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "SECTOR MAP: OFFLINE TACTICAL GRID", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextPrimary)
            }
        }
    }
}
