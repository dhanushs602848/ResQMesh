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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.MeshNode
import com.resqmesh.app.data.model.NodeStatus
import com.resqmesh.app.data.model.SystemTelemetry
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.GlowCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.BorderGlass
import com.resqmesh.app.ui.theme.DeepBlueNeon
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.SurfaceCard
import com.resqmesh.app.ui.theme.SurfaceGlassNavy
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
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
    val liveNodes by (viewModel?.nodes?.collectAsState() ?: remember {
        mutableStateOf(
            listOf(
                MeshNode("RQM-042", "Relay Node Delta", -64, 1, true, System.currentTimeMillis() - 45000, NodeStatus.RELAYING),
                MeshNode("RQM-109", "Mobile Responder Unit", -78, 2, false, System.currentTimeMillis() - 120000, NodeStatus.ONLINE),
                MeshNode("RQM-312", "Emergency Hub Echo", -59, 1, true, System.currentTimeMillis() - 25000, NodeStatus.ONLINE)
            )
        )
    })
    val telemetry by (viewModel?.telemetry?.collectAsState() ?: remember { mutableStateOf(SystemTelemetry()) })

    val activeRelaysCount = liveNodes.count { it.isRelayCapable }
    val maxHops = liveNodes.maxOfOrNull { it.hopCount } ?: 0

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
                        label = "NODES ONLINE",
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

            // 3. Discovered Peer Nodes Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERED PEER NODES (${liveNodes.size} ACTIVE)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    PulsingDot(color = SafeGreen, size = 8.dp)
                }
            }

            items(liveNodes.size) { index ->
                PeerNodeItemCard(peer = liveNodes[index])
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

            Spacer(modifier = Modifier.height(16.dp))

            // Radar Canvas
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(DeepSpaceBlack),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2.2f

                    // Concentric Range Rings
                    val ringFractions = listOf(0.33f, 0.66f, 1.0f)
                    ringFractions.forEach { frac ->
                        drawCircle(
                            color = ElectricCyan.copy(alpha = 0.2f),
                            radius = maxRadius * frac,
                            center = center,
                            style = Stroke(width = 1.2f)
                        )
                    }

                    // Crosshair Axes
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.15f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.15f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1f
                    )

                    // Rotating Radar Sweep Beam
                    val rad = Math.toRadians(sweepAngle.toDouble())
                    val endX = (center.x + maxRadius * cos(rad)).toFloat()
                    val endY = (center.y + maxRadius * sin(rad)).toFloat()
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(ElectricCyan.copy(alpha = 0.8f), Color.Transparent),
                            start = center,
                            end = Offset(endX, endY)
                        ),
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )

                    // Center Node (Current Device)
                    drawCircle(color = ElectricCyan, radius = 7f, center = center)
                    drawCircle(color = Color.White, radius = 3.5f, center = center)

                    // Simulated Surrounding Peer Nodes
                    val simulatedPositions = listOf(
                        Offset(center.x + maxRadius * 0.45f, center.y - maxRadius * 0.35f),
                        Offset(center.x - maxRadius * 0.55f, center.y - maxRadius * 0.20f),
                        Offset(center.x + maxRadius * 0.20f, center.y + maxRadius * 0.65f)
                    )

                    simulatedPositions.forEach { pos ->
                        // Communication Link Line
                        drawLine(
                            color = ElectricCyan.copy(alpha = 0.35f),
                            start = center,
                            end = pos,
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                        // Peer Node Dot
                        drawCircle(color = SafeGreen, radius = 6f, center = pos)
                        drawCircle(color = Color.White, radius = 2.5f, center = pos)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Center: LOCAL HOST • Scanning 360° for surrounding multi-hop beacons",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(text = "${peer.id} • ${peer.status.name} • Hops: ${peer.hopCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            listOf(
                Triple(Icons.Default.Bluetooth, "BLE Multi-Hop Flooding", "Zero configuration P2P message relay"),
                Triple(Icons.Default.WifiTethering, "Wi-Fi Direct Group Owner", "High-bandwidth payload & telemetry sharing"),
                Triple(Icons.Default.Radio, "LoRa 433/868/915 MHz (Modular)", "Ultra long-range emergency SOS packet beacon")
            ).forEach { (icon, title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceGlassNavy, RoundedCornerShape(10.dp))
                            .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = title, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}
