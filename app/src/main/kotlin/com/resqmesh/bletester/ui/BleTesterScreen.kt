package com.resqmesh.bletester.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqmesh.bletester.ble.*
import com.resqmesh.bletester.model.MessagePacket
import com.resqmesh.bletester.model.MessageType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleTesterScreen(
    scannerManager: BleScannerManager,
    advertiserManager: BleAdvertiserManager,
    serverManager: BleGattServerManager,
    clientManager: BleGattClientManager,
    sniffer: BlePacketSniffer,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Scanner", "Advertiser", "GATT Ping", "Sniffer Log")

    val isScanning by scannerManager.isScanning.collectAsStateWithLifecycle()
    val isAdvertising by advertiserManager.isAdvertising.collectAsStateWithLifecycle()
    val isServerRunning by serverManager.isServerRunning.collectAsStateWithLifecycle()
    val connectionState by clientManager.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ResQMesh BLE Tester", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (hasPermissions) "Permissions OK | Ready for 2-Phone Test" else "Permissions Required!",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    if (!hasPermissions) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant Perms", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Status bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan: ${if (isScanning) "ACTIVE" else "OFF"} | Adv: ${if (isAdvertising) "ON" else "OFF"} | Svr: ${if (isServerRunning) "RUNNING" else "OFF"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = {
                            if (isServerRunning) serverManager.stopServer() else serverManager.startServer()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(if (isServerRunning) "Stop GATT Svr" else "Start GATT Svr", fontSize = 11.sp)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> ScannerTab(
                        scannerManager = scannerManager,
                        onConnectPeer = { peer ->
                            clientManager.connect(peer.device)
                            selectedTab = 2 // Switch to GATT tab
                        }
                    )
                    1 -> AdvertiserTab(
                        advertiserManager = advertiserManager
                    )
                    2 -> GattPingTab(
                        clientManager = clientManager,
                        connectionState = connectionState
                    )
                    3 -> SnifferTab(sniffer = sniffer)
                }
            }
        }
    }
}

@Composable
fun ScannerTab(
    scannerManager: BleScannerManager,
    onConnectPeer: (DiscoveredPeer) -> Unit
) {
    val isScanning by scannerManager.isScanning.collectAsStateWithLifecycle()
    val peers by scannerManager.discoveredPeers.collectAsStateWithLifecycle()
    val filterResQMesh by scannerManager.filterOnlyResQMesh.collectAsStateWithLifecycle()
    val status by scannerManager.statusMessage.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isScanning) scannerManager.stopScanning() else scannerManager.startScanning()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isScanning) "Stop Scanning" else "Start BLE Scan")
            }

            OutlinedButton(onClick = { scannerManager.clearPeers() }) {
                Text("Clear (${peers.size})")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = filterResQMesh,
                onCheckedChange = { scannerManager.setFilterOnlyResQMesh(it) }
            )
            Text("Filter ResQMesh Service UUID only", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (peers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Scanning for nearby BLE peers...\nKeep second phone advertising" else "Scanner is stopped. Press 'Start BLE Scan'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(peers, key = { it.address }) { peer ->
                    PeerCard(peer = peer, onConnect = { onConnectPeer(peer) })
                }
            }
        }
    }
}

@Composable
fun PeerCard(peer: DiscoveredPeer, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peer.name.ifBlank { "Unknown BLE Node" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = peer.address,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (peer.isResQMeshNode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (peer.isResQMeshNode) "ResQMesh" else "Other BLE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RSSI: ${peer.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        peer.rssi >= -65 -> Color(0xFF2E7D32)
                        peer.rssi >= -80 -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }
                )

                Button(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("GATT Connect", fontSize = 12.sp)
                }
            }

            if (peer.parsedPacket != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Packet: [${peer.parsedPacket.messageType}] from ${peer.parsedPacket.senderId}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Payload: \"${peer.parsedPacket.payload}\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdvertiserTab(
    advertiserManager: BleAdvertiserManager
) {
    val isAdvertising by advertiserManager.isAdvertising.collectAsStateWithLifecycle()
    val status by advertiserManager.statusMessage.collectAsStateWithLifecycle()
    val sentCount by advertiserManager.packetsSentCount.collectAsStateWithLifecycle()

    var senderId by remember { mutableStateOf("NODE-${UUID.randomUUID().toString().take(4)}") }
    var selectedType by remember { mutableStateOf(MessageType.SOS) }
    var payloadText by remember { mutableStateOf("HELP! Medical Emergency at Sector 4") }
    var ttl by remember { mutableIntStateOf(7) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("BLE Packet Broadcaster", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = senderId,
            onValueChange = { senderId = it },
            label = { Text("Sender Node ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Message Type:", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageType.entries.filter { it != MessageType.ACK }.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        payloadText = when (type) {
                            MessageType.SOS -> "SOS! Injured civilian requires evac"
                            MessageType.CHAT -> "Test mesh text message"
                            MessageType.LOCATION -> "LOC: 12.9716, 77.5946"
                            MessageType.BEACON -> "BEACON: Link quality probe"
                            else -> "Test"
                        }
                    },
                    label = { Text(type.name, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = payloadText,
            onValueChange = { payloadText = it },
            label = { Text("Message Payload") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TTL: $ttl hops", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = ttl.toFloat(),
                onValueChange = { ttl = it.toInt() },
                valueRange = 1f..15f,
                steps = 14,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isAdvertising) {
                    advertiserManager.stopAdvertising()
                } else {
                    val packet = MessagePacket(
                        senderId = senderId,
                        receiverId = MessagePacket.BROADCAST_ID,
                        payload = payloadText,
                        messageType = selectedType,
                        ttl = ttl
                    )
                    advertiserManager.startAdvertising(packet)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAdvertising) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isAdvertising) Icons.Default.Stop else Icons.Default.Podcasts, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAdvertising) "Stop Advertising" else "Start Advertising Packet")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Advertiser State: $status", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Advertisements Emitted: $sentCount", style = MaterialTheme.typography.bodySmall)
                Text("Service UUID: ${MessagePacket.SERVICE_UUID}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun GattPingTab(
    clientManager: BleGattClientManager,
    connectionState: String
) {
    val targetAddress by clientManager.connectedDeviceAddress.collectAsStateWithLifecycle()
    val latency by clientManager.lastRoundTripLatencyMs.collectAsStateWithLifecycle()
    var pingMessage by remember { mutableStateOf("PING TEST") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("GATT Peer-to-Peer & Latency Ping", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("GATT Connection Status:", style = MaterialTheme.typography.labelSmall)
                Text(connectionState, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                targetAddress?.let {
                    Text("Target Peer: $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                latency?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last Round-Trip Latency: ${it}ms",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pingMessage,
            onValueChange = { pingMessage = it },
            label = { Text("GATT Payload to Send") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val packet = MessagePacket(
                    senderId = "PING-TESTER",
                    receiverId = targetAddress ?: MessagePacket.BROADCAST_ID,
                    payload = pingMessage,
                    messageType = MessageType.CHAT
                )
                clientManager.sendPacket(packet)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send Packet via GATT")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { clientManager.disconnect() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect GATT")
        }
    }
}

@Composable
fun SnifferTab(sniffer: BlePacketSniffer) {
    val logs by sniffer.logs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Live Sniffer Logs (${logs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { sniffer.clear() }) {
                Text("Clear Logs")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                val badgeColor = when (log.direction) {
                    LogDirection.TX -> Color(0xFF4CAF50)
                    LogDirection.RX -> Color(0xFF00BCD4)
                    LogDirection.ADV -> Color(0xFF2196F3)
                    LogDirection.SCAN -> Color(0xFF9E9E9E)
                    LogDirection.GATT_SERVER -> Color(0xFF9C27B0)
                    LogDirection.GATT_CLIENT -> Color(0xFFFF9800)
                    LogDirection.ERROR -> Color(0xFFF44336)
                    LogDirection.INFO -> Color(0xFFBDBDBD)
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[${log.formattedTime}]",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = log.direction.name,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        log.deviceAddress?.let {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = it,
                                color = Color(0xFFAAAAAA),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Text(
                        text = log.message,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )

                    log.hexString?.let {
                        Text(
                            text = "HEX: $it",
                            color = Color(0xFF64B5F6),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
