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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.EmergencyMessage
import com.resqmesh.app.data.model.IncidentPriority
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

    val liveBroadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember {
        mutableStateOf(
            listOf(
                EmergencyMessage(
                    id = "BC-901",
                    senderId = "RQM-NODE-ALPHA",
                    senderName = "Forward Post Alpha",
                    title = "Critical Medical Distress Broadcast",
                    content = "Relayed SOS: First responder assistance required at Sector 4 North coordinates.",
                    timestamp = System.currentTimeMillis() - 180000,
                    priority = IncidentPriority.CRITICAL,
                    hopCount = 2
                ),
                EmergencyMessage(
                    id = "BC-844",
                    senderId = "WEATHER-RELAY-02",
                    senderName = "Telemetry Sentinel",
                    title = "Flash Flood Advisory Alert",
                    content = "Regional river water rise detected. Mesh routing switched to elevated bridge nodes.",
                    timestamp = System.currentTimeMillis() - 1080000,
                    priority = IncidentPriority.HIGH,
                    hopCount = 3
                ),
                EmergencyMessage(
                    id = "BC-720",
                    senderId = "LOCAL-HOST",
                    senderName = "Mesh Router Daemon",
                    title = "Mesh Routing Table Synchronized",
                    content = "3 peer relay nodes linked into autonomous multi-hop message forwarding group.",
                    timestamp = System.currentTimeMillis() - 2040000,
                    priority = IncidentPriority.INFO,
                    hopCount = 0
                )
            )
        )
    })

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
            // Tactical Tabs (All, Distress, System)
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
                    listOf("ALL ALERTS", "DISTRESS", "SYSTEM").forEachIndexed { idx, label ->
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
                        PulsingDot(color = WarningAmber, size = 8.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${liveBroadcasts.size} BROADCASTS RECEIVED ACROSS MESH",
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

            // Broadcast Cards List
            val filtered = when (selectedTab) {
                1 -> liveBroadcasts.filter { it.priority == IncidentPriority.CRITICAL || it.priority == IncidentPriority.HIGH }
                2 -> liveBroadcasts.filter { it.priority == IncidentPriority.INFO || it.priority == IncidentPriority.NORMAL }
                else -> liveBroadcasts
            }

            items(filtered.size) { index ->
                val broadcast = filtered[index]
                TacticalBroadcastCard(
                    item = broadcast,
                    onClick = {
                        viewModel?.acknowledgeAlert(broadcast.id)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isAcknowledged) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (item.isAcknowledged) SafeGreen else accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.priority.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = accentColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "• ${item.senderName} • Hops: ${item.hopCount}${if (item.isAcknowledged) " • ACKNOWLEDGED" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (item.isAcknowledged) SafeGreen else TextSecondary
                    )
                }
            }
        }
    }
}

private fun BorderGlassColor(accent: Color): Color = accent.copy(alpha = 0.2f)
