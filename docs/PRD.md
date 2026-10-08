# Product Requirements Document (PRD) — Intercom-Alpha

> **Document Version:** 2.9  
> **Status:** Production Release Tagged (`v2.9.0`)  
> **Last Updated:** 2026-10-08  
> **Repository:** `Intercom-Alpha`  
> **Obsidian Reference:** `03_Specifications/` & `PROJECT-SPEC.md`

---

## 🎯 1. Product Vision & Overview

### 1.1 Problem Statement
Motorcyclists, cyclists, and outdoor adventurers often ride in groups through remote environments (mountain passes, forest trails, tunnels, highways) where cellular data is either nonexistent, expensive, or highly unreliable. Existing commercial helmet communicators (e.g., Cardo, Sena) rely on proprietary, expensive hardware intercoms that cost $250–$400+ per unit and do not easily interoperate across brands.

### 1.2 Product Vision
**Intercom-Alpha** transforms standard smartphones into high-performance, decentralized, group voice communicators that operate **100% offline** via peer-to-peer Bluetooth Low Energy (BLE) mesh networks.

```
Rider opens app ➔ Creates or scans QR code ➔ Pairs Bluetooth helmet headset ➔ Real-time offline voice
```

- **Zero Accounts:** No email, phone number, password, or cloud signup.
- **Zero Internet Required:** All voice frames and control signaling route via local BLE GATT mesh.
- **Cross-Platform Interoperability:** Seamless, low-latency audio transmission between Android and iOS.
- **Battery-Centric:** Designed for 4+ hours of continuous background operation with the phone screen off in a pocket or tank bag.

---

## 👥 2. Target Audience & User Personas

| Persona | Environment | Primary Need | Critical Constraint |
| :--- | :--- | :--- | :--- |
| **Motorcycle Touring Groups** | Highways, mountain passes, tunnels | Low-latency voice communication between 2–8 riders | Must work with physical helmet buttons and glove touches |
| **Mountain Bike / Trail Riders** | Remote forests, canyons, downhill trails | Hands-free VOX voice activation without reaching for phone | Zero cellular coverage; high ambient wind/shock noise |
| **Overland & Convoy Drivers** | Remote backroads, desert tracks | Ad-hoc mesh range extension across multiple vehicles | Multi-hop packet relay between convoy vehicles |

---

## 🔑 3. Key Value Propositions & Design Pillars

1. **Instant Offline Onboarding**: A rider creates a group in 1 second; other riders join by scanning an offline QR code containing the group identifier and cryptographic key (`INTERCOM:v1:`).
2. **Multi-Hop Mesh Flooding**: Packets automatically hop across intermediate riders (TTL=3), extending effective communication distance well beyond direct point-to-point BLE range.
3. **Sliding-Window Loop Suppression**: Automatic deduplication caches drop looped packets within a 5,000ms window, preventing broadcast storms.
4. **End-to-End AEAD Encryption**: Audio and control packets are protected using RFC 8439 ChaCha20-Poly1305, ensuring untrusted relays cannot eavesdrop or tamper with voice frames.
5. **Native Platform UX**:
   - **Android**: Jetpack Compose, modern Navigation 3, and Koin Multiplatform.
   - **iOS**: 100% Native SwiftUI and AVFoundation (no shared Compose UI on iOS).

---

## 📋 4. Functional Requirements

### 4.1 Audio Engine & Transmission
- **FR-01 (Audio Capture & Playback)**: Capture microphone audio at 48kHz / 16-bit PCM little-endian; encode/compress frames into low-bandwidth profiles (16–64 kbps).
- **FR-02 (PTT Transmission Mode)**: Push-to-Talk via on-screen button (press, hold, drag to lock) and external Bluetooth media buttons (`KEY_VOLUME_UP`, `KEY_HEADSETHOOK`).
- **FR-03 (VOX Transmission Mode)**: Voice-activated transmission with configurable energy threshold (-20 dB to -60 dB) and hangover time (200–500 ms).
- **FR-04 (RMS Level Metering)**: Real-time audio metering (0.0 to 1.0) published to client UI for both input microphone and incoming peer audio.

### 4.2 Bluetooth & Mesh Networking
- **FR-05 (GATT Mesh Protocol)**: Unified GATT Service UUID `0000180d-0000-1000-8000-00805f9b34fb`, Characteristic UUID `00002a37-0000-1000-8000-00805f9b34fb`.
- **FR-06 (Simultaneous Peripheral & Central)**: Devices must advertise and scan simultaneously to form dynamic peer connections.
- **FR-07 (Multi-Hop Flooding Relay)**: Intermediate nodes automatically decrement packet TTL (from 3 down to 1) and re-broadcast packets to other connected peers.
- **FR-08 (Inner Packet Extraction)**: When receiving a relay packet, intermediate nodes extract the inner audio/control frame and dispatch it to local audio output.
- **FR-09 (Packet Deduplication)**: Sliding-window cache drops duplicate packets within 5,000 ms using composite keys `(senderId, sequence)`.

### 4.3 Group Management & Security
- **FR-10 (Offline Group Creation)**: Leader generates unique group ID and pre-shared cryptographic key.
- **FR-11 (QR Code Invite)**: Encode invite payload as `INTERCOM:v1:<base64(json)>` containing group ID, name, leader ID, and 24-hour expiration timestamp.
- **FR-12 (Packet Encryption)**: Encrypt audio and sensitive control payloads with RFC 8439 ChaCha20-Poly1305 using key derived from QR secret via FIPS 180-4 SHA-256.

### 4.4 Hardware & Headset Integration
- **FR-13 (Bluetooth SCO Routing)**: Route audio bidirectional stream through connected Bluetooth helmets / headsets via SCO audio profiles.
- **FR-14 (Physical Button Mapping)**: Intercept headset button events to trigger PTT_PRESS and PTT_RELEASE without waking the phone screen.

---

## ⚡ 5. Non-Functional Requirements & Performance Targets

| Metric | Target | Measurement Method |
| :--- | :--- | :--- |
| **1-Hop Audio Latency** | < 100 ms | Timestamp round-trip automated test |
| **3-Hop Relay Latency** | < 300 ms | Multi-node simulation benchmark |
| **Open-Air Range (1-Hop)** | ≥ 100 meters | Line-of-sight field testing |
| **Urban Range (1-Hop)** | ≥ 30 meters | Obstructed street field testing |
| **Battery Consumption** | < 30% per 4-hour ride | Continuous background recording & relay |
| **Memory Footprint** | < 80 MB resident RAM | Memory profiler during active mesh session |
| **Crash-Free Sessions** | > 99.5% | Play Console & TestFlight crash analytics |
| **Audio Intelligibility** | PESQ MOS > 3.5 | Clean voice reproduction under wind noise |

---

## 🚀 6. Delivered v2.0 Roadmap Capabilities
- **Cellular Fallback & Cloud Relay Gateway**: Ktor WebSocket fallback bridging separated mesh clusters over cellular data (Sprint 09).
- **Multi-Channel Intercom & Emergency Priority**: 16 configurable sub-channels with Dual-Watch and emergency preemption override chime synthesis (Sprint 10).
- **Convoy Situational Awareness & GPS Telemetry**: Great-Circle Haversine telemetry, relative rider bearings, and motion-aware beaconing (Sprint 11).
- **Offline Mesh Canned Hazard & Quick Text Messaging**: 1-tap standardized road hazard alerts with delivery ACK tracking (Sprint 11).
- **Rolling Emergency Voice Blackbox**: Encrypted in-memory rolling circular audio buffer with pure KMP ChaCha20-Poly1305 and LZ77 compression (Sprint 11).
- **Cockpit UI & Multiplatform Bridge**: 100% native SwiftUI cockpit on iOS and Compose Multiplatform cockpit on Android/Desktop (Sprint 12).
- **Hardware-to-Cockpit Service Bridge & R8 Hardening**: Wired live BLE mesh audio/PTT/meters, R8 minification (3.17 MB APK), and 4-hour battery/WakeLock validation (Sprint 13).
- **Advanced Convoy Mesh, Forward-Secret Key Ratchet & Live Activity**: Pure KMP RFC 5869 HKDF Perfect Forward Secrecy session ratchet, Cardo/Sena Bluetooth GATT battery/PTT accessory manager, native iOS 18+ Live Activity / Dynamic Island widget, and multi-cluster cross-mesh SFU bridging (Sprint 14).
- **Zero-Scroll Tactical Cockpit HUD & Telemetry Integration**: Zero-vertical-scroll mobile handlebar layout, 140dp tactile PTT with hot-mic pulse, 58dp volume/VOX-lock flanking actuators, single-row 4-hazard macro strip, and vector radar with polar-to-cartesian rider dots across Compose and SwiftUI (Sprint 15).
- **Mesh Repeater, Audio DSP & Convoy Simulation**: Pure KMP AGC & DRC speech normalization, autonomous stationary basecamp mesh repeater mode (`MeshRepeaterService.kt`), automated 8-node virtual convoy RF simulation harness (`ConvoyMeshSimulationHarness.kt`), and decentralized OTA group re-keying & dynamic epoch migration (`GroupEpochManager.kt`) (Sprint 16).
- **Basecamp Stationary Repeater UI & Dedicated Low-Power Cockpit Mode**: Dedicated `BasecampRepeaterScreen.kt` in pure OLED `#000000` dark standby, 100% native SwiftUI [`BasecampRepeaterView.swift`](file:///d:/Codes/Antigravity/Project4/Intercom-Alpha/app/iosApp/iosApp/BasecampRepeaterView.swift) with iOS 17+ StandBy landscape support, and remote basecamp beacon telemetry with 1-tap diagnostic pings on the main cockpit HUD (Sprint 17).
- **Offline Vector Terrain & Trail Map Overlay**: Pure KMP offline vector geometry projection, trail tracks, topographic elevation contours, waypoint breadcrumbs, and seamless radar integration across Compose Multiplatform (`ProximityRadarView.kt`, `OfflineTrailCockpitSheet.kt`) and 100% Native iOS SwiftUI (`ContentView.swift`, `OfflineTrailCockpitSheet.swift`) with zero cellular data (Sprint 18 - v2.5.0 Release).
- **Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing & Storage Manager**: Pure KMP chunked file transmission engine (Adler-32, sliding-window retransmission, MTU <= 100 bytes), GPX 1.1 and RFC 7946 GeoJSON parsers/exporters, and seamless 1-tap route beaming across Compose Multiplatform (`TrailFileManagerSheet.kt`) and 100% Native iOS SwiftUI (`TrailFileManagerSheet.swift`) with offline route catalog management (Sprint 19 - v2.6.0 Release).
- **Tactical Convoy Geofencing, Autonomous Power Governor & Multi-Node Stress Benchmark Suite**: Pure KMP Haversine tether geofencing and waypoint proximity radar alarms, BFS connected-component convoy cluster split detection, autonomous multi-tier battery duty-cycle governor (NORMAL, CONSERVATIVE, EXTREME_SURVIVAL, REST_STOP_SLEEP) with instant PTT/motion wake and Emergency SOS preemption, multiplatform cockpit HUD presentation (tether boundary rings, breach banners, power pills), and automated 16-node virtual convoy stress benchmark harness with Archify showcase diagram synchronization (Sprint 20 - v2.7.0 Release).
- **Autonomous Crash Impact Detection, eCall SOS & Offline Voice Command HUD**: Pure KMP multi-axis IMU crash impact detection (8.5g severe collision shock, 4.5g hard impact, >60° sustained fall-over) with 30s abortable pre-alert countdown, offline acoustic DSP keyword spotting ("Intercom SOS", "Next Channel", "Prev Channel", "Toggle Mute", "Battery Status", "Cancel Alert") under 80–120 km/h aerodynamic wind noise, platform hardware sensor loops across Android and iOS, full multiplatform emergency HUD presentation, and 5,000-sample streaming simulation harness with Archify showcase diagram synchronization (Sprint 21 - v2.8.0 Release).
- **Dynamic Mesh Directed Routing, AODV-Lite Route Optimization & RF Collision Reduction**: Pure KMP AODV-Lite on-demand route discovery (RREQ/RREP/RERR) with monotonic sequence numbers, link delivery ratio & RSSI-based ETX metrics, 3-failure link break circuit breaker, selective unicast/multicast forwarding, overhearing bystander contention suppression (>80% RF airtime reduction in 24-node convoy), reactive multiplatform routing HUD presentation across Compose Multiplatform and 100% Native iOS SwiftUI, and 24-node large-scale convoy simulation harness with Archify showcase diagram synchronization (Sprint 22 - v2.9.0 Release).

---

## 🏁 7. Release Criteria & Definition of Done (DoD)

1. **Multiplatform Test Suite**: 100% passing unit tests on JVM, Android, and iOS Simulator targets (`./gradlew test`).
2. **Build Success**: Clean build of Android release APK (`assembleRelease`) and iOS framework compilation (`compileKotlinIosSimulatorArm64`) without errors.
3. **Field Tested Range**: Reliable voice communication over $\ge 2$ hops with active TTL decrement.
4. **Screen-Off Background Execution**: Audio capture, relay, and playback survive screen lock and app backgrounding for 4+ hours with <30% battery drain.
5. **Memory & Power Bounds**: Resident RAM strictly bounded (<80 MB total, <1 MB blackbox circular buffer), zero WakeLock leaks.
