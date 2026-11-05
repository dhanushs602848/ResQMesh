# ResQMesh 🔴

> A cinematic, futuristic emergency-response mesh networking application for Android built with Jetpack Compose & Material 3.

## Visual Design Vision & Architecture

| Layer | Implementation |
|---|---|
| **Core Theme** | Deep Space Command Center (`#050810`, `#0B1220`, `#00D9FF`, `#FF304F`, `#24E69A`) |
| **3D Rendering** | Procedural 3D Earth Globe Canvas with dynamic meridians, glowing atmospheric aura, continent clusters, rotating telemetry arcs, and emergency SOS waves |
| **Intro Sequence** | Cinematic 7–8 second 3D animated introduction with Skip button & replay capability in Settings |
| **Scroll System** | Realtime scroll-linked parallax, Earth scale/translate reduction, and dynamic docking into a compact floating SOS control |
| **Tactical Glass** | Multi-layer Glassmorphic cards with translucent backdrops & radiant glow rims |
| **Architecture** | Clean modular Android architecture with Hilt DI & Type-safe Jetpack Navigation Compose |

---

## Complete Screen Directory

1. **Cinematic 3D Intro** (`IntroScreen.kt`)
   - Slowly emerging 3D Earth in pitch space
   - Rotating meridians & continent clusters
   - Glowing cyan peer nodes & communication arcs
   - Radial SOS pulse propagation
   - ResQMesh branding & slogan: *"CONNECTING PEOPLE. SAVING LIVES."*
   - Interactive Skip & Replay triggers

2. **Futuristic Command Dashboard** (`DashboardScreen.kt`)
   - Header with live network status indicator
   - Central interactive rotating 3D Earth
   - Scroll-driven parallax scaling (1.0 → 0.65) and vertical translation
   - Primary circular glowing emergency SOS button with animated breathing wave
   - Auto-docking compact floating SOS button on scroll past primary button
   - Hardware subsystem health indicators (BLE, GPS, P2P Relay)
   - Realtime telemetry metrics & recent broadcast preview

3. **Dedicated SOS Distress Control** (`SosScreen.kt`)
   - High-visibility emergency beacon trigger with rapid pulse halo
   - 4-category tactical incident classification (Medical, Disaster, Trapped, Security)
   - Confirmation safety dialog preventing accidental triggers
   - Disarm / Cancel emergency broadcast controls

4. **Mesh Network Topology Screen** (`MeshNetworkScreen.kt`)
   - Interactive 360° sweeping radar canvas visualizing local nodes
   - Telemetry metric summaries (Discovered nodes, Hops, Throughput)
   - Discovered peer cards with RSSI signal meters and relay status
   - Physical layer transport capability breakdown (BLE, Wi-Fi Direct, LoRa)

5. **Emergency Alerts & Broadcasts** (`AlertsScreen.kt`)
   - Multi-category tactical tabs (All Alerts, Distress, System)
   - Color-coded severity indicators (Emergency Red, Warning Amber, Cyan Info)
   - Hop count and origin node tracking for mesh packet auditing

6. **Tactical Rescue Map** (`RescueMapScreen.kt`)
   - Vector radar grid canvas with topographic contour circles
   - Dynamic incident pinpoint markers & relay vectors connecting to host
   - Interactive Zoom In / Zoom Out controls
   - Realtime GPS coordinate fix acquisition capsule

7. **Mission History & Packet Logs** (`HistoryScreen.kt`)
   - Chronological timeline of all transmitted SOS beacons and relayed packets
   - Persistent local log auditing

8. **Settings & Tactical Profile** (`SettingsScreen.kt`)
   - Replay 3D Intro button
   - Operator callsign & Node Hardware ID
   - Radio toggles for BLE Mesh Discovery & GPS Geolocation
   - Distress sirens & haptic vibration controls

---

## Celestia Hackathon Final Architecture & Integration

ResQMesh is engineered for high-concurrency offline disaster response operations across 4 integrated functional pillars:

1. **Member 1 (BLE Transport & Relay Engine)**:
   - Hardware-level BLE scanning and advertising (`DefaultBleManager`).
   - GATT client/server packet dispatcher with dynamic MTU negotiation (up to 512 bytes, fallback 23 bytes).
   - Multi-hop mesh relay engine (`MeshRelayEngine`) with loop suppression and TTL decrementation (default 7 hops).
   - Fragmentation engine (`MeshPacketFragmenter`, `SecureEnvelopeFragmenter`) handling oversized payloads.

2. **Member 2 (Incidents, Room Persistence & Acknowledgment)**:
   - Local Room SQLite database (`IncidentDao`, `IncidentEntity`) with offline queue.
   - Bidirectional BLE acknowledgment protocol (`AckSystem`) marking incidents delivered.
   - Deduplication tracking preventing relay storms and duplicate notifications.
   - Cloud synchronization uplink (`ResQMeshApi`, `RetrofitClient`) when internet connectivity is re-established.

3. **Member 3 (Tactical UI, Offline Vector Map & Location)**:
   - Jetpack Compose M3 UI with cinematic 3D Earth canvas and dark command center styling.
   - 100% Offline Tactical Radar Map (`RescueMapScreen.kt`) with mathematical planar projection (`TacticalCoordinateHelper`). Operates purely on vector canvas without external tile servers or internet API keys.
   - Realtime GPS positioning (`DefaultLocationTracker`) with stale threshold (5 min) and provider failover.

4. **Member 4 (Security Module & Offline Audio Alerts)**:
   - AES-256-GCM AEAD encryption (`EncryptedPayloadCodec`).
   - Hardware-backed Android Keystore wrapping (`AndroidKeystoreResponderKeyProvider`).
   - Replay attack mitigation with 10-minute sliding window and UUID cache (`InMemoryReplayProtectionValidator`).
   - Per-hop relay authentication using HMAC-SHA256 (`RelayAuthenticator`).
   - Offline audible emergency sirens and haptic vibration (`AndroidAudioAlertManager`).

---

## Automated Verification Suite

- Total Unit Tests: **199 Tests Passing** (141 `:app` + 58 `:core:security`), 0 failures, 0 skipped.
- Build Verification: `:app:assembleDebug` SUCCESSFUL.

```bash
# Run complete test suite
./gradlew :core:security:testDebugUnitTest :app:testDebugUnitTest

# Assemble production debug APK
./gradlew :app:assembleDebug
```

---

## Opening and Running in Android Studio

1. Open **Android Studio** (Hedgehog or Ladybug / Koala).
2. Choose **File → Open** and select `C:\Users\DHANUSH\StudioProjects\ResQMesh`.
3. Let Gradle complete initial sync. `local.properties` is configured with your Android SDK path, and JDK 17+ is configured.
4. Select an emulator or connected physical Android device (API 26+).
5. Click **Run ▶** (`Shift+F10`) to launch the application.
