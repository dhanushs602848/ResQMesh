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
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

data class MapIncidentMarker(
    val id: String,
    val title: String,
    val type: String,
    val priority: IncidentPriority,
    val coordinate: GeoCoordinate?,
    val distanceMeters: Double?,
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

    // Generate real incident markers using actual GPS coordinates relative to current phone
    val markers = remember(broadcasts, location) {
        val userLoc = location
        broadcasts.map { msg ->
            val dist = if (userLoc != null && msg.location != null) {
                TacticalCoordinateHelper.calculateDistanceMeters(
                    userLoc.latitude,
                    userLoc.longitude,
                    msg.location.latitude,
                    msg.location.longitude
                )
            } else {
                null
            }
            MapIncidentMarker(
                id = msg.id,
                title = msg.title,
                type = msg.priority.displayName,
                priority = msg.priority,
                coordinate = msg.location,
                distanceMeters = dist,
                isEmergency = msg.priority == IncidentPriority.CRITICAL || msg.priority == IncidentPriority.HIGH
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
                        userLocation = location,
                        markers = markers,
                        onZoomIn = { zoomLevel = (zoomLevel + 0.25f).coerceAtMost(3.0f) },
                        onZoomOut = { zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(0.5f) }
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
                            PulsingDot(color = if (location != null) SafeGreen else WarningAmber, size = 8.dp)
                        }
                    }
                }

                // Active Incident Points on Map Header
                val locatedCount = markers.count { it.coordinate != null }
                val unlocatedCount = markers.count { it.coordinate == null }

                item {
                    Text(
                        text = if (markers.isEmpty()) {
                            "INCIDENT TELEMETRY (0 REPORTED)"
                        } else {
                            "INCIDENT TELEMETRY (${markers.size} TOTAL • $locatedCount PINPOINTED • $unlocatedCount UNLOCATED)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (markers.isEmpty()) {
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
                                    text = "NO ACTIVE INCIDENTS REPORTED",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Tactical rescue map is standing by. Incoming emergency SOS messages broadcast over the BLE mesh will be projected here in real time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(markers.size) { idx ->
                        val marker = markers[idx]
                        val hasCoord = marker.coordinate != null

                        ResQCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            !hasCoord -> Icons.Default.LocationOff
                                            marker.isEmergency -> Icons.Default.Sos
                                            else -> Icons.Default.LocationOn
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            !hasCoord -> WarningAmber
                                            marker.isEmergency -> EmergencyRed
                                            else -> ElectricCyan
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = marker.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        val locText = if (hasCoord) {
                                            val distStr = marker.distanceMeters?.let { " • %.0fm away".format(it) } ?: ""
                                            "GPS: %.4f°, %.4f°$distStr".format(marker.coordinate!!.latitude, marker.coordinate.longitude)
                                        } else {
                                            "Coordinates unattached (No GPS in SOS)"
                                        }
                                        Text(
                                            text = "${marker.type} • $locText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasCoord) TextSecondary else WarningAmber
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (hasCoord) SafeGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (hasCoord) "PINPOINTED" else "UNLOCATED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasCoord) SafeGreen else WarningAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
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
    userLocation: GeoCoordinate?,
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
    val textMeasurer = rememberTextMeasurer()
    val baseRangeMeters = 500.0
    val currentRangeMeters = (baseRangeMeters / zoom.toDouble()).coerceIn(50.0, 5000.0)

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
            val center = Offset(w / 2f, h / 2f)
            val maxRadius = minOf(w, h) * 0.40f

            // 1. Futuristic Grid Overlay
            val gridStep = (35f * zoom).coerceIn(20f, 80f)
            var x = (center.x % gridStep)
            while (x < w) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }

            var y = (center.y % gridStep)
            while (y < h) {
                drawLine(
                    color = ElectricCyan.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // 2. Real Range Rings (Distance Scale from Center)
            for (i in 1..4) {
                val ringFrac = i / 4f
                val ringRadius = maxRadius * ringFrac
                val ringDistMeters = (currentRangeMeters * ringFrac).toInt()

                drawCircle(
                    color = DeepBlueNeon.copy(alpha = 0.16f * i),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )

                // Range label on north axis
                val ringLabel = if (ringDistMeters >= 1000) "%.1fkm".format(ringDistMeters / 1000.0) else "${ringDistMeters}m"
                val textLayout = textMeasurer.measure(
                    text = ringLabel,
                    style = TextStyle(color = ElectricCyan.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(center.x + 4.dp.toPx(), center.y - ringRadius - 6.dp.toPx())
                )
            }

            // Crosshair Axes
            drawLine(
                color = ElectricCyan.copy(alpha = 0.20f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, h),
                strokeWidth = 1f
            )
            drawLine(
                color = ElectricCyan.copy(alpha = 0.20f),
                start = Offset(0f, center.y),
                end = Offset(w, center.y),
                strokeWidth = 1f
            )

            // 3. User Central Position
            val userPos = center
            val userHasGps = userLocation != null
            val userColor = if (userHasGps) SafeGreen else WarningAmber

            drawCircle(
                color = userColor.copy(alpha = 0.25f),
                radius = pulseSize,
                center = userPos
            )
            drawCircle(color = userColor, radius = 6.dp.toPx(), center = userPos)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = userPos)

            // 4. Incident Markers (Plotted with Real GPS Coordinate Projections)
            if (userHasGps) {
                markers.forEach { marker ->
                    if (marker.coordinate != null) {
                        val (screenX, screenY) = TacticalCoordinateHelper.projectToScreen(
                            originLat = userLocation!!.latitude,
                            originLon = userLocation.longitude,
                            targetLat = marker.coordinate.latitude,
                            targetLon = marker.coordinate.longitude,
                            centerPx = Pair(userPos.x, userPos.y),
                            maxRadiusPx = maxRadius,
                            rangeMeters = currentRangeMeters
                        )

                        // Clamp positions to visible map viewport with padding
                        val mPos = Offset(
                            x = screenX.coerceIn(20.dp.toPx(), w - 20.dp.toPx()),
                            y = screenY.coerceIn(20.dp.toPx(), h - 20.dp.toPx())
                        )
                        val mColor = if (marker.isEmergency) EmergencyRed else ElectricCyan

                        // Connect to user with dashed relay vector line
                        drawLine(
                            color = mColor.copy(alpha = 0.40f),
                            start = userPos,
                            end = mPos,
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        drawCircle(color = mColor.copy(alpha = 0.35f), radius = 12.dp.toPx(), center = mPos)
                        drawCircle(color = mColor, radius = 5.dp.toPx(), center = mPos)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = mPos)

                        // Marker Text Tag
                        val distStr = marker.distanceMeters?.let {
                            if (it >= 1000.0) "%.1fkm".format(it / 1000.0) else "${it.toInt()}m"
                        } ?: ""
                        val markerLabel = "${marker.title.take(10)}\n$distStr"
                        val tagLayout = textMeasurer.measure(
                            text = markerLabel,
                            style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        )
                        drawText(
                            textLayoutResult = tagLayout,
                            topLeft = Offset(
                                x = (mPos.x - (tagLayout.size.width / 2f)).coerceIn(4f, w - tagLayout.size.width - 4f),
                                y = if (mPos.y < center.y) (mPos.y - tagLayout.size.height - 4f).coerceAtLeast(4f) else (mPos.y + 6f).coerceAtMost(h - tagLayout.size.height - 4f)
                            )
                        )
                    }
                }
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

        // Legend Tag Top-Start
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
                PulsingDot(color = if (userLocation != null) SafeGreen else WarningAmber, size = 6.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (userLocation != null) "OFFLINE TACTICAL GRID: REAL GPS" else "TACTICAL GRID: ACQUIRING GPS FIX",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextPrimary
                )
            }
        }

        // Scale Indicator Top-End
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceGlassNavy)
                .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val rangeDisplay = if (currentRangeMeters >= 1000.0) "%.1fkm".format(currentRangeMeters / 1000.0) else "${currentRangeMeters.toInt()}m"
            Text(
                text = "SCALE: $rangeDisplay (${"%.1f".format(zoom)}x)",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )
        }
    }
}
