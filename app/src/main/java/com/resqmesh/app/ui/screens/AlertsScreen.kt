package com.resqmesh.app.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.data.model.UploadStatus
import com.resqmesh.app.navigation.Screen
import com.resqmesh.app.ui.components.FuturisticBottomBar
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.PulsingDot
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.theme.WarningAmber
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

@Composable
fun AlertsScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToMesh: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val allBroadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember {
        mutableStateOf(emptyList())
    })

    // Segregate real mesh live signals from reference simulation alerts
    val liveAlerts = remember(allBroadcasts) { allBroadcasts.filter { !it.isDemo } }
    val demoAlerts = remember(allBroadcasts) { allBroadcasts.filter { it.isDemo } }

    val displayedList = when (selectedTab) {
        0 -> liveAlerts // All real live alerts (incoming + local)
        1 -> liveAlerts.filter { it.priority == IncidentPriority.CRITICAL || it.priority == IncidentPriority.HIGH }
        2 -> demoAlerts // Reference simulation alerts
        else -> liveAlerts
    }

    Scaffold(
        topBar = {
            ResQTopBar(title = "EMERGENCY BROADCASTS", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack,
        bottomBar = {
            FuturisticBottomBar(
                currentRoute = Screen.Alerts.route,
                onNavigate = { screen ->
                    when (screen) {
                        Screen.Dashboard -> onNavigateToHome()
                        Screen.MeshNetwork -> onNavigateToMesh()
                        Screen.Alerts -> {}
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tactical Tabs: Live Feed, Distress Only, Simulation Mode
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DeepSpaceBlack,
                    contentColor = ElectricCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ElectricCyan,
                            height = 3.dp
                        )
                    }
                ) {
                    val tabs = listOf(
                        "LIVE FEED (${liveAlerts.size})",
                        "CRITICAL",
                        "SIMULATION (${demoAlerts.size})"
                    )
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == idx) ElectricCyan else TextSecondary
                                )
                            }
                        )
                    }
                }
            }

            // Summary Status Ribbon
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(
                            color = if (liveAlerts.isNotEmpty()) EmergencyRed else SafeGreen,
                            size = 8.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedTab == 2) {
                                "${demoAlerts.size} REFERENCE DEMO ALERTS"
                            } else {
                                "${liveAlerts.size} LIVE RESCUE BROADCAST(S)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "TAP ITEM TO ACKNOWLEDGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontSize = 10.sp
                    )
                }
            }

            // Empty state for Live Feed if no real incoming or local SOS exists
            if (displayedList.isEmpty()) {
                item {
                    EmptyAlertsCard(selectedTab = selectedTab)
                }
            } else {
                items(displayedList.size) { index ->
                    val broadcast = displayedList[index]
                    TacticalBroadcastCard(
                        item = broadcast,
                        onClick = {
                            viewModel?.acknowledgeAlert(broadcast.id)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyAlertsCard(selectedTab: Int) {
    GlassCard(borderColor = ElectricCyan.copy(alpha = 0.3f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.1f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (selectedTab == 1) "NO CRITICAL DISTRESS SIGNALS" else "NO LIVE DISTRESS SIGNALS DETECTED",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Listening on BLE Service UUID 7b1f3c8a... When nearby peer nodes transmit an emergency distress beacon, it will be received and surfaced here in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun TacticalBroadcastCard(
    item: EmergencyMessage,
    onClick: () -> Unit
) {
    val (accentColor, icon) = when (item.priority) {
        IncidentPriority.CRITICAL -> EmergencyRed to Icons.Default.Emergency
        IncidentPriority.HIGH -> WarningAmber to Icons.Default.Warning
        IncidentPriority.NORMAL -> ElectricCyan to Icons.Default.Info
        IncidentPriority.INFO -> SafeGreen to Icons.Default.Info
    }

    val elapsedMinutes = ((System.currentTimeMillis() - item.timestamp) / 60000).coerceAtLeast(1)
    val timeText = if (elapsedMinutes < 60) "${elapsedMinutes}m ago" else "${elapsedMinutes / 60}h ago"

    GlassCard(
        borderColor = if (item.isAcknowledged) BorderGlassColor(accentColor) else accentColor.copy(alpha = 0.65f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isAcknowledged) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (item.isAcknowledged) SafeGreen else accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header: Title & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Distress details
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // GPS Pinpoint Coordinates if present
                if (item.location != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GPS: %.4f° N, %.4f° E".format(item.location.latitude, item.location.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = SafeGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Origin / Relaying Metadata
                val routingText = when {
                    item.isDemo -> "Simulated Node: ${item.senderName} • Hops: ${item.hopCount}"
                    item.isReceived -> "Relayed by ${item.senderName} • Origin: ${item.originNodeId ?: item.senderId} • Hops: ${item.hopCount}"
                    else -> "Origin: Local Device (${item.senderId}) • Hops: 0"
                }

                Text(
                    text = routingText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Tactical Badges Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Badge: INCOMING / LOCAL / DEMO
                    val (typeLabel, typeColor) = when {
                        item.isDemo -> "SIMULATION" to TextSecondary
                        item.isReceived -> "INCOMING SOS" to ElectricCyan
                        else -> "LOCAL BEACON" to EmergencyRed
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(typeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = typeColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Priority Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.priority.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Uplink Badge
                    if (item.uploadStatus != UploadStatus.NOT_APPLICABLE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(item.uploadStatus.color.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CLOUD: ${item.uploadStatus.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = item.uploadStatus.color,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (item.isAcknowledged) {
                        val ackText = if (item.acknowledgedBy.isNotEmpty()) {
                            val count = item.acknowledgedBy.size
                            val peersLabel = if (count == 1) "peer" else "peers"
                            val nodesStr = item.acknowledgedBy.joinToString(", ") { it.takeLast(6) }
                            "• ACKNOWLEDGED ($count $peersLabel: $nodesStr)"
                        } else {
                            "• ACKNOWLEDGED"
                        }
                        Text(
                            text = ackText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = SafeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun BorderGlassColor(accent: Color): Color = accent.copy(alpha = 0.2f)
