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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Sos
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqmesh.app.data.model.IncidentPriority
import com.resqmesh.app.ui.components.GlassCard
import com.resqmesh.app.ui.components.ResQTopBar
import com.resqmesh.app.ui.theme.DeepSpaceBlack
import com.resqmesh.app.ui.theme.ElectricCyan
import com.resqmesh.app.ui.theme.EmergencyRed
import com.resqmesh.app.ui.theme.SafeGreen
import com.resqmesh.app.ui.theme.TextPrimary
import com.resqmesh.app.ui.theme.TextSecondary
import com.resqmesh.app.ui.viewmodel.ResQMeshViewModel

data class HistoryLogItem(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val timestamp: String,
    val isDistress: Boolean
)

@Composable
fun HistoryScreen(
    viewModel: ResQMeshViewModel? = null,
    onNavigateBack: () -> Unit
) {
    val liveBroadcasts by (viewModel?.broadcasts?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    val dynamicLogs = remember(liveBroadcasts) {
        val broadcastLogs = liveBroadcasts.map { msg ->
            val elapsedMinutes = ((System.currentTimeMillis() - msg.timestamp) / 60000).coerceAtLeast(1)
            val timeText = if (elapsedMinutes < 60) "${elapsedMinutes}m ago" else "${elapsedMinutes / 60}h ago"
            val isCritical = msg.priority == IncidentPriority.CRITICAL || msg.priority == IncidentPriority.HIGH
            HistoryLogItem(
                id = msg.id,
                type = if (isCritical) "SOS BROADCAST" else "MESH PACKET",
                title = msg.title,
                detail = "${msg.content} (Hops: ${msg.hopCount}, Relay: ${msg.senderName})",
                timestamp = timeText,
                isDistress = isCritical
            )
        }

        val baseLogs = listOf(
            HistoryLogItem("H-1", "SOS TRANSMISSION", "Emergency Beacon Disarmed", "Distress test canceled by operator cleanly.", "10:14 AM", true),
            HistoryLogItem("H-2", "PACKET RELAY", "Multi-Hop Broadcast Forwarded", "Forwarded weather bulletin for Sector 4 peer node.", "09:48 AM", false),
            HistoryLogItem("H-3", "MESH DISCOVERY", "3 Peer Nodes Synchronized", "Formed direct BLE mesh clusters with ID RQM-042.", "09:12 AM", false)
        )

        (broadcastLogs + baseLogs).distinctBy { it.id }
    }

    Scaffold(
        topBar = {
            ResQTopBar(title = "MISSION & PACKET LOGS", onNavigateBack = onNavigateBack)
        },
        containerColor = DeepSpaceBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHRONOLOGICAL EVENT TIMELINE (${dynamicLogs.size} ENTRIES)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(text = "LOCAL PERSISTENT STORAGE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = SafeGreen)
                }
            }

            items(dynamicLogs.size) { idx ->
                val log = dynamicLogs[idx]
                val accent = if (log.isDistress) EmergencyRed else ElectricCyan

                GlassCard(borderColor = accent.copy(alpha = 0.4f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f))
                                .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (log.isDistress) Icons.Default.Sos else Icons.Default.Outbox,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = log.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = log.timestamp, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = log.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "${log.type} • ID: ${log.id}", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
