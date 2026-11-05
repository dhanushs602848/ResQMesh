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

## Opening and Running in Android Studio

1. Open **Android Studio** (Hedgehog or Ladybug / Koala).
2. Choose **File → Open** and select `C:\Users\DHANUSH\StudioProjects\ResQMesh`.
3. Let Gradle complete initial sync. `local.properties` is configured with your Android SDK path, and `gradle.properties` is pointed at the embedded JBR JDK 25 (`C:\Program Files\Android\Android Studio1\jbr`).
4. Select an emulator or connected physical Android device (API 26+).
5. Click **Run ▶** (`Shift+F10`) to launch the application.
